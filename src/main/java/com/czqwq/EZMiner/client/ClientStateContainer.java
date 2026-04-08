package com.czqwq.EZMiner.client;

import com.czqwq.EZMiner.chain.state.ChainClientState;
import com.czqwq.EZMiner.core.MinerModeState;

/** Holds client-side view of the player's current mining mode and chain state. */
public class ClientStateContainer {

    public MinerModeState minerModeState = new MinerModeState();
    public ChainClientState chainClientState = new ChainClientState();
    /** Number of blocks mined in the current chain operation. Updated by runtime sync packets. */
    public volatile int chainedBlockCount = 0;
    /** Elapsed time in milliseconds of the current chain operation. Updated by runtime sync packets. */
    public volatile long chainElapsedMs = 0L;
    /** Number of blocks currently rendered in preview on the client. */
    public volatile int previewRenderedCount = 0;
}
