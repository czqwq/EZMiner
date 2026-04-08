package com.czqwq.EZMiner.chain.state;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.entity.player.EntityPlayerMP;

import org.joml.Vector3i;

/**
 * Central state authority and lifecycle cleanup service.
 */
public class ChainStateService {

    private final Map<UUID, ChainPlayerState> stateMap = new ConcurrentHashMap<>();

    public ChainPlayerState getOrCreate(UUID playerUUID) {
        return stateMap.computeIfAbsent(playerUUID, ChainPlayerState::new);
    }

    public ChainPlayerState onPlayerLogin(EntityPlayerMP player) {
        ChainPlayerState state = getOrCreate(player.getUniqueID());
        state.clearRuntime();
        return state;
    }

    public void onPlayerLogout(UUID playerUUID) {
        ChainPlayerState state = stateMap.remove(playerUUID);
        if (state != null) state.clearRuntime();
    }

    public void onPlayerRespawn(UUID playerUUID) {
        clearPlayerRuntime(playerUUID);
    }

    public void onPlayerDimensionChanged(UUID playerUUID) {
        clearPlayerRuntime(playerUUID);
    }

    public void onWorldUnload() {
        for (ChainPlayerState state : stateMap.values()) {
            state.clearRuntime();
        }
    }

    private void clearPlayerRuntime(UUID playerUUID) {
        ChainPlayerState state = stateMap.get(playerUUID);
        if (state != null) state.clearRuntime();
    }

    public ChainSession markSessionStart(UUID playerUUID, Vector3i origin, int dimId) {
        ChainPlayerState state = getOrCreate(playerUUID);
        ChainSession session = new ChainSession(new Vector3i(origin), dimId, System.currentTimeMillis());
        state.session = session;
        state.runtimeState.inOperate = true;
        state.runtimeState.chainedCount = 0;
        state.runtimeState.elapsedMs = 0L;
        return session;
    }

    public void markSessionStop(UUID playerUUID) {
        ChainPlayerState state = stateMap.get(playerUUID);
        if (state == null) return;
        state.runtimeState.inOperate = false;
        state.runtimeState.queuedCandidates = 0;
        state.session = null;
    }

    public ChainSession getSession(UUID playerUUID) {
        ChainPlayerState state = stateMap.get(playerUUID);
        if (state == null) return null;
        return state.session;
    }
}
