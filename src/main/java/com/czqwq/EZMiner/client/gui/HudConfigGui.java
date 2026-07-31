package com.czqwq.EZMiner.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.resources.I18n;

import org.lwjgl.input.Keyboard;

import com.czqwq.EZMiner.Config;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * A minimal, transparent overlay that lets the player drag the EZMiner HUD to a
 * new position on screen.
 *
 * <p>
 * Unlike a normal {@link GuiScreen}, this screen:
 * <ul>
 * <li>Does <strong>not</strong> draw a dark background — the game world,
 * other HUDs, and the EZMiner HUD itself all remain visible behind it.</li>
 * <li>Does <strong>not</strong> pause the game ({@link #doesGuiPauseGame()}
 * returns {@code false}).</li>
 * <li>Renders only a small drag handle (red square with crosshair) at the
 * current HUD anchor point.</li>
 * </ul>
 *
 * <p>
 * <b>Usage:</b> press the bound key (default unbound) or run
 * {@code /EZMiner hud config}. Drag the red handle to reposition the HUD.
 * Press {@code Esc} or click anywhere outside the handle to close and save.
 *
 * <p>
 * <b>Module decoupling:</b> this class only reads/writes {@link Config#hudPosX}
 * and {@link Config#hudPosY} via {@link Config#saveHudPos(int, int)}. It has
 * no dependency on {@link com.czqwq.EZMiner.client.HudRenderer} or any other
 * EZMiner subsystem.
 */
@SideOnly(Side.CLIENT)
public class HudConfigGui extends GuiScreen {

    // ── Drag-handle visuals ────────────────────────────────────────────────────
    private static final int HANDLE_SIZE = 12;
    private static final int HANDLE_COLOR = 0xCC_FF3232; // semi-transparent red
    private static final int CROSSHAIR_COLOR = 0xFF_FFFFFF; // opaque white
    private static final int TIP_COLOR = 0xFF_CCCCCC; // light gray

    private boolean dragging;
    private int dragOffsetX;
    private int dragOffsetY;

    // ── GuiScreen overrides ────────────────────────────────────────────────────

    @Override
    public void initGui() {
        // Intentionally empty — no buttons, no background texture.
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        // When the user prefers a modal config screen, draw the default
        // darkened background to hide the world and other HUDs behind.
        if (!Config.hudConfigShowOtherHuds) {
            drawDefaultBackground();
        }

        final int hx = Config.hudPosX;
        final int hy = Config.hudPosY;
        final int hs = HANDLE_SIZE;
        final int half = hs / 2;

        // Draw the drag-handle square.
        drawRect(hx - half, hy - half, hx + half, hy + half, HANDLE_COLOR);

        // Draw crosshair lines so the handle is easy to locate.
        drawHorizontalLine(hx - half, hx + half - 1, hy, CROSSHAIR_COLOR);
        drawVerticalLine(hx, hy - half, hy + half - 1, CROSSHAIR_COLOR);

        // Draw a small instruction tip near the handle.
        final String tip = I18n.format("ezminer.hud.config.dragTip");
        final int tipX = hx + half + 4;
        final int tipY = hy - half - mc.fontRenderer.FONT_HEIGHT - 2;
        drawString(mc.fontRenderer, tip, tipX, tipY, TIP_COLOR);

        // Also draw the HUD position coordinate readout.
        final String pos = hx + ", " + hy;
        drawString(mc.fontRenderer, pos, hx + half + 4, hy - half + 2, TIP_COLOR);
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int button) {
        if (button == 0 && isOnHandle(mouseX, mouseY)) {
            dragging = true;
            dragOffsetX = mouseX - Config.hudPosX;
            dragOffsetY = mouseY - Config.hudPosY;
            return; // consume the click — don't pass to super
        }
        // Clicking anywhere else closes the GUI (same as Esc).
        closeAndSave();
    }

    @Override
    protected void mouseClickMove(int mouseX, int mouseY, int clickedButton, long timeSinceLastClick) {
        if (!dragging) return;
        Config.hudPosX = clampX(mouseX - dragOffsetX);
        Config.hudPosY = clampY(mouseY - dragOffsetY);
    }

    @Override
    protected void mouseMovedOrUp(int mouseX, int mouseY, int which) {
        if (dragging && which == 0) {
            Config.hudPosX = clampX(mouseX - dragOffsetX);
            Config.hudPosY = clampY(mouseY - dragOffsetY);
            Config.saveHudPos(Config.hudPosX, Config.hudPosY);
            dragging = false;
        }
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) {
        // Esc closes the GUI. The current position is already saved on every
        // drag release, so no extra save is needed.
        if (keyCode == Keyboard.KEY_ESCAPE || keyCode == this.mc.gameSettings.keyBindInventory.getKeyCode()) {
            if (dragging) {
                Config.saveHudPos(Config.hudPosX, Config.hudPosY);
                dragging = false;
            }
            Config.hudConfigGuiOpen = false;
            this.mc.displayGuiScreen(null);
            return;
        }
        // Arrow keys for fine-tuning (±1 px).
        if (keyCode == Keyboard.KEY_UP) {
            Config.hudPosY = clampY(Config.hudPosY - 1);
            Config.saveHudPos(Config.hudPosX, Config.hudPosY);
        } else if (keyCode == Keyboard.KEY_DOWN) {
            Config.hudPosY = clampY(Config.hudPosY + 1);
            Config.saveHudPos(Config.hudPosX, Config.hudPosY);
        } else if (keyCode == Keyboard.KEY_LEFT) {
            Config.hudPosX = clampX(Config.hudPosX - 1);
            Config.saveHudPos(Config.hudPosX, Config.hudPosY);
        } else if (keyCode == Keyboard.KEY_RIGHT) {
            Config.hudPosX = clampX(Config.hudPosX + 1);
            Config.saveHudPos(Config.hudPosX, Config.hudPosY);
        }
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private boolean isOnHandle(int mouseX, int mouseY) {
        final int half = HANDLE_SIZE / 2;
        return mouseX >= Config.hudPosX - half && mouseX <= Config.hudPosX + half
            && mouseY >= Config.hudPosY - half
            && mouseY <= Config.hudPosY + half;
    }

    private void closeAndSave() {
        if (dragging) {
            Config.saveHudPos(Config.hudPosX, Config.hudPosY);
            dragging = false;
        }
        Config.hudConfigGuiOpen = false;
        this.mc.displayGuiScreen(null);
    }

    /** Clamp X so the HUD anchor stays visible (at least 0 px from left edge). */
    private static int clampX(int x) {
        if (x < 0) return 0;
        // Allow any positive value; the player can scroll it back if needed.
        return x;
    }

    /** Clamp Y so the HUD anchor stays visible (at least 0 px from top edge). */
    private static int clampY(int y) {
        if (y < 0) return 0;
        return y;
    }

    // ── Static factory (for use from packet handler / command / keybind) ───────

    /** Open the HUD drag-config screen on the client. Safe to call from any thread. */
    public static void open() {
        Config.hudConfigGuiOpen = true;
        Minecraft.getMinecraft()
            .displayGuiScreen(new HudConfigGui());
    }
}
