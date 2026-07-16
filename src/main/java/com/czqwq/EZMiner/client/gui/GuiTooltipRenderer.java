package com.czqwq.EZMiner.client.gui;

import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;

import org.lwjgl.opengl.GL11;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Standalone vanilla-style hover-tooltip renderer.
 *
 * <p>
 * Self-contained so any screen or overlay can draw a tooltip without relying on
 * {@link net.minecraft.client.gui.GuiScreen}'s protected helpers. Supports
 * multi-line text via {@code \n} and clamps the box to the screen bounds so it
 * never renders off-screen.
 */
@SideOnly(Side.CLIENT)
public final class GuiTooltipRenderer {

    /** Padding between the text and the tooltip box edges. */
    private static final int PAD = 3;
    /** Extra vertical spacing between lines. */
    private static final int LINE_SPACING = 2;
    /** Horizontal offset of the box from the mouse cursor. */
    private static final int CURSOR_OFFSET = 12;
    private static final int BG_COLOR = 0xF0100010;
    private static final int BORDER_COLOR = 0xFF5000AA;

    private GuiTooltipRenderer() {}

    /**
     * Draws {@code text} in a tooltip box next to the mouse cursor. Call after
     * all other GUI elements so the tooltip overlays them.
     *
     * @param fontRenderer font used to measure and draw the text
     * @param text         already-localised tooltip text; {@code \n} starts a new line
     * @param mouseX       current mouse X in GUI coordinates
     * @param mouseY       current mouse Y in GUI coordinates
     * @param screenW      scaled screen width, used for clamping
     * @param screenH      scaled screen height, used for clamping
     */
    public static void render(FontRenderer fontRenderer, String text, int mouseX, int mouseY, int screenW,
        int screenH) {
        if (text == null || text.isEmpty()) return;

        String[] lines = text.split("\n", -1);
        int textW = 0;
        for (String line : lines) {
            textW = Math.max(textW, fontRenderer.getStringWidth(line));
        }
        int textH = lines.length * fontRenderer.FONT_HEIGHT + (lines.length - 1) * LINE_SPACING;

        // Position beside the cursor, flipped/clamped to stay on screen.
        int x = mouseX + CURSOR_OFFSET;
        int y = mouseY - CURSOR_OFFSET;
        if (x + textW + PAD > screenW) {
            x = Math.max(PAD, mouseX - textW - CURSOR_OFFSET);
        }
        y = Math.max(PAD, Math.min(y, screenH - textH - PAD));

        int left = x - PAD;
        int top = y - PAD;
        int right = x + textW + PAD;
        int bottom = y + textH + PAD;

        GL11.glDisable(GL11.GL_DEPTH_TEST);
        Gui.drawRect(left, top, right, bottom, BG_COLOR);
        // 1px border
        Gui.drawRect(left, top, right, top + 1, BORDER_COLOR);
        Gui.drawRect(left, bottom - 1, right, bottom, BORDER_COLOR);
        Gui.drawRect(left, top + 1, left + 1, bottom - 1, BORDER_COLOR);
        Gui.drawRect(right - 1, top + 1, right, bottom - 1, BORDER_COLOR);

        GL11.glColor4f(1.0f, 1.0f, 1.0f, 1.0f);
        for (int i = 0; i < lines.length; i++) {
            fontRenderer.drawStringWithShadow(lines[i], x, y + i * (fontRenderer.FONT_HEIGHT + LINE_SPACING), 0xFFFFFF);
        }
        GL11.glEnable(GL11.GL_DEPTH_TEST);
    }
}
