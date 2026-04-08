package com.czqwq.EZMiner.chain.state;

/**
 * Client-only state: input + display projection.
 */
public class ChainClientState {

    public boolean keyPressed = false;
    public int mainMode = 1;
    public int subMode = 0;
    public int previewRenderedCount = 0;
    public int chainedCount = 0;
    public long elapsedMs = 0L;
}
