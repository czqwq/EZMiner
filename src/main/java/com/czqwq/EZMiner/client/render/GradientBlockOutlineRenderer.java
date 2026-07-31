package com.czqwq.EZMiner.client.render;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.joml.Vector3i;
import org.lwjgl.opengl.GL11;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Modern-Gradient block outline renderer — renders each block with a static
 * rainbow colour determined by its vertical position within the vein.
 *
 * <p>
 * Lower blocks receive red, upper blocks receive pink, creating a vertical
 * rainbow gradient that <strong>does not animate</strong>. Uses the shared
 * GPU-side VBO (same as Modern / Rainbow) — blocks are grouped into Y-bands,
 * and each band is drawn with a single-colour draw call via
 * {@link RenderCache#renderRange}.
 *
 * <p>
 * <b>Module decoupling:</b> this class is independent of {@link MinerRenderer}.
 * It reads only the position list passed via {@link #render} — no dependency on
 * any EZMiner subsystem.
 */
@SideOnly(Side.CLIENT)
public class GradientBlockOutlineRenderer implements BlockOutlineRenderStrategy {

    /** Rainbow colour table (R, G, B) — red at bottom, pink at top. */
    private static final float[][] RAINBOW = { { 1.0F, 0.0F, 0.0F }, // red
        { 1.0F, 0.5F, 0.0F }, // orange
        { 1.0F, 1.0F, 0.0F }, // yellow
        { 0.0F, 1.0F, 0.0F }, // green
        { 0.0F, 1.0F, 1.0F }, // cyan
        { 0.0F, 0.0F, 1.0F }, // blue
        { 0.5F, 0.0F, 1.0F }, // purple
        { 1.0F, 0.0F, 1.0F }, // pink
    };

    @Override
    public void render(RenderCache cache, int indexCount, List<Vector3i> positions) {
        if (positions.isEmpty()) return;

        // ── Group block indices by Y-band ──────────────────────────────────
        int minY = Integer.MAX_VALUE;
        int maxY = Integer.MIN_VALUE;
        for (Vector3i p : positions) {
            if (p.y < minY) minY = p.y;
            if (p.y > maxY) maxY = p.y;
        }
        final int range = Math.max(1, maxY - minY);
        final int nBands = RAINBOW.length;

        // Each band collects the original block index (position in the list).
        @SuppressWarnings("unchecked")
        final List<Integer>[] bandBlocks = new List[nBands];
        for (int i = 0; i < nBands; i++) {
            bandBlocks[i] = new ArrayList<>();
        }
        for (int i = 0; i < positions.size(); i++) {
            final int band = Math.min((positions.get(i).y - minY) * nBands / range, nBands - 1);
            bandBlocks[band].add(i);
        }

        // ── GL setup (shared by both passes) ──────────────────────────────
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);

        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_CULL_FACE);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

        // ── Pass 1: visible edges, depth-tested, thick ────────────────────
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        GL11.glLineWidth(2.5F);
        for (int band = 0; band < nBands; band++) {
            final float[] c = RAINBOW[band];
            GL11.glColor4f(c[0], c[1], c[2], 1.0F);
            renderBand(cache, bandBlocks[band]);
        }

        // ── Pass 2: hidden edges, no depth, thin, dimmed ──────────────────
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glLineWidth(1.0F);
        for (int band = 0; band < nBands; band++) {
            final float[] c = RAINBOW[band];
            GL11.glColor4f(c[0] * 0.3F, c[1] * 0.3F, c[2] * 0.3F, 0.25F);
            renderBand(cache, bandBlocks[band]);
        }

        GL11.glPopAttrib();
    }

    /**
     * Renders all blocks in one band. Sorts indices so adjacent blocks form
     * contiguous ranges, minimising draw calls.
     */
    private static void renderBand(RenderCache cache, List<Integer> blockIndices) {
        if (blockIndices.isEmpty()) return;
        Collections.sort(blockIndices);

        int rangeStart = blockIndices.get(0);
        int rangeCount = 1;
        for (int i = 1; i < blockIndices.size(); i++) {
            final int idx = blockIndices.get(i);
            if (idx == rangeStart + rangeCount) {
                // Adjacent — extend the current range.
                rangeCount++;
            } else {
                // Gap — flush the previous range, start a new one.
                cache.renderRange(rangeStart * 24, rangeCount * 24);
                rangeStart = idx;
                rangeCount = 1;
            }
        }
        // Flush the final range.
        cache.renderRange(rangeStart * 24, rangeCount * 24);
    }
}
