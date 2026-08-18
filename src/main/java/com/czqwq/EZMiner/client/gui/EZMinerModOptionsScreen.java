package com.czqwq.EZMiner.client.gui;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiIngameMenu;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.resources.I18n;
import net.minecraft.util.ChatComponentText;

import com.czqwq.EZMiner.EZMiner;

import cpw.mods.fml.client.FMLClientHandler;
import cpw.mods.fml.client.IModGuiFactory;
import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.ModContainer;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Functional replacement for FML's in-game "Mod Options..." screen
 * ({@code GuiIngameModOptions}/{@code GuiModOptionList}).
 *
 * <p>
 * The FML stub in 1.7.10 (which is what dev environments show) hardcodes three
 * debug rows — "Test 1", "TEST 2", "DISABLED" — and can never open any config
 * GUI. This screen lists every loaded mod that provides a main config GUI via
 * {@link IModGuiFactory#mainConfigGuiClass()} (EZMiner included once its {@link
 * EZMinerGuiFactory} is registered) and opens it on click. It is opened from the
 * pause menu's "Mod Options..." button via {@link MixinGuiIngameMenu}.
 */
@SideOnly(Side.CLIENT)
public class EZMinerModOptionsScreen extends GuiScreen {

    private static final int BTN_DONE = 200;

    private final GuiScreen parentScreen;
    private final List<ModContainer> mods = new ArrayList<>();

    private EZMinerModOptionList optionList;

    /** Entry point called by {@link MixinGuiIngameMenu} when the pause-menu button is pressed. */
    public static void open(GuiIngameMenu from) {
        Minecraft.getMinecraft()
            .displayGuiScreen(new EZMinerModOptionsScreen(from));
    }

    public EZMinerModOptionsScreen(GuiScreen parent) {
        this.parentScreen = parent;
        for (ModContainer mod : Loader.instance()
            .getActiveModList()) {
            IModGuiFactory factory = FMLClientHandler.instance()
                .getGuiFactoryFor(mod);
            if (factory != null && factory.mainConfigGuiClass() != null) {
                mods.add(mod);
            }
        }
    }

    @Override
    public void initGui() {
        buttonList.clear();
        this.optionList = new EZMinerModOptionList(this, mods);
        this.optionList.registerScrollButtons(this.buttonList, 7, 8);
        buttonList
            .add(new GuiButton(BTN_DONE, width / 2 - 100, height / 6 + 168, 200, 20, I18n.format("ezminer.gui.done")));
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button.enabled && button.id == BTN_DONE) {
            mc.gameSettings.saveOptions();
            mc.displayGuiScreen(parentScreen);
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        if (optionList != null) {
            optionList.drawScreen(mouseX, mouseY, partialTicks);
        }
        drawCenteredString(fontRendererObj, I18n.format("ezminer.gui.modoptions.title"), width / 2, 15, 0xFFFFFF);
        if (mods.isEmpty()) {
            drawCenteredString(fontRendererObj, I18n.format("ezminer.gui.modoptions.empty"), width / 2, 50, 0xDDDDDD);
        }
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    /** Called by {@link EZMinerModOptionList} when the player clicks a mod row. */
    void onModEntryClicked(int index) {
        if (index < 0 || index >= mods.size()) return;
        ModContainer mod = mods.get(index);
        try {
            IModGuiFactory factory = FMLClientHandler.instance()
                .getGuiFactoryFor(mod);
            Class<? extends GuiScreen> cls = factory.mainConfigGuiClass();
            GuiScreen configScreen = cls.getConstructor(GuiScreen.class)
                .newInstance(this);
            mc.displayGuiScreen(configScreen);
        } catch (Exception e) {
            EZMiner.LOG.error("Failed to open config GUI for mod {}", mod.getModId(), e);
            if (mc.thePlayer != null) {
                mc.thePlayer.addChatMessage(
                    new ChatComponentText(I18n.format("ezminer.gui.modoptions.openFailed", mod.getName())));
            }
        }
    }

    Minecraft getMinecraftInstance() {
        return mc;
    }

    FontRenderer getFontRenderer() {
        return fontRendererObj;
    }
}
