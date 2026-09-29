package com.czqwq.EZMiner.chain.state;

import java.util.UUID;

/**
 * Client-only state: input + display projection.
 *
 * <p>
 * The live preview counter and session dimension are <strong>not</strong> stored here: they live
 * on {@code client/ClientStateContainer} ({@code previewRenderedCount}, read by
 * {@code HudRenderer}) and in {@code chain/network/PacketChainStateSync}'s own
 * {@code sessionDimension} field. The write-only duplicates that used to sit in this class were
 * removed so there is exactly one owner per value.
 * </p>
 */
public class ChainClientState {

    public boolean keyPressed = false;
    public int mainMode = 1;
    public int subMode = 0;
    public UUID sessionId = null;
    public long sessionStartMs = 0L;
    public boolean inOperate = false;
    public int chainedCount = 0;
    public long elapsedMs = 0L;
}
