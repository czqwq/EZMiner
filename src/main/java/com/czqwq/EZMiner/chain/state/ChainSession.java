package com.czqwq.EZMiner.chain.state;

import java.util.UUID;

import org.joml.Vector3i;

/**
 * Server-side execution session context.
 */
public class ChainSession {

    public final UUID sessionId = UUID.randomUUID();
    public final Vector3i originPos;
    public final int dimensionId;
    public final long startTimeMs;

    public ChainSession(Vector3i originPos, int dimensionId, long startTimeMs) {
        this.originPos = originPos;
        this.dimensionId = dimensionId;
        this.startTimeMs = startTimeMs;
    }
}
