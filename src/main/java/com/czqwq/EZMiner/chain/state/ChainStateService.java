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

    public ChainPlayerState getOrCreate(UUID uuid) {
        return stateMap.computeIfAbsent(uuid, ChainPlayerState::new);
    }

    public ChainPlayerState onPlayerLogin(EntityPlayerMP player) {
        ChainPlayerState state = getOrCreate(player.getUniqueID());
        state.clearRuntime();
        return state;
    }

    public void onPlayerLogout(UUID uuid) {
        ChainPlayerState state = stateMap.remove(uuid);
        if (state != null) state.clearRuntime();
    }

    public void onPlayerRespawn(UUID uuid) {
        ChainPlayerState state = stateMap.get(uuid);
        if (state != null) state.clearRuntime();
    }

    public void onPlayerDimensionChanged(UUID uuid) {
        ChainPlayerState state = stateMap.get(uuid);
        if (state != null) state.clearRuntime();
    }

    public void onWorldUnload() {
        for (ChainPlayerState state : stateMap.values()) {
            state.clearRuntime();
        }
    }

    public void markSessionStart(UUID uuid, Vector3i origin, int dimId) {
        ChainPlayerState state = getOrCreate(uuid);
        state.session = new ChainSession(new Vector3i(origin), dimId, System.currentTimeMillis());
        state.runtimeState.inOperate = true;
        state.runtimeState.chainedCount = 0;
        state.runtimeState.elapsedMs = 0L;
    }

    public void markSessionStop(UUID uuid) {
        ChainPlayerState state = stateMap.get(uuid);
        if (state == null) return;
        state.runtimeState.inOperate = false;
        state.runtimeState.queuedCandidates = 0;
        state.session = null;
    }
}
