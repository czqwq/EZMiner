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
            stopRuntime(mgr);
        }
    }

    public void cleanupManagerRuntime(UUID playerUUID, Map<UUID, Manager> managers) {
        Manager mgr = managers.get(playerUUID);
        if (mgr == null) return;
        stopRuntime(mgr);
    }

    private void stopRuntime(Manager mgr) {
        if (mgr.operator != null) {
            mgr.operator.stopImmediately();
            mgr.operator = null;
        }
        mgr.cleanupState();
        mgr.clearDrops();
    }
}
