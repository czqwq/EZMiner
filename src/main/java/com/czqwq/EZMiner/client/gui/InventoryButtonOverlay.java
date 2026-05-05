package com.czqwq.EZMiner.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.inventory.GuiInventory;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.common.MinecraftForge;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Injects a small EZMiner config button into the vanilla inventory screen.
 *
 * <p>
 * The button is added to the left side of the inventory container and renders
 * the {@code textures/icons/settings.png} icon. Clicking it opens
 * {@link EZMinerConfigGui}.
 *
 * <p>
 * Uses Forge's {@link GuiScreenEvent} so no Mixin or reflection is required.
 */
@SideOnly(Side.CLIENT)
public class InventoryButtonOverlay {

    /** Unique button ID – chosen high enough not to clash with any vanilla inventory buttons. */
    private static final int BTN_ID = 1000;

    /**
     * Width and height of the injected button in screen pixels.
     * Keep it small so it fits beside the inventory panel without overlapping.
     */
    private static final int BTN_SIZE = 20;

    /**
     * Horizontal distance from the left edge of the inventory panel to place the button.
     * Positive values shift the button further left (away from the panel).
     */
    private static final int BTN_OFFSET_LEFT = 24;

    /**
     * Vertical offset from the top of the inventory panel.
     * Set to 26 px so our button does not overlap the button that
     * ServerUtilities (and similar mods) injects at y+4.
     */
    private static final int BTN_Y_OFFSET = 26;

    /** Vanilla inventory panel dimensions (constant for GuiInventory in 1.7.10). */
    private static final int INV_X_SIZE = 176;
    private static final int INV_Y_SIZE = 166;

    /** Settings icon texture (16 × 16 placeholder). */
    private static final ResourceLocation SETTINGS_TEXTURE = new ResourceLocation(
        "ezminer",
        "textures/icons/settings.png");

    // ── Event handlers ────────────────────────────────────────────────────────

    /**
     * After the inventory GUI is initialised, inject the EZMiner config button
     * to the left of the inventory panel.
     */
    @SubscribeEvent
    @SuppressWarnings("unchecked")
    public void onInitGui(GuiScreenEvent.InitGuiEvent.Post event) {
        if (!(event.gui instanceof GuiInventory)) return;
        int guiLeft = (event.gui.width - INV_X_SIZE) / 2;
        int guiTop = (event.gui.height - INV_Y_SIZE) / 2;
        int btnX = guiLeft - BTN_OFFSET_LEFT - BTN_SIZE / 2;
        int btnY = guiTop + BTN_Y_OFFSET;
        event.buttonList.add(new TexturedButton(BTN_ID, btnX, btnY, BTN_SIZE, BTN_SIZE, SETTINGS_TEXTURE));
    }

    /**
     * When the injected button is clicked, open the EZMiner config GUI.
     */
    @SubscribeEvent
    public void onActionPerformed(GuiScreenEvent.ActionPerformedEvent.Post event) {
        if (!(event.gui instanceof GuiInventory)) return;
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
}
