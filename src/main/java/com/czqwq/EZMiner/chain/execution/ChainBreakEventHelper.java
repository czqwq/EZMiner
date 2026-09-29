package com.czqwq.EZMiner.chain.execution;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.world.World;
import net.minecraftforge.common.ForgeHooks;
import net.minecraftforge.event.world.BlockEvent;

import com.czqwq.EZMiner.Config;

/**
 * Per-block protection gate + optional Forge {@code BlockEvent.BreakEvent} for the fast
 * harvest paths.
 *
 * <p>
 * EZMiner's fast paths deliberately skip the per-block break event for performance. When
 * {@link Config#fireBreakEvent} is enabled (server config, default off) this helper fires the
 * canonical event so listeners can adjust the XP drop and protection/claim mods can cancel
 * individual blocks. {@code ForgeHooks.onBlockBreakEvent} also handles the client
 * block-resync packet itself when the event is cancelled.
 * </p>
 *
 * <p>
 * Independently of that config flag, the fast paths must not bypass <em>protection</em>. The
 * EBS write path never reaches {@code World.setBlock}, so {@code World.canMineBlock} and any
 * claim mod that cancels through the break event were both skipped — see
 * {@link #canBreakAt}. That is the protection gate every harvest path now calls.
 * </p>
 */
public final class ChainBreakEventHelper {

    /**
     * Mods that implement their chunk/region protection by cancelling
     * {@code BlockEvent.BreakEvent}. When one of these is present the event must be fired even
     * if {@link Config#fireBreakEvent} is off, otherwise EZMiner's fast paths silently bypass
     * the protection (ServerUtilities registers exactly such a handler: it cancels the event
     * when the actor may not edit the claimed chunk).
     */
    private static final String[] PROTECTION_MOD_IDS = { "serverutilities" };

    /**
     * True when a protection mod that cancels through the Forge break event is loaded. Cached
     * because {@code Loader.isModLoaded} is a mod-list scan and this is consulted per removed
     * block.
     */
    private static final boolean PROTECTION_MOD_LOADED = detectProtectionMod();

    private ChainBreakEventHelper() {}

    private static boolean detectProtectionMod() {
        try {
            for (String modId : PROTECTION_MOD_IDS) {
                if (cpw.mods.fml.common.Loader.isModLoaded(modId)) return true;
            }
        } catch (Throwable ignored) {
            // Never let a mod-list probe break the harvest path.
        }
        return false;
    }

    /**
     * Fires the per-block break event when {@link Config#fireBreakEvent} is on.
     * Must be called <em>before</em> tool damage and block removal.
     *
     * @return the fired event (check {@code isCanceled()} and abort the block if
     *         so; use {@code getExpToDrop()} for XP), or {@code null} when the
     *         feature is disabled and the caller should keep its default behavior
     */
    public static BlockEvent.BreakEvent fireIfEnabled(World world, EntityPlayerMP player, int x, int y, int z) {
        if (!Config.fireBreakEvent) return null;
        return ForgeHooks.onBlockBreakEvent(world, player.theItemInWorldManager.getGameType(), player, x, y, z);
    }

    /**
     * Fires the per-block break event when {@link Config#fireBreakEvent} is on <em>or</em> a
     * protection mod that cancels through it is loaded.
     *
     * @return the fired event, or {@code null} when neither condition holds
     */
    public static BlockEvent.BreakEvent fireIfEnabledOrProtected(World world, EntityPlayerMP player, int x, int y,
        int z) {
        if (!Config.fireBreakEvent && !PROTECTION_MOD_LOADED) return null;
        return ForgeHooks.onBlockBreakEvent(world, player.theItemInWorldManager.getGameType(), player, x, y, z);
    }

    /**
     * Single protection gate for every EZMiner harvest path.
     *
     * <p>
     * Always consults {@code World.canMineBlock} — the cheap vanilla check that covers the
     * world-spawn protection radius and any mod that hooks it — and additionally consults the
     * Forge {@code BlockEvent.BreakEvent} when {@link Config#fireBreakEvent} is on or a
     * protection mod that cancels through that event is loaded.
     * </p>
     *
     * <p>
     * The split is deliberate: firing a Forge event allocates an object per block, which is a
     * real cost on a 1024-block vein, whereas {@code canMineBlock} is a cheap predicate. Users
     * who install no such protection mod therefore do not pay the event allocation, and users
     * who do install one get the claim enforcement they had before the fast paths existed.
     * </p>
     *
     * @return {@code true} when the position may be broken
     */
    public static boolean canBreakAt(World world, EntityPlayerMP player, int x, int y, int z) {
        if (world == null || player == null) return false;
        if (!world.canMineBlock(player, x, y, z)) return false;
        BlockEvent.BreakEvent event = fireIfEnabledOrProtected(world, player, x, y, z);
        return event == null || !event.isCanceled();
    }
}
