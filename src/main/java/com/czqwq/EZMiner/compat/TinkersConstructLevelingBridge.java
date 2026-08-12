package com.czqwq.EZMiner.compat;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;

import com.czqwq.EZMiner.core.founder.BasePositionFounder;

import cpw.mods.fml.common.Loader;

/**
 * TiC {@code ActiveToolMod.beforeBlockBreak} bridge — restores Tinkers' Construct tool
 * leveling (IguanaTweaks XP) during chain mining.
 *
 * <p>
 * EZMiner's fast harvest paths bypass {@code ItemInWorldManager.tryHarvestBlock}, so
 * {@code Item.onBlockStartBreak} is never called and TiC's registered
 * {@code ActiveToolMod.beforeBlockBreak} hooks (IguanaTweaks tool XP, TiC autosmelt,
 * lapis mid-stream modify, …) silently stop firing for chained blocks. This bridge
 * replays exactly what the <em>base</em> {@code ToolCore.onBlockStartBreak} does —
 * iterate {@code TConstructRegistry.activeModifiers} — without calling
 * {@code onBlockStartBreak} itself, because AOE overrides ({@code AOEHarvestTool},
 * {@code LumberAxe}, {@code Scythe}) would recursively break extra blocks mid-chain.
 * </p>
 *
 * <p>
 * <strong>Decoupling:</strong> no IguanaTweaks dependency at all — its leveling hook is
 * just one entry in TiC's active-modifier list, so XP gating (effective tool, strength,
 * ore bonus, creative exclusion) runs Iguana's own unmodified logic. TiC classes are
 * only referenced inside the nested {@link Impl} holder, which the JVM classloads
 * lazily on first use — never when TConstruct is absent. When TConstruct is not
 * installed, {@link #TIC_LOADED} is a constant {@code false} and the JIT eliminates
 * the call entirely.
 * </p>
 *
 * <p>
 * <strong>Note:</strong> {@code afterBlockBreak} hooks need no bridge — they already
 * fire through the existing {@code stack.func_150999_a} → {@code ToolCore.onBlockDestroyed}
 * call in the harvest paths. TE blocks go through vanilla {@code tryHarvestBlock},
 * which fires {@code onBlockStartBreak} itself — do not bridge those or XP doubles.
 * </p>
 */
public final class TinkersConstructLevelingBridge {

    private static final boolean TIC_LOADED = Loader.isModLoaded("TConstruct");

    private TinkersConstructLevelingBridge() {}

    /**
     * Fire TiC's {@code ActiveToolMod.beforeBlockBreak} hooks for the player's held
     * tool, exactly like a vanilla block break would. Must be called <em>before</em>
     * tool damage and block removal — hooks read the live block from the world.
     *
     * @param player the mining player (server side)
     * @param x      block x
     * @param y      block y
     * @param z      block z
     * @return {@code true} if a hook cancelled the harvest, meaning it consumed the
     *         block itself (e.g. TiC autosmelt already set it to air, spawned the
     *         smelted drop, and damaged the tool). The caller must then skip its own
     *         tool damage / removal / drops for this block and treat it as harvested,
     *         mirroring vanilla {@code tryHarvestBlock}. Always {@code false} when
     *         TConstruct is absent or the held item is not a TiC tool.
     */
    public static boolean fireBeforeBlockBreak(EntityPlayerMP player, int x, int y, int z) {
        if (!TIC_LOADED) return false;
        ItemStack stack = player.getCurrentEquippedItem();
        // Mirror ToolCore.onBlockStartBreak's own hasTagCompound guard — hooks
        // unconditionally read stack.getTagCompound().
        if (stack == null || !stack.hasTagCompound()) return false;
        return Impl.fire(stack, x, y, z, player);
    }

    // ── Deterministic auto-smelt drop ownership ──────────────────────────────
    //
    // TiC's autosmelt hook spawns the smelted EntityItem synchronously INSIDE
    // beforeBlockBreak (exact block centre, block already air). Manager's
    // EntityJoinWorldEvent interceptor needs to attribute that item to the
    // current chain without stealing other players' or other mods' drops, so
    // the harvest paths pre-register the position (with the owning player)
    // immediately before replaying the hooks. The interceptor checks this map
    // during the synchronously-dispatched join event; the caller then clears
    // the entry. All access happens on the server thread — a plain HashMap
    // suffices, and the map is empty outside the brief hook window.

    private static final Map<Long, UUID> pendingSmelt = new HashMap<>();

    /**
     * Registers {@code (x, y, z)} as being about to go through the TiC
     * {@code beforeBlockBreak} replay for {@code player}. Call immediately
     * before {@link #fireBeforeBlockBreak}; must be paired with
     * {@link #clearBlockBreak} afterwards.
     */
    public static void markBeforeBlockBreak(EntityPlayerMP player, int x, int y, int z) {
        if (!TIC_LOADED) return;
        pendingSmelt.put(BasePositionFounder.encodePos(x, y, z), player.getUniqueID());
    }

    /** Removes the pending entry for the given position. Idempotent. */
    public static void clearBlockBreak(int x, int y, int z) {
        if (TIC_LOADED) pendingSmelt.remove(BasePositionFounder.encodePos(x, y, z));
    }

    /** True when {@code (x, y, z)} was just marked for this exact player. */
    public static boolean isSmeltCandidate(EntityPlayerMP player, int x, int y, int z) {
        if (!TIC_LOADED) return false;
        UUID owner = pendingSmelt.get(BasePositionFounder.encodePos(x, y, z));
        return owner != null && owner.equals(player.getUniqueID());
    }

    /** Holder for all TConstruct class references — only classloaded when TiC is present. */
    private static final class Impl {

        private Impl() {}

        static boolean fire(ItemStack stack, int x, int y, int z, EntityPlayerMP player) {
            if (!(stack.getItem() instanceof tconstruct.library.tools.ToolCore tool)) return false;

            // Indexed loop over the ArrayList — no iterator allocation in the
            // per-block hot path.
            ArrayList<tconstruct.library.ActiveToolMod> mods = tconstruct.library.TConstructRegistry.activeModifiers;
            boolean cancelHarvest = false;
            for (int i = 0, n = mods.size(); i < n; i++) {
                if (mods.get(i)
                    .beforeBlockBreak(tool, stack, x, y, z, player)) cancelHarvest = true;
            }
            return cancelHarvest;
        }
    }
}
