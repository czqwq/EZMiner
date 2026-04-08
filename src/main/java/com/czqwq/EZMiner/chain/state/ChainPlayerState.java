package com.czqwq.EZMiner.chain.state;

import java.util.UUID;

import com.czqwq.EZMiner.core.MinerConfig;
import com.czqwq.EZMiner.core.MinerModeState;

/**
 * Per-player canonical state entry (server-side authority).
 */
public class ChainPlayerState {

    public final UUID playerUUID;
    public final MinerConfig minerConfig = new MinerConfig();
    public final MinerModeState minerModeState = new MinerModeState();
    public final ChainRuntimeState runtimeState = new ChainRuntimeState();
    public volatile boolean keyPressed = false;
    public volatile ChainSession session = null;

    public ChainPlayerState(UUID playerUUID) {
        this.playerUUID = playerUUID;
    }

    public void clearRuntime() {
        keyPressed = false;
        session = null;
        runtimeState.reset();
    }
}
