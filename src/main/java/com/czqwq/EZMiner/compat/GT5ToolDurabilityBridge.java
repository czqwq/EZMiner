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
            long estimated = (long) Math.max(1, hardness * toolStats.getToolDamagePerBlockBreak());

            return (currentDamage + estimated) < maxDamage;
        }

        static long estimateDamage(ItemStack stack, Block block, World world, int x, int y, int z) {
            if (!(stack.getItem() instanceof gregtech.api.items.MetaGeneratedTool tool)) return 0;

            gregtech.api.interfaces.IToolStats toolStats = tool.getToolStats(stack);
            if (toolStats == null) return 0;

            float hardness = block.getBlockHardness(world, x, y, z);
            return (long) Math.max(1, hardness * toolStats.getToolDamagePerBlockBreak());
        }
    }
}
