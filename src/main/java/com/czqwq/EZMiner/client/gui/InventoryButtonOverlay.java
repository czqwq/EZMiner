package com.czqwq.EZMiner.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.inventory.GuiContainerCreative;
import net.minecraft.client.gui.inventory.GuiInventory;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.common.MinecraftForge;

import com.czqwq.EZMiner.EZMiner;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Injects a small EZMiner config button into both the vanilla survival inventory
 * screen and the creative inventory screen.
 *
 * <p>
 * The button is placed in the same x-column as ServerUtilities' sidebar buttons
 * ({@code guiLeft - 18}) and positioned one slot (17 px) below SU's first button
 * so the two never overlap.
 *
 * <p>
 * Uses Forge's {@link GuiScreenEvent} so no Mixin or reflection is required.
 */
@SideOnly(Side.CLIENT)
public class InventoryButtonOverlay {

    /** Unique button ID – chosen high enough not to clash with any vanilla inventory buttons. */
    private static final int BTN_ID = 1000;

    /**
     * Button size in screen pixels.
     * Matches the 16 × 16 px size used by ServerUtilities sidebar buttons.
     */
    private static final int BTN_SIZE = 16;

    /**
     * Horizontal offset from guiLeft at which ServerUtilities places its sidebar buttons.
     * From {@code GuiSidebar.setButtonLocations}: {@code button.x = gui.guiLeft - offsetX}.
     */
    private static final int SU_OFFSET_X = 18;

    /**
     * Y offset used by ServerUtilities for the first sidebar button in the survival inventory.
     * From {@code GuiSidebar.setButtonLocations}: {@code offsetY = 8}.
     */
    private static final int SU_OFFSET_Y_SURVIVAL = 8;

    /**
     * Y offset used by ServerUtilities for the first sidebar button in the creative inventory.
     * From {@code GuiSidebar.setButtonLocations}: {@code offsetY = 6} when {@code instanceof GuiContainerCreative}.
     */
    private static final int SU_OFFSET_Y_CREATIVE = 6;

    /**
     * Height of one button "slot" in SU's sidebar (button height 16 + 1 px gap = 17).
     * Our button is placed one slot below SU's first button.
     */
    private static final int SU_BTN_SLOT = 17;

    /** Vanilla survival inventory panel size (GuiInventory). */
    private static final int INV_X_SIZE = 176;
    private static final int INV_Y_SIZE = 166;

    /** Vanilla creative inventory panel size (GuiContainerCreative). */
    private static final int CREATIVE_X_SIZE = 195;
    private static final int CREATIVE_Y_SIZE = 136;

    /**
     * Settings icon texture.
     * Domain MUST match the assets directory name ({@code assets/EZMiner/}) exactly.
     */
    private static final ResourceLocation SETTINGS_TEXTURE = new ResourceLocation(
        EZMiner.MODID,
        "textures/icons/settings.png");

    // ── Event handlers ────────────────────────────────────────────────────────

    /**
     * After the inventory GUI is initialised, inject the EZMiner config button
     * to the left of the inventory panel, one slot below SU's first button.
     */
    @SubscribeEvent
    @SuppressWarnings("unchecked")
    public void onInitGui(GuiScreenEvent.InitGuiEvent.Post event) {
        int[] offsets = guiOffsets(event.gui);
        if (offsets == null) return;
        // offsets: [guiLeft, guiTop, suOffsetY]
        int btnX = offsets[0] - SU_OFFSET_X;
        int btnY = offsets[1] + offsets[2] + SU_BTN_SLOT; // one slot below SU's first button
        event.buttonList.add(new TexturedButton(BTN_ID, btnX, btnY, BTN_SIZE, BTN_SIZE, SETTINGS_TEXTURE));
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
     * Returns {@code [guiLeft, guiTop, suOffsetY]} for a supported inventory GUI,
     * or {@code null} if the GUI is not a recognised inventory screen.
     */
    private static int[] guiOffsets(net.minecraft.client.gui.GuiScreen gui) {
        if (gui instanceof GuiInventory) {
            int guiLeft = (gui.width - INV_X_SIZE) / 2;
            int guiTop = (gui.height - INV_Y_SIZE) / 2;
            return new int[] { guiLeft, guiTop, SU_OFFSET_Y_SURVIVAL };
        }
        if (gui instanceof GuiContainerCreative) {
            int guiLeft = (gui.width - CREATIVE_X_SIZE) / 2;
            int guiTop = (gui.height - CREATIVE_Y_SIZE) / 2;
            return new int[] { guiLeft, guiTop, SU_OFFSET_Y_CREATIVE };
        }
        return null;
    }
}
