package com.czqwq.EZMiner.chain.execution;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.world.World;
import net.minecraftforge.common.ForgeHooks;
import net.minecraftforge.event.world.BlockEvent;

import com.czqwq.EZMiner.Config;

/**
 * Optional per-block Forge {@code BlockEvent.BreakEvent} for the fast harvest paths.
 *
 * <p>
 * EZMiner's fast paths deliberately skip the per-block break event for performance.
 * When {@link Config#fireBreakEvent} is enabled (server config, default off), this
 * helper fires the canonical event so protection/claim mods can cancel individual
 * blocks and listeners can adjust the XP drop. {@code ForgeHooks.onBlockBreakEvent}
 * also handles the client block-resync packet itself when the event is cancelled.
 */
public final class ChainBreakEventHelper {

    private ChainBreakEventHelper() {}

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
}
