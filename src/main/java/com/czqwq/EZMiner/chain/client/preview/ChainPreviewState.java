package com.czqwq.EZMiner.chain.client.preview;

/**
 * Client-side preview projection state.
 *
 * <p>
 * The former {@code target} field was written on every frame and never read; the search target
 * is owned by {@code ChainPreviewController}.
 * </p>
 */
public class ChainPreviewState {

    public boolean frozen = false;
    public int renderedCount = 0;
}
