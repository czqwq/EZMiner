package com.czqwq.EZMiner.client;

import com.czqwq.EZMiner.core.MinerModeState;

/** Holds client-side view of the player's current mining mode and chain state. */
public class ClientStateContainer {

    public MinerModeState minerModeState = new MinerModeState();
    /** Number of blocks mined in the current chain operation. Updated by PacketChainCount. */
    public volatile int chainedBlockCount = 0;
}
