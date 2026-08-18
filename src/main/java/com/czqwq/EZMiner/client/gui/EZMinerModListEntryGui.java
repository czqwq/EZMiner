package com.czqwq.EZMiner.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.resources.I18n;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Screen opened by Forge's mod list "Config" button (via {@link
 * EZMinerGuiFactory#mainConfigGuiClass()}).
 *
 * <p>
 * <b>Main menu guard:</b> the mod list is reachable from the main menu, where
 * there is no world/player yet. <code>EZMinerConfigGui</code> reads/writes live
 * config and talks to the server, so it must not open there. When opened on the
 * main menu this screen instead shows a localized hint and a "Done" button that
 * returns to the mod list. When opened in-game it simply hands over to {@link
 * EZMinerConfigGui}, returning to {@code this} screen's parent on close.
 */
@SideOnly(Side.CLIENT)
public class EZMinerModListEntryGui extends GuiScreen {

    private static final int BTN_DONE = 0;

    private final GuiScreen parentScreen;
    private final boolean blocked;

    /** Reflective entry point used by {@link cpw.mods.fml.client.GuiModList}. */
    public EZMinerModListEntryGui(GuiScreen parent) {
        this.parentScreen = parent;
        Minecraft mc = Minecraft.getMinecraft();
        this.blocked = mc.thePlayer == null || mc.theWorld == null;
    }

    @Override
    public void initGui() {
        buttonList.clear();
        if (!blocked) {
            // In-game: hand over to the real config GUI. On close it returns to
            // parentScreen (retargeted by BTN_CLOSE / BTN_CLIENT_SAVE / Esc).
            mc.displayGuiScreen(new EZMinerConfigGui(parentScreen));
            return;
        }
        buttonList
            .add(new GuiButton(BTN_DONE, width / 2 - 100, height / 6 + 168, 200, 20, I18n.format("ezminer.gui.done")));
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button.enabled && button.id == BTN_DONE) {
            mc.displayGuiScreen(parentScreen);
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        drawCenteredString(fontRendererObj, I18n.format("ezminer.gui.needIngame"), width / 2, height / 3, 0xE0A0A0);
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}
