package com.czqwq.EZMiner.chain.state;

import java.util.UUID;

import org.joml.Vector3i;

/**
 * Immutable input request produced by client/network/hook events.
 */
public class ChainRequest {

    public final UUID playerUUID;
    public final boolean keyPressed;
    public final Integer mainMode;
    public final Integer subMode;
    public final Vector3i triggerPos;
    public final int dimensionId;

    public ChainRequest(UUID playerUUID, boolean keyPressed, Integer mainMode, Integer subMode, Vector3i triggerPos,
        int dimensionId) {
        this.playerUUID = playerUUID;
        this.keyPressed = keyPressed;
        this.mainMode = mainMode;
        this.subMode = subMode;
        this.triggerPos = triggerPos;
        this.dimensionId = dimensionId;
    }
}
