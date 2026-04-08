package com.czqwq.EZMiner.chain.state;

import java.util.UUID;

/**
 * Client-only state: input + display projection.
 */
public class ChainClientState {

    public boolean keyPressed = false;
    public int mainMode = 1;
    public int subMode = 0;
    public int previewRenderedCount = 0;
    public UUID sessionId = null;
    public long sessionStartMs = 0L;
    public int sessionDimension = 0;
    public boolean inOperate = false;
    public int chainedCount = 0;
    public long elapsedMs = 0L;
}
