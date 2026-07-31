package com.czqwq.EZMiner.client.render;

import java.util.List;

import org.joml.Vector3i;

/**
 * Strategy interface for block-outline preview rendering.
 *
 * <p>
 * Implementations receive the pre-uploaded {@link RenderCache}, an index count, and the
 * raw position list. Batch renderers (Native, Modern, Rainbow) ignore {@code positions}
 * and call {@link RenderCache#render(int)}. Per-block renderers (Gradient) use
 * {@code positions} for per-block colouring and ignore the cache.
 *
 * <p>
 * The calling code translates the matrix to world-relative coordinates before invoking
 * {@link #render}.
 */
public interface BlockOutlineRenderStrategy {

    /**
     * Renders the block outlines.
     *
     * @param cache      the shared VBO/display-list cache (ignored by per-block renderers)
     * @param indexCount number of index entries to draw; implementations must skip rendering
     *                   when this value is {@code <= 0}
     * @param positions  the block positions in world space; may be empty. Batch renderers
     *                   ignore this parameter; per-block renderers iterate it directly.
     */
    void render(RenderCache cache, int indexCount, List<Vector3i> positions);
}
