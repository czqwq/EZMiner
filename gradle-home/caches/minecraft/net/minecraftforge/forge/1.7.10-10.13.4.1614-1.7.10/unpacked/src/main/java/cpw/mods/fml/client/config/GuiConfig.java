/*
 * Forge Mod Loader
 * Copyright (c) 2012-2014 cpw.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Lesser Public License v2.1
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/old-licenses/gpl-2.0.html
 *
 * Contributors (this class):
 *     bspkrs - implementation
 */

package cpw.mods.fml.client.config;

import static cpw.mods.fml.client.config.GuiUtils.RESET_CHAR;
import static cpw.mods.fml.client.config.GuiUtils.UNDO_CHAR;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.resources.I18n;
import net.minecraft.util.ChatComponentText;

import org.lwjgl.input.Keyboard;

import cpw.mods.fml.client.config.GuiConfigEntries.IConfigEntry;
import cpw.mods.fml.client.event.ConfigChangedEvent;
import cpw.mods.fml.client.event.ConfigChangedEvent.OnConfigChangedEvent;
import cpw.mods.fml.client.event.ConfigChangedEvent.PostConfigChangedEvent;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.eventhandler.Event.Result;

/**
 * This class is the base GuiScreen for all config GUI screens. It can be extended by mods to provide the top-level config screen
 * that will be called when the Config button is clicked from the Main Menu Mods list.
 * 
 * @author bspkrs
 */
public class GuiConfig extends GuiScreen
{
    /**
     * A reference to the screen object that created this. Used for navigating between screens.
     */
    public final GuiScreen parentScreen;
    public String title = "Config GUI";
    public String titleLine2;
    @SuppressWarnings("rawtypes")
    public final List<IConfigElement> configElements;
    @SuppressWarnings("rawtypes")
    public final List<IConfigEntry> initEntries;
    public GuiConfigEntries entryList;
    private GuiButtonExt btnDefaultAll;
    private GuiButtonExt btnUndoAll;
    private GuiCheckBox chkApplyGlobally;
    public final String modID;
    /**
     * When set to a non-null value the OnConfigChanged and PostConfigChanged events will be posted when the Done button is pressed
     * if any configElements were changed (includes child screens). If not defined, the events will be posted if the parent gui is null
     * or if the parent gui is not an instance of GuiConfig.
     */
    public final String configID;
    public final boolean isWorldRunning;
    public final boolean allRequireWorldRestart;
    public final boolean allRequireMcRestart;
    public boolean needsRefresh = true;
    private HoverChecker undoHoverChecker;
    private HoverChecker resetHoverChecker;
    private HoverChecker checkBoxHoverChecker;

    /**
     * GuiConfig constructor that will use ConfigChangedEvent when editing is concluded. If a non-null value is passed for configID,
     * the OnConfigChanged and PostConfigChanged events will be posted when the Done button is pressed if any configElements were changed
     * (includes child screens). If configID is not defined, the events will be posted if the parent gui is null or if the parent gui 
     * is not an instance of GuiConfig.
     *
     * @param parentScreen the parent GuiScreen object
     * @param configElements a List of IConfigProperty objects
     * @param modID the mod ID for the mod whose config settings will be edited
     * @param configID an identifier that will be passed to the OnConfigChanged and PostConfigChanged events. Setting this value will force 
     *            the save action to be called when the Done button is pressed on this screen if any configElements were changed.
     * @param allRequireWorldRestart send true if all configElements on this screen require a world restart
     * @param allRequireMcRestart send true if all configElements on this screen require MC to be restarted
     * @param title the desired title for this screen. For consistency it is recommended that you pass the path of the config file being
     *            edited.
     */
    @SuppressWarnings("rawtypes")
    public GuiConfig(GuiScreen parentScreen, List<IConfigElement> configElements, String modID, String configID, 
            boolean allRequireWorldRestart, boolean allRequireMcRestart, String title)
    {
        this(parentScreen, configElements, modID, configID, allRequireWorldRestart, allRequireMcRestart, title, null);
    }

    /**
     * GuiConfig constructor that will use ConfigChangedEvent when editing is concluded. This constructor passes null for configID.
     * If configID is not defined, the events will be posted if the parent gui is null or if the parent gui is not an instance of GuiConfig.
     *
     * @param parentScreen the parent GuiScreen object
     * @param configElements a List of IConfigProperty objects
     * @param modID the mod ID for the mod whose config settings will be edited
     * @param allRequireWorldRestart send true if all configElements on this screen require a world restart
     * @param allRequireMcRestart send true if all configElements on this screen require MC to be restarted
     * @param title the desired title for this screen. For consistency it is recommended that you pass the path of the config file being
     *            edited.
     */
    @SuppressWarnings("rawtypes")
    public GuiConfig(GuiScreen parentScreen, List<IConfigElement> configElements, String modID, 
            boolean allRequireWorldRestart, boolean allRequireMcRestart, String title)
    {
        this(parentScreen, configElements, modID, null, allRequireWorldRestart, allRequireMcRestart, title, null);
    }

    /**
     * GuiConfig constructor that will use ConfigChangedEvent when editing is concluded. This constructor passes null for configID.
     * If configID is not defined, the events will be posted if the parent gui is null or if the parent gui is not an instance of GuiConfig.
     *
     * @param parentScreen the parent GuiScreen object
     * @param configElements a List of IConfigProperty objects
     * @param modID the mod ID for the mod whose config settings will be edited
     * @param allRequireWorldRestart send true if all configElements on this screen require a world restart
     * @param allRequireMcRestart send true if all configElements on this screen require MC to be restarted
     * @param title the desired title for this screen. For consistency it is recommended that you pass the path of the config file being
     *            edited.
     * @param titleLine2 the desired title second line for this screen. Typically this is used to send the category name of the category
     *            currently being edited.
     */
    @SuppressWarnings("rawtypes")
    public GuiConfig(GuiScreen parentScreen, List<IConfigElement> configElements, String modID, 
            boolean allRequireWorldRestart, boolean allRequireMcRestart, String title, String titleLine2)
    {
        this(parentScreen, configElements, modID, null, allRequireWorldRestart, allRequireMcRestart, title, titleLine2);
    }

    /**
     * GuiConfig constructor that will use ConfigChangedEvent when editing is concluded. titleLine2 is specified in this constructor.
     * If a non-null value is passed for configID, the OnConfigChanged and PostConfigChanged events will be posted when the Done button is 
     * pressed if any configElements were changed (includes child screens). If configID is not defined, the events will be posted if the parent 
     * gui is null or if the parent gui is not an instance of GuiConfig.
     *
     * @param parentScreen the parent GuiScreen object
     * @param configElements a List of IConfigProperty objects
     * @param modID the mod ID for the mod whose config settings will be edited
     * @param configID an identifier that will be passed to the OnConfigChanged and PostConfigChanged events
     * @param allRequireWorldRestart send true if all configElements on this screen require a world restart
     * @param allRequireMcRestart send true if all configElements on this screen require MC to be restarted
     * @param title the desired title for this screen. For consistency it is recommended that you pass the path of the config file being
     *            edited.
     * @param titleLine2 the desired title second line for this screen. Typically this is used to send the category name of the category
     *            currently being edited.
     */
    @SuppressWarnings("rawtypes")
    public GuiConfig(GuiScreen parentScreen, List<IConfigElement> configElements, String modID, String configID, 
            boolean allRequireWorldRestart, boolean allRequireMcRestart, String title, String titleLine2)
    {
        this.field_146297_k = Minecraft.func_71410_x();
        this.parentScreen = parentScreen;
        this.configElements = configElements;
        this.entryList = new GuiConfigEntries(this, field_146297_k);
        this.initEntries = new ArrayList<IConfigEntry>(entryList.listEntries);
        this.allRequireWorldRestart = allRequireWorldRestart;
        this.allRequireMcRestart = allRequireMcRestart;
        this.modID = modID;
        this.configID = configID;
        this.isWorldRunning = field_146297_k.field_71441_e != null;
        if (title != null)
            this.title = title;
        this.titleLine2 = titleLine2;
        if (this.titleLine2 != null && this.titleLine2.startsWith(" > "))
            this.titleLine2 = this.titleLine2.replaceFirst(" > ", "");
    }

    public static String getAbridgedConfigPath(String path)
    {
        Minecraft mc = Minecraft.func_71410_x();
        if (mc.field_71412_D.getAbsolutePath().endsWith("."))
            return path.replace("\\", "/").replace(mc.field_71412_D.getAbsolutePath().replace("\\", "/").substring(0, mc.field_71412_D.getAbsolutePath().length() - 1), "/.minecraft/");
        else
            return path.replace("\\", "/").replace(mc.field_71412_D.getAbsolutePath().replace("\\", "/"), "/.minecraft");
    }

    @SuppressWarnings("unchecked")
    @Override
    public void func_73866_w_()
    {
        Keyboard.enableRepeatEvents(true);

        if (this.entryList == null || this.needsRefresh)
        {
            this.entryList = new GuiConfigEntries(this, field_146297_k);
            this.needsRefresh = false;
        }

        int undoGlyphWidth = field_146297_k.field_71466_p.func_78256_a(UNDO_CHAR) * 2;
        int resetGlyphWidth = field_146297_k.field_71466_p.func_78256_a(RESET_CHAR) * 2;
        int doneWidth = Math.max(field_146297_k.field_71466_p.func_78256_a(I18n.func_135052_a("gui.done")) + 20, 100);
        int undoWidth = field_146297_k.field_71466_p.func_78256_a(" " + I18n.func_135052_a("fml.configgui.tooltip.undoChanges")) + undoGlyphWidth + 20;
        int resetWidth = field_146297_k.field_71466_p.func_78256_a(" " + I18n.func_135052_a("fml.configgui.tooltip.resetToDefault")) + resetGlyphWidth + 20;
        int checkWidth = field_146297_k.field_71466_p.func_78256_a(I18n.func_135052_a("fml.configgui.applyGlobally")) + 13;
        int buttonWidthHalf = (doneWidth + 5 + undoWidth + 5 + resetWidth + 5 + checkWidth) / 2;
        this.field_146292_n.add(new GuiButtonExt(2000, this.field_146294_l / 2 - buttonWidthHalf, this.field_146295_m - 29, doneWidth, 20, I18n.func_135052_a("gui.done")));
        this.field_146292_n.add(this.btnDefaultAll = new GuiUnicodeGlyphButton(2001, this.field_146294_l / 2 - buttonWidthHalf + doneWidth + 5 + undoWidth + 5,
                this.field_146295_m - 29, resetWidth, 20, " " + I18n.func_135052_a("fml.configgui.tooltip.resetToDefault"), RESET_CHAR, 2.0F));
        this.field_146292_n.add(btnUndoAll = new GuiUnicodeGlyphButton(2002, this.field_146294_l / 2 - buttonWidthHalf + doneWidth + 5,
                this.field_146295_m - 29, undoWidth, 20, " " + I18n.func_135052_a("fml.configgui.tooltip.undoChanges"), UNDO_CHAR, 2.0F));
        this.field_146292_n.add(chkApplyGlobally = new GuiCheckBox(2003, this.field_146294_l / 2 - buttonWidthHalf + doneWidth + 5 + undoWidth + 5 + resetWidth + 5,
                this.field_146295_m - 24, I18n.func_135052_a("fml.configgui.applyGlobally"), false));

        this.undoHoverChecker = new HoverChecker(this.btnUndoAll, 800);
        this.resetHoverChecker = new HoverChecker(this.btnDefaultAll, 800);
        this.checkBoxHoverChecker = new HoverChecker(chkApplyGlobally, 800);
        this.entryList.initGui();
    }

    @Override
    public void func_146281_b()
    {
        this.entryList.onGuiClosed();
        
        if (this.configID != null && this.parentScreen instanceof GuiConfig)
        {
            GuiConfig parentGuiConfig = (GuiConfig) this.parentScreen;
            parentGuiConfig.needsRefresh = true;
            parentGuiConfig.func_73866_w_();
        }
        
        if (!(this.parentScreen instanceof GuiConfig))
            Keyboard.enableRepeatEvents(false);
    }

    @Override
    protected void func_146284_a(GuiButton button)
    {
        if (button.field_146127_k == 2000)
        {
            boolean flag = true;
            try
            {
                if ((configID != null || this.parentScreen == null || !(this.parentScreen instanceof GuiConfig)) 
                        && (this.entryList.hasChangedEntry(true)))
                {
                    boolean requiresMcRestart = this.entryList.saveConfigElements();

                    if (Loader.isModLoaded(modID))
                    {
                        ConfigChangedEvent event = new OnConfigChangedEvent(modID, configID, isWorldRunning, requiresMcRestart);
                        FMLCommonHandler.instance().bus().post(event);
                        if (!event.getResult().equals(Result.DENY))
                            FMLCommonHandler.instance().bus().post(new PostConfigChangedEvent(modID, configID, isWorldRunning, requiresMcRestart));
                        
                        if (requiresMcRestart)
                        {
                            flag = false;
                            field_146297_k.func_147108_a(new GuiMessageDialog(parentScreen, "fml.configgui.gameRestartTitle", 
                                    new ChatComponentText(I18n.func_135052_a("fml.configgui.gameRestartRequired")), "fml.configgui.confirmRestartMessage"));
                        }
                        
                        if (this.parentScreen instanceof GuiConfig)
                            ((GuiConfig) this.parentScreen).needsRefresh = true;
                    }
                }
            }
            catch (Throwable e)
            {
                e.printStackTrace();
            }
            
            if (flag)
                this.field_146297_k.func_147108_a(this.parentScreen);
        }
        else if (button.field_146127_k == 2001)
        {
            this.entryList.setAllToDefault(this.chkApplyGlobally.isChecked());
        }
        else if (button.field_146127_k == 2002)
        {
            this.entryList.undoAllChanges(this.chkApplyGlobally.isChecked());
        }
    }

    @Override
    protected void func_73864_a(int x, int y, int mouseEvent)
    {
        if (mouseEvent != 0 || !this.entryList.func_148179_a(x, y, mouseEvent))
        {
            this.entryList.mouseClicked(x, y, mouseEvent);
            super.func_73864_a(x, y, mouseEvent);
        }
    }

    @Override
    protected void func_146286_b(int x, int y, int mouseEvent)
    {
        if (mouseEvent != 0 || !this.entryList.func_148181_b(x, y, mouseEvent))
        {
            super.func_146286_b(x, y, mouseEvent);
        }
    }

    @Override
    protected void func_73869_a(char eventChar, int eventKey)
    {
        if (eventKey == Keyboard.KEY_ESCAPE)
            this.field_146297_k.func_147108_a(parentScreen);
        else
            this.entryList.keyTyped(eventChar, eventKey);
    }

    @Override
    public void func_73876_c()
    {
        super.func_73876_c();
        this.entryList.updateScreen();
    }

    @Override
    public void func_73863_a(int mouseX, int mouseY, float partialTicks)
    {
        this.func_146276_q_();
        this.entryList.func_148128_a(mouseX, mouseY, partialTicks);
        this.func_73732_a(this.field_146289_q, this.title, this.field_146294_l / 2, 8, 16777215);
        String title2 = this.titleLine2;

        if (title2 != null)
        {
            int strWidth = field_146297_k.field_71466_p.func_78256_a(title2);
            int elipsisWidth = field_146297_k.field_71466_p.func_78256_a("...");
            if (strWidth > field_146294_l - 6 && strWidth > elipsisWidth)
                title2 = field_146297_k.field_71466_p.func_78269_a(title2, field_146294_l - 6 - elipsisWidth).trim() + "...";
            this.func_73732_a(this.field_146289_q, title2, this.field_146294_l / 2, 18, 16777215);
        }

        this.btnUndoAll.field_146124_l = this.entryList.areAnyEntriesEnabled(this.chkApplyGlobally.isChecked()) && this.entryList.hasChangedEntry(this.chkApplyGlobally.isChecked());
        this.btnDefaultAll.field_146124_l = this.entryList.areAnyEntriesEnabled(this.chkApplyGlobally.isChecked()) && !this.entryList.areAllEntriesDefault(this.chkApplyGlobally.isChecked());
        super.func_73863_a(mouseX, mouseY, partialTicks);
        this.entryList.drawScreenPost(mouseX, mouseY, partialTicks);
        if (this.undoHoverChecker.checkHover(mouseX, mouseY))
            this.drawToolTip(this.field_146297_k.field_71466_p.func_78271_c(I18n.func_135052_a("fml.configgui.tooltip.undoAll"), 300), mouseX, mouseY);
        if (this.resetHoverChecker.checkHover(mouseX, mouseY))
            this.drawToolTip(this.field_146297_k.field_71466_p.func_78271_c(I18n.func_135052_a("fml.configgui.tooltip.resetAll"), 300), mouseX, mouseY);
        if (this.checkBoxHoverChecker.checkHover(mouseX, mouseY))
            this.drawToolTip(this.field_146297_k.field_71466_p.func_78271_c(I18n.func_135052_a("fml.configgui.tooltip.applyGlobally"), 300), mouseX, mouseY);
    }

    @SuppressWarnings("rawtypes")
    public void drawToolTip(List stringList, int x, int y)
    {
        this.func_146283_a(stringList, x, y);
    }
}