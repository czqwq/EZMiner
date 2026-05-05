package com.czqwq.EZMiner.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.util.ResourceLocation;

import org.lwjgl.opengl.GL11;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * A {@link GuiButton} that renders a texture icon instead of a text label.
 *
 * <p>
 * The icon is drawn at full size inside the button bounds (minus 2 px padding).
 * When the mouse hovers over the button the icon is tinted slightly darker to
 * give visual feedback.
 */
@SideOnly(Side.CLIENT)
public class TexturedButton extends GuiButton {

    private final ResourceLocation texture;

    /**
     * @param id      unique button ID (same as {@link GuiButton})
     * @param x       screen X position
     * @param y       screen Y position
     * @param w       button width in screen pixels
     * @param h       button height in screen pixels
     * @param texture the {@link ResourceLocation} of the icon texture to draw
     */
    public TexturedButton(int id, int x, int y, int w, int h, ResourceLocation texture) {
        super(id, x, y, w, h, "");
        this.texture = texture;
    }

    @Override
    public void drawButton(Minecraft mc, int mouseX, int mouseY) {
        if (!visible) return;

        boolean hovered = mouseX >= xPosition && mouseY >= yPosition
            && mouseX < xPosition + width
            && mouseY < yPosition + height;

        // Draw a simple dark box so the icon has a visible background.
        int bg = hovered ? 0xFF555555 : 0xFF333333;
        drawRect(xPosition, yPosition, xPosition + width, yPosition + height, bg);

        mc.getTextureManager().bindTexture(texture);
        GL11.glColor4f(1.0f, 1.0f, 1.0f, hovered ? 0.75f : 1.0f);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

        int pad = 2;
        int ix = xPosition + pad;
        int iy = yPosition + pad;
        int iw = width - 2 * pad;
        int ih = height - 2 * pad;

        // Use Tessellator with normalised UV so any texture size works.
        Tessellator tess = Tessellator.instance;
        tess.startDrawingQuads();
        tess.addVertexWithUV(ix, iy + ih, 0, 0.0, 1.0);
        tess.addVertexWithUV(ix + iw, iy + ih, 0, 1.0, 1.0);
        tess.addVertexWithUV(ix + iw, iy, 0, 1.0, 0.0);
        tess.addVertexWithUV(ix, iy, 0, 0.0, 0.0);
        tess.draw();

        GL11.glDisable(GL11.GL_BLEND);
        GL11.glColor4f(1.0f, 1.0f, 1.0f, 1.0f);
    }
}
