package com.czqwq.EZMiner.chain.lifecycle;

import java.util.Map;
import java.util.UUID;

import net.minecraft.entity.player.EntityPlayerMP;

import com.czqwq.EZMiner.EZMiner;
import com.czqwq.EZMiner.chain.execution.CooldownTracker;
import com.czqwq.EZMiner.core.Manager;
import com.czqwq.EZMiner.toolswap.server.ToolSwapServerService;

/**
 * Central lifecycle coordinator for chain runtime cleanup.
 */
public class ChainLifecycleService {

    public void onPlayerLogin(EntityPlayerMP player) {
        EZMiner.chainStateService.onPlayerLogin(player);
    }

    public void onPlayerLogout(UUID playerUUID, Map<UUID, Manager> managers) {
        EZMiner.chainStateService.onPlayerLogout(playerUUID);
        CooldownTracker.clear(playerUUID);
        Manager mgr = managers.remove(playerUUID);
        if (mgr != null) {
            stopRuntime(mgr);
            mgr.unRegistry();
        }
        ToolSwapServerService.clear(playerUUID);
    }

    public void onPlayerRespawn(UUID playerUUID, Map<UUID, Manager> managers) {
        EZMiner.chainStateService.onPlayerRespawn(playerUUID);
        cleanupManagerRuntime(playerUUID, managers);
        ToolSwapServerService.clear(playerUUID);
    }

    public void onPlayerDimensionChanged(UUID playerUUID, Map<UUID, Manager> managers) {
        EZMiner.chainStateService.onPlayerDimensionChanged(playerUUID);
        cleanupManagerRuntime(playerUUID, managers);
        ToolSwapServerService.clear(playerUUID);
    }

    public void onWorldUnload(Map<UUID, Manager> managers) {
        EZMiner.chainStateService.onWorldUnload();
        for (Manager mgr : managers.values()) {
            // The world is going away: do not spawn into it, and do not destroy what is
            // collected — the manager survives the unload, so the next key-release flush can
            // still deliver it once the world is available again.
            stopRuntime(mgr, false);
        }
    }

    public void cleanupManagerRuntime(UUID playerUUID, Map<UUID, Manager> managers) {
        Manager mgr = managers.get(playerUUID);
        if (mgr == null) return;
        stopRuntime(mgr);
    }

    private void stopRuntime(Manager mgr) {
        stopRuntime(mgr, true);
    }

    /**
     * Stops the operator and cleans up per-chain runtime state.
     *
     * <p>
     * <strong>Drop/XP safety:</strong> with the default {@code dropImmediately=false} and
     * {@code xpDropMode=1} the whole chain's items and XP live in the manager's drop collector
     * and {@code XPDropHandler} until the key is released. The old code called
     * {@code cleanupState()}/{@code clearDrops()} straight away, so logging out, respawning or
     * changing dimension <em>destroyed</em> them with nothing spawned.
     * {@link Manager#flushDrops()} — which already carries the respawn → world-spawn fallback
     * chain — now runs first.
     * </p>
     *
     * @param cleanupRuntime when {@code true} the collector/XP are discarded after the flush
     *                       and the chain runtime state is reset. When {@code false} only the
     *                       operator is stopped and the collector is left intact for a later
     *                       flush (world unload).
     */
    private void stopRuntime(Manager mgr, boolean cleanupRuntime) {
        if (mgr.operator != null) {
            mgr.operator.stopImmediately();
            mgr.operator = null;
        }
        if (!cleanupRuntime) return;
        if (mgr.canFlushDrops()) {
            // Deliver pending items + XP before the collector is cleared.
            mgr.flushDrops();
        }
        mgr.cleanupState();
        mgr.clearDrops();
    }
}
