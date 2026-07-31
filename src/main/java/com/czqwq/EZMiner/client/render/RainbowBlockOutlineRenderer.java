package com.czqwq.EZMiner.client.render;

import java.util.List;

import org.joml.Vector3i;
import org.lwjgl.opengl.GL11;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Modern-Rainbow block outline renderer — same two-pass structure as
 * {@link ModernBlockOutlineRenderer}, but the edge colour cycles through the
 * rainbow every {@value #CYCLE_MS} ms.
 *
 * <p>
 * <b>Module decoupling:</b> this class is independent of {@link MinerRenderer}.
 * It reads the current time from {@link System#currentTimeMillis()} to derive
 * the colour index — no dependency on any EZMiner subsystem.
 */
@SideOnly(Side.CLIENT)
public class RainbowBlockOutlineRenderer implements BlockOutlineRenderStrategy {

    /** Milliseconds each rainbow colour is displayed. */
    private static final int CYCLE_MS = 100;

    /** Rainbow colour table (R, G, B). */
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
        if (indexCount <= 0) return;

        // Use long arithmetic before the cast — (currentTime / 500) overflows int.
        final int idx = (int) ((System.currentTimeMillis() / CYCLE_MS) % RAINBOW.length);
        final float r = RAINBOW[idx][0];
        final float g = RAINBOW[idx][1];
        final float b = RAINBOW[idx][2];

        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);

        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_CULL_FACE);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

        // Pass 1: visible edges — depth-tested, thick, full current rainbow colour
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        GL11.glLineWidth(2.5F);
        GL11.glColor4f(r, g, b, 1.0F);
        cache.render(indexCount);

        // Pass 2: hidden edges — no depth test, thin, dimmed current rainbow colour
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glLineWidth(1.0F);
        GL11.glColor4f(r * 0.35F, g * 0.35F, b * 0.35F, 0.25F);
        cache.render(indexCount);

        GL11.glPopAttrib();
    }
}
