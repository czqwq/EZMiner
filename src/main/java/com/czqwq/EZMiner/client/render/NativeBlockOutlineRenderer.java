package com.czqwq.EZMiner.client.render;

import org.lwjgl.opengl.GL11;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Original single-pass wireframe renderer.
 *
 * <p>
 * Draws all outline edges in a single pass with depth testing disabled, so the preview
 * is always visible through solid geometry.
 */
@SideOnly(Side.CLIENT)
public class NativeBlockOutlineRenderer implements BlockOutlineRenderStrategy {

    @Override
    public void render(RenderCache cache, int indexCount) {
        if (indexCount <= 0) return;

        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);

        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_CULL_FACE);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glLineWidth(2.0F);
        GL11.glColor4f(0.25F, 0.9F, 1.0F, 0.8F);

        cache.render(indexCount);

        GL11.glPopAttrib();
    }
}
