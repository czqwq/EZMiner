package com.czqwq.EZMiner.chain.client.preview;

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
        state.renderedCount = 0;
    }
}
