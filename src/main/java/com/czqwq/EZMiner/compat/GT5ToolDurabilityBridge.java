package com.czqwq.EZMiner.compat;

import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;

import cpw.mods.fml.common.Loader;

/**
 * Pre-harvest durability guard for GregTech 5 tools during chain mining.
 *
 * <p>
 * <strong>Problem:</strong> EZMiner's harvest paths call
 * {@code Item.onBlockDestroyed} (via {@code stack.func_150999_a}) to apply tool
 * damage <em>before</em> removing the block. GT tools store durability in NBT and
 * the damage formula is
 * {@code Math.max(1, blockHardness &times; getToolDamagePerBlockBreak())}. When a
 * tool's remaining durability is less than the incoming damage, the tool breaks
 * during {@code onBlockDestroyed} — but the block has already been removed from
 * the world by that point. The drops step then sees a broken tool (or no tool)
 * and either skips drops or produces wrong results (e.g. wrench dismantling a
 * machine block without dropping the machine).
 * </p>
 *
 * <p>
 * <strong>Fix:</strong> call {@link #hasEnoughDurability} before the tool-damage
 * step. When it returns {@code false}, the caller skips the block entirely (neither
 * removes it nor damages the tool), preventing the "block gone, no drops" scenario.
 * </p>
 *
 * <p>
 * <strong>Decoupling:</strong> all GT5 class references are inside the nested
 * {@link Impl} holder, classloaded lazily only when GregTech is present. When GT5
 * is absent, {@link #GT5_LOADED} is constant {@code false} and every call returns
 * {@code true} (no restriction).
 * </p>
 */
public final class GT5ToolDurabilityBridge {

    static final boolean GT5_LOADED = Loader.isModLoaded("gregtech");

    private GT5ToolDurabilityBridge() {}

    /**
     * Checks whether the player's held GT tool has enough remaining durability to
     * survive breaking the given block.
     *
     * <p>
     * Call this <strong>before</strong> the tool-damage step in every harvest
     * path. When it returns {@code false}, skip the block — do not remove it,
     * do not damage the tool, do not spawn drops.
     * </p>
     *
     * @param player the mining player
     * @param block  the block about to be harvested
     * @param world  the world
     * @param x      block x
     * @param y      block y
     * @param z      block z
     * @return {@code true} if the tool has enough durability (or is not a GT tool)
     */
    public static boolean hasEnoughDurability(EntityPlayer player, Block block, World world, int x, int y, int z) {
        if (!GT5_LOADED) return true;
        if (player == null || player.capabilities.isCreativeMode) return true;
        ItemStack stack = player.getCurrentEquippedItem();
        if (stack == null) return true;
        return Impl.checkDurability(stack, block, world, x, y, z);
    }

    /**
     * Returns the estimated durability cost of breaking the given block with the
     * player's current GT tool, or 0 if the tool is not a GT tool. Useful for
     * callers that want to batch-check or log the cost.
     */
    public static long estimateDamage(EntityPlayer player, Block block, World world, int x, int y, int z) {
        if (!GT5_LOADED) return 0;
        if (player == null) return 0;
        ItemStack stack = player.getCurrentEquippedItem();
        if (stack == null) return 0;
        return Impl.estimateDamage(stack, block, world, x, y, z);
    }

    /**
     * True when the held GT tool can survive one more typical block.
     *
     * <p>
     * Used by {@code BaseOperator.canOperate()}, whose vanilla formula
     * ({@code getMaxDamage() - getItemDamage() > 1}) is dead for every GT tool because
     * {@code MetaBaseItem}'s constructor calls {@code setMaxDamage(0)}. GT durability lives in NBT,
     * so this asks the bridge instead of the {@code ItemStack} accessors.
     * </p>
     *
     * <p>
     * The reserve is deliberately conservative: it requires the tool to survive the worst-case
     * single-block charge ({@link #worstCaseChargeForOneBlock}) with at least one point of slack, so
     * the gate hands off <em>before</em> the block that would break the tool.
     * </p>
     */
    public static boolean hasDurabilityReserveForNextBlock(EntityPlayer player) {
        if (!GT5_LOADED) return true;
        if (player == null || player.capabilities.isCreativeMode) return true;
        ItemStack stack = player.getCurrentEquippedItem();
        if (stack == null) return true;
        return Impl.hasReserve(stack);
    }

    /**
     * Worst-case GT durability charge for breaking one block.
     *
     * <p>
     * GT charges twice: {@code Math.max(1, hardness × getToolDamagePerBlockBreak())} in
     * {@code MetaGeneratedTool.onBlockDestroyed}, and additionally
     * {@code convertBlockDrops(...) × getToolDamagePerDropConversion()} from its HarvestDrops
     * handler. The bridge used to model only the first term, so on GT ore drops (where
     * {@code convertBlockDrops} returns &gt; 0) it over-admitted and the guard let the tool break on
     * the very block it was meant to protect. Adding one drop-conversion unit is the conservative
     * upper bound for the second term.
     * </p>
     */
    private static long worstCaseChargeForOneBlock(float hardness, Object toolStats) {
        long blockCharge = (long) Math.max(1, hardness * damagePerBlockBreak(toolStats));
        return blockCharge + damagePerDropConversion(toolStats);
    }

    /** Holder for all GT5 class references — only classloaded when GregTech is present. */
    private static final class Impl {

        private Impl() {}

        static boolean checkDurability(ItemStack stack, Block block, World world, int x, int y, int z) {
            if (!(stack.getItem() instanceof gregtech.api.items.MetaGeneratedTool tool)) return true;

            gregtech.api.interfaces.IToolStats toolStats = tool.getToolStats(stack);
            if (toolStats == null) return true;

            long currentDamage = gregtech.api.items.MetaGeneratedTool.getToolDamage(stack);
            long maxDamage = gregtech.api.items.MetaGeneratedTool.getToolMaxDamage(stack);
            if (maxDamage <= 0) return true;

            float hardness = block.getBlockHardness(world, x, y, z);
            long estimated = worstCaseChargeForOneBlock(hardness, toolStats);

            // GT breaks the tool when tNewDamage >= getToolMaxDamage (MetaGeneratedTool.doDamage),
            // so "strictly less than max" is exactly "will not break".
            return (currentDamage + estimated) < maxDamage;
        }

        static boolean hasReserve(ItemStack stack) {
            if (!(stack.getItem() instanceof gregtech.api.items.MetaGeneratedTool tool)) return true;

            gregtech.api.interfaces.IToolStats toolStats = tool.getToolStats(stack);
            if (toolStats == null) return true;

            long currentDamage = gregtech.api.items.MetaGeneratedTool.getToolDamage(stack);
            long maxDamage = gregtech.api.items.MetaGeneratedTool.getToolMaxDamage(stack);
            if (maxDamage <= 0) return true;

            // No block in hand here, so use the tool's own stone-level charge as the reference
            // (hardness 1.5 = vanilla stone) plus the worst-case drop-conversion term. Erring
            // conservative means a handoff slightly early, never a broken tool.
            long estimated = worstCaseChargeForOneBlock(1.5F, toolStats);
            return (currentDamage + estimated) < maxDamage;
        }

        static long estimateDamage(ItemStack stack, Block block, World world, int x, int y, int z) {
            if (!(stack.getItem() instanceof gregtech.api.items.MetaGeneratedTool tool)) return 0;

            gregtech.api.interfaces.IToolStats toolStats = tool.getToolStats(stack);
            if (toolStats == null) return 0;

            float hardness = block.getBlockHardness(world, x, y, z);
            return worstCaseChargeForOneBlock(hardness, toolStats);
        }
    }

    /**
     * Reads {@code IToolStats.getToolDamagePerBlockBreak()} reflectively-safely through the bridge's
     * cached GT types. Kept outside {@code Impl} so the two call paths share one implementation and
     * cannot drift (the ledger required the gate and the guard to agree).
     */
    private static float damagePerBlockBreak(Object toolStats) {
        try {
            return ((gregtech.api.interfaces.IToolStats) toolStats).getToolDamagePerBlockBreak();
        } catch (Throwable t) {
            return 1.0F;
        }
    }

    /**
     * Cached handle for GT's second damage term, resolved at most once per JVM.
     *
     * <p>
     * The previous body called {@code toolStats.getClass().getMethod(...)} on every invocation.
     * That method runs twice per block (once in the pre-harvest guard, once in {@code canOperate}'s
     * gate) on the harvest hot path — up to ~2048 reflective lookups for a 1024-block vein — in a
     * file that deliberately caches every other GT handle for exactly this reason.
     * </p>
     *
     * <p>
     * It is resolved against the <strong>interface</strong> ({@code IToolStats}) rather than the
     * concrete stats class: the interface lookup succeeds for every implementation that inherits the
     * method, so one handle serves all tool types, which a per-class cache could not. A
     * {@link java.util.concurrent.atomic.AtomicReference}-style guard is unnecessary — a benign race
     * would at worst resolve the same {@code Method} twice, and the field is {@code volatile} so
     * readers see a fully-published handle.
     * </p>
     */
    private static volatile java.lang.reflect.Method mDropConversion;
    private static volatile boolean dropConversionResolved;

    private static java.lang.reflect.Method dropConversionMethod() {
        if (!dropConversionResolved) {
            java.lang.reflect.Method resolved = null;
            try {
                resolved = gregtech.api.interfaces.IToolStats.class.getMethod("getToolDamagePerDropConversion");
            } catch (Throwable t) {
                resolved = null; // absent on this GT5U generation — caller falls back
            }
            mDropConversion = resolved;
            dropConversionResolved = true;
        }
        return mDropConversion;
    }

    /**
     * GT's second charge for one block.
     *
     * <p>
     * {@code IToolStats} does not expose it on every GT5U generation, so when the cached handle is
     * absent the worst case is taken as the block-break charge (i.e. double the first term) —
     * conservative by construction, which is the correct direction for a durability guard.
     * </p>
     */
    private static long damagePerDropConversion(Object toolStats) {
        java.lang.reflect.Method m = dropConversionMethod();
        if (m != null) {
            try {
                return Math.max(1L, ((Number) m.invoke(toolStats)).longValue());
            } catch (Throwable t) {
                // fall through to the conservative fallback
            }
        }
        return Math.max(1L, (long) damagePerBlockBreak(toolStats));
    }
}
