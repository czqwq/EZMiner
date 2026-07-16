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
 * Matches the visual style of ServerUtilities sidebar buttons: the icon is
 * drawn at full button size with a semi-transparent white hover overlay.
 * No background box is drawn so it blends with the inventory panel.
 */
@SideOnly(Side.CLIENT)
public class TexturedButton extends GuiButton {

    private final ResourceLocation texture;

    /** Optional hover tooltip text; {@code null} = no tooltip. */
    private String tooltip;

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

    /** Sets the hover tooltip text (already localised). Returns {@code this} for chaining. */
    public TexturedButton setTooltip(String tooltip) {
        this.tooltip = tooltip;
        return this;
    }

    /** The hover tooltip text, or {@code null} when none is set. */
    public String getTooltip() {
        return tooltip;
    }

    /** True when this button is visible and the mouse is over it. */
    public boolean isHovered(int mouseX, int mouseY) {
        return visible && mouseX >= xPosition
            && mouseY >= yPosition
            && mouseX < xPosition + width
            && mouseY < yPosition + height;
    }

    @Override
    public void drawButton(Minecraft mc, int mouseX, int mouseY) {
        if (!visible) return;

        boolean hovered = isHovered(mouseX, mouseY);

        // Draw the settings icon (full button area, no padding) -----------------
        mc.getTextureManager()
            .bindTexture(texture);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glColor4f(1.0f, 1.0f, 1.0f, 1.0f);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

        Tessellator tess = Tessellator.instance;
        tess.startDrawingQuads();
        tess.addVertexWithUV(xPosition, yPosition + height, 0, 0.0, 1.0);
        tess.addVertexWithUV(xPosition + width, yPosition + height, 0, 1.0, 1.0);
        tess.addVertexWithUV(xPosition + width, yPosition, 0, 1.0, 0.0);
        tess.addVertexWithUV(xPosition, yPosition, 0, 0.0, 0.0);
        tess.draw();

        // Hover highlight: semi-transparent white overlay (matches SU) ----------
        if (hovered) {
            GL11.glDisable(GL11.GL_TEXTURE_2D);
            GL11.glColor4f(1.0f, 1.0f, 1.0f, 0.13f);
            tess.startDrawingQuads();
            tess.addVertex(xPosition, yPosition + height, 0);
            tess.addVertex(xPosition + width, yPosition + height, 0);
            tess.addVertex(xPosition + width, yPosition, 0);
            tess.addVertex(xPosition, yPosition, 0);
            tess.draw();
            GL11.glEnable(GL11.GL_TEXTURE_2D);
        }

        GL11.glDisable(GL11.GL_BLEND);
        GL11.glColor4f(1.0f, 1.0f, 1.0f, 1.0f);
    }
}
