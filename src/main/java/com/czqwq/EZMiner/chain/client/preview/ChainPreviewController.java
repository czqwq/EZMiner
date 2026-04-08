package com.czqwq.EZMiner.chain.client.preview;

import org.joml.Vector3i;

/**
 * Preview lifecycle controller, independent from execution lifecycle.
 */
public class ChainPreviewController {

    private final ChainPreviewState state = new ChainPreviewState();

    public ChainPreviewState getState() {
        return state;
    }

    public void freeze() {
        state.frozen = true;
    }

    public void unfreeze() {
        state.frozen = false;
        state.target = null;
        state.renderedCount = 0;
    }

    public void setTarget(Vector3i pos) {
        state.target = pos == null ? null : new Vector3i(pos);
    }
}
