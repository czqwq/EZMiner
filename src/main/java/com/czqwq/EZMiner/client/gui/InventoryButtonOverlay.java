package com.czqwq.EZMiner.client.gui;

import java.awt.Rectangle;
import java.lang.reflect.Field;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.inventory.GuiContainerCreative;
import net.minecraft.client.gui.inventory.GuiInventory;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.common.MinecraftForge;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Injects a small EZMiner config button into both the vanilla survival inventory
 * screen and the creative inventory screen.
 *
 * <p>
 * When ServerUtilities is present the button is positioned dynamically below
 * SU's entire sidebar block (read from {@code GuiSidebar.lastDrawnArea} via
 * reflection so the layout stays correct regardless of how many SU buttons are
 * enabled or whether the sidebar has been dragged). When SU is absent the
 * button falls back to a fixed position at the left edge of the inventory panel.
 *
 * <p>
 * Uses Forge's {@link GuiScreenEvent} so no Mixin or reflection is required.
 *
 * <p>
 * <strong>Texture note:</strong> Minecraft 1.7.10's {@code ResourceLocation}
 * constructor lowercases the domain, so the assets folder name must also be
 * all-lowercase ({@code assets/ezminer/}).
 */
@SideOnly(Side.CLIENT)
public class InventoryButtonOverlay {

    /** Unique button ID – chosen high enough not to clash with any vanilla inventory buttons. */
    private static final int BTN_ID = 1000;

    /**
     * Button size in screen pixels.
     * Matches the 16 × 16 px icon size used by ServerUtilities sidebar buttons.
     */
    private static final int BTN_SIZE = 16;

    /**
     * Horizontal distance from guiLeft at which SU places its sidebar column.
     * {@code GuiSidebar.setButtonLocations}: {@code button.x = gui.guiLeft - offsetX - buttonY*17}.
     * For the default column (buttonY=0) this equals {@code guiLeft - 18}.
     */
    private static final int SU_OFFSET_X = 18;

    /**
     * Y offset of SU's first sidebar button in the survival inventory
     * (from {@code GuiSidebar.setButtonLocations}: {@code offsetY = 8}).
     * Used as the fallback Y when SU is not installed.
     */
    private static final int SU_FALLBACK_Y_SURVIVAL = 8;

    /**
     * Y offset of SU's first sidebar button in the creative inventory
     * (from {@code GuiSidebar.setButtonLocations}: {@code offsetY = 6} for GuiContainerCreative).
     * Used as the fallback Y when SU is not installed.
     */
    private static final int SU_FALLBACK_Y_CREATIVE = 6;

    /** Vanilla survival inventory panel size (GuiInventory). */
    private static final int INV_X_SIZE = 176;
    private static final int INV_Y_SIZE = 166;

    /** Vanilla creative inventory panel size (GuiContainerCreative). */
    private static final int CREATIVE_X_SIZE = 195;
    private static final int CREATIVE_Y_SIZE = 136;

    /**
     * Settings icon texture.
     * Domain must be all-lowercase because MC 1.7.10's {@code ResourceLocation}
     * lowercases the domain, matching the {@code assets/ezminer/} folder.
     */
    private static final ResourceLocation SETTINGS_TEXTURE = new ResourceLocation(
        "ezminer",
        "textures/icons/settings.png");

    // ── Reflection cache for GuiSidebar.lastDrawnArea ────────────────────────

    /** {@code true} once the one-time field lookup has been attempted. */
    private static boolean suFieldLookupDone = false;

    /**
     * Cached reference to {@code serverutils.client.gui.GuiSidebar.lastDrawnArea},
     * or {@code null} if SU is not on the class-path.
     */
    private static Field suLastDrawnAreaField = null;

    // ── State ─────────────────────────────────────────────────────────────────

    /** Reference to the button we most recently added so we can reposition it each frame. */
    private TexturedButton ourButton = null;

    // ── Event handlers ────────────────────────────────────────────────────────

    /**
     * After the inventory GUI is initialised, inject the EZMiner config button
     * to the left of the inventory panel. The initial position is a safe default;
     * it is refined each frame by {@link #onDrawScreenPre}.
     */
    @SubscribeEvent
    @SuppressWarnings("unchecked")
    public void onInitGui(GuiScreenEvent.InitGuiEvent.Post event) {
        int[] offsets = guiOffsets(event.gui);
        if (offsets == null) return;
        // Place at a conservative default: same column as SU, below a full 6-button
        // grid (6 × 17 px = 102 px). The real position is refined in onDrawScreenPre.
        int btnX = offsets[0] - SU_OFFSET_X;
        int btnY = offsets[1] + offsets[2];
        ourButton = new TexturedButton(BTN_ID, btnX, btnY, BTN_SIZE, BTN_SIZE, SETTINGS_TEXTURE);
        event.buttonList.add(ourButton);
    }

    /**
     * Before each frame is drawn, reposition the button to sit directly below
     * SU's sidebar block (using {@code GuiSidebar.lastDrawnArea}) so the two
     * never overlap regardless of how many SU buttons are visible or whether the
     * sidebar has been dragged.
     */
    @SubscribeEvent
    public void onDrawScreenPre(GuiScreenEvent.DrawScreenEvent.Pre event) {
        if (ourButton == null) return;
        int[] offsets = guiOffsets(event.gui);
        if (offsets == null) return;

        Rectangle suArea = getSuLastDrawnArea();
        int btnX;
        int btnY;
        if (suArea != null && suArea.width > 0 && suArea.height > 0) {
            // Align with SU's drawn area: same left edge, just below the bottom.
            btnX = suArea.x + 2; // +2 skips SU's 2-px bounding-box padding
            btnY = suArea.y + suArea.height; // bottom of SU area (includes 2-px padding)
        } else {
            // SU absent or has no visible buttons: fall back to the top of the SU slot.
            btnX = offsets[0] - SU_OFFSET_X;
            btnY = offsets[1] + offsets[2];
        }
        ourButton.xPosition = btnX;
        ourButton.yPosition = btnY;
    }

    /**
     * When the injected button is clicked, open the EZMiner config GUI.
     */
    @SubscribeEvent
    public void onActionPerformed(GuiScreenEvent.ActionPerformedEvent.Post event) {
        if (guiOffsets(event.gui) == null) return;
        if (event.button.id != BTN_ID) return;
        Minecraft.getMinecraft()
            .displayGuiScreen(new EZMinerConfigGui());
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    /** Registers this overlay with the Forge and FML event buses. */
    public void registry() {
        MinecraftForge.EVENT_BUS.register(this);
        FMLCommonHandler.instance()
            .bus()
            .register(this);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Returns {@code [guiLeft, guiTop, suFallbackOffsetY]} for a supported inventory GUI,
     * or {@code null} if the GUI is not a recognised inventory screen.
     */
    private static int[] guiOffsets(net.minecraft.client.gui.GuiScreen gui) {
        if (gui instanceof GuiInventory) {
            int guiLeft = (gui.width - INV_X_SIZE) / 2;
            int guiTop = (gui.height - INV_Y_SIZE) / 2;
            return new int[] { guiLeft, guiTop, SU_FALLBACK_Y_SURVIVAL };
        }
        if (gui instanceof GuiContainerCreative) {
            int guiLeft = (gui.width - CREATIVE_X_SIZE) / 2;
            int guiTop = (gui.height - CREATIVE_Y_SIZE) / 2;
            return new int[] { guiLeft, guiTop, SU_FALLBACK_Y_CREATIVE };
        }
        return null;
    }

    /**
     * Returns the value of {@code serverutils.client.gui.GuiSidebar.lastDrawnArea}
     * via reflection, or {@code null} if SU is not installed or the field is inaccessible.
     * The {@code Field} object is cached after the first lookup so reflection overhead
     * is paid only once per JVM session.
     */
    private static Rectangle getSuLastDrawnArea() {
        if (!suFieldLookupDone) {
            suFieldLookupDone = true;
            try {
                Class<?> cls = Class.forName("serverutils.client.gui.GuiSidebar");
                suLastDrawnAreaField = cls.getField("lastDrawnArea");
            } catch (Throwable ignored) {
                // SU is not on the class-path; suLastDrawnAreaField stays null.
            }
        }
        if (suLastDrawnAreaField == null) return null;
        try {
            return (Rectangle) suLastDrawnAreaField.get(null);
        } catch (Throwable ignored) {
            return null;
        }
    }
}
