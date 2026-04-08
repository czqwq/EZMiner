package com.czqwq.EZMiner.chain.state;

/**
 * Authoritative runtime state stored on the server.
 */
public class ChainRuntimeState {

    public boolean inOperate = false;
    public int chainedCount = 0;
    public long elapsedMs = 0L;
    public int queuedCandidates = 0;
    public String lastErrorCode = "";

    public void reset() {
        inOperate = false;
        chainedCount = 0;
        elapsedMs = 0L;
        queuedCandidates = 0;
        lastErrorCode = "";
    }
}
