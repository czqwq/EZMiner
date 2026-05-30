package com.czqwq.EZMiner.client.render;

import org.lwjgl.opengl.GL11;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Two-pass block outline renderer inspired by FTB-Ultimine.
 *
 * <p>
 * Pass 1 – depth-tested: draws visible outline edges as solid, opaque, thicker lines so the
 * preview hugs block surfaces and has clear depth perception.
 *
 * <p>
 * Pass 2 – no depth test: draws the same edges with low alpha so the selection shape is still
 * readable through solid geometry (hidden-line ghost effect).
 */
@SideOnly(Side.CLIENT)
public class ModernBlockOutlineRenderer implements BlockOutlineRenderStrategy {

    @Override
    public void render(RenderCache cache, int indexCount) {
        if (indexCount <= 0) return;

        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);

        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_CULL_FACE);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

        // Pass 1: visible edges – depth-tested, solid, thick, bright white-cyan
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        GL11.glLineWidth(2.5F);
        GL11.glColor4f(0.8F, 1.0F, 1.0F, 1.0F);
        cache.render(indexCount);

        // Pass 2: hidden edges – no depth test, translucent, thin, dim cyan
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glLineWidth(1.0F);
        GL11.glColor4f(0.25F, 0.9F, 1.0F, 0.2F);
        cache.render(indexCount);

        GL11.glPopAttrib();
    }
}
