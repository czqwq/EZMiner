package com.czqwq.EZMiner.client.render;

/**
 * Strategy interface for block-outline preview rendering.
 *
 * <p>
 * Implementations receive the pre-uploaded {@link RenderCache} and an index count, and are
 * responsible for setting all required OpenGL state before drawing and restoring it afterwards.
 * The calling code translates the matrix to world-relative coordinates before invoking
 * {@link #render}.
 */
public interface BlockOutlineRenderStrategy {

    /**
     * Renders the block outlines using the given cache and index count.
     *
     * @param cache      the shared VBO/display-list cache containing vertex data
     * @param indexCount number of index entries to draw; implementations must skip rendering
     *                   when this value is {@code <= 0}
     */
    void render(RenderCache cache, int indexCount);
}
