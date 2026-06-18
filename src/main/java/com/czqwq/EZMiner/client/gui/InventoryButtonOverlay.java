package com.czqwq.EZMiner.client.gui;

import java.awt.Rectangle;
import java.lang.reflect.Field;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.inventory.GuiContainerCreative;
import net.minecraft.client.gui.inventory.GuiInventory;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.resources.I18n;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.common.MinecraftForge;

import org.lwjgl.opengl.GL11;

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
 * SU's entire sidebar block (by finding the {@code GuiSidebar} button instance in
 * the screen's button list and reading its real on-screen bounds) so the layout
 * stays correct regardless of how many SU buttons are enabled or whether the
 * sidebar has been dragged. When SU is absent the
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
     */
    private static final int SU_FIRST_BTN_Y_SURVIVAL = 8;

    /**
     * Y offset of SU's first sidebar button in the creative inventory
     * (from {@code GuiSidebar.setButtonLocations}: {@code offsetY = 6} for GuiContainerCreative).
     */
    private static final int SU_FIRST_BTN_Y_CREATIVE = 6;

    /** Vanilla survival inventory panel size (GuiInventory). */
    private static final int INV_X_SIZE = 176;
    private static final int INV_Y_SIZE = 166;

    /** Vanilla creative inventory panel size (GuiContainerCreative). */
    private static final int CREATIVE_X_SIZE = 195;
    private static final int CREATIVE_Y_SIZE = 136;

    /**
     * Bottom margin (px) from the inventory panel bottom for the fallback
     * button position when SU is absent or its sidebar bounds cannot be read.
     */
    private static final int FALLBACK_BOTTOM_MARGIN = 4;

    // ── SRG field names for production (reobfuscated) environments ─────────
    // In a deobfuscated dev environment Minecraft fields use MCP names;
    // in production they use SRG names. We try MCP first, then SRG.

    private static final String SRG_BUTTON_LIST = "field_146292_n"; // GuiScreen.buttonList
    private static final String SRG_X_POS = "field_146128_h"; // GuiButton.xPosition
    private static final String SRG_Y_POS = "field_146129_i"; // GuiButton.yPosition
    private static final String SRG_WIDTH = "field_146120_f"; // GuiButton.width
    private static final String SRG_HEIGHT = "field_146121_g"; // GuiButton.height

    /**
     * Settings icon texture.
     * Domain must be all-lowercase because MC 1.7.10's {@code ResourceLocation}
     * lowercases the domain, matching the {@code assets/ezminer/} folder.
     */
    private static final ResourceLocation SETTINGS_TEXTURE = new ResourceLocation(
        "ezminer",
        "textures/icons/settings.png");

    // ── Reflection cache for GuiSidebar bounds ──────────────────────────────

    /** {@code true} once the one-time class / field lookup has been attempted. */
    private static boolean suLookupDone = false;

    /** Cached {@code serverutils.client.gui.GuiSidebar} class, or null if SU absent. */
    private static Class<?> suSidebarClass = null;

    /** Cached {@code GuiScreen.buttonList} field, set during first successful lookup. */
    private static Field suButtonListField;

    /** Cached {@code GuiButton} field accessors, set during first successful lookup. */
    private static Field suXField, suYField, suWField, suHField;

    /**
     * Gets a public field by MCP name first, falling back to the SRG name
     * for production (reobfuscated) environments.
     */
    private static Field getFieldMcpSrg(Class<?> clazz, String mcp, String srg) {
        try {
            Field f = clazz.getField(mcp);
            f.setAccessible(true);
            return f;
        } catch (NoSuchFieldException e) {
            try {
                Field f = clazz.getField(srg);
                f.setAccessible(true);
                return f;
            } catch (NoSuchFieldException e2) {
                return null;
            }
        }
    }

    /**
     * Gets a declared field by MCP name first, falling back to the SRG name
     * for production (reobfuscated) environments.
     */
    private static Field getDeclaredFieldMcpSrg(Class<?> clazz, String mcp, String srg) {
        try {
            Field f = clazz.getDeclaredField(mcp);
            f.setAccessible(true);
            return f;
        } catch (NoSuchFieldException e) {
            try {
                Field f = clazz.getDeclaredField(srg);
                f.setAccessible(true);
                return f;
            } catch (NoSuchFieldException e2) {
                return null;
            }
        }
    }

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
        // Place at the bottom of the inventory panel as a safe default;
        // the real position is refined in onDrawScreenPre.
        int btnX = offsets[0] - SU_OFFSET_X;
        int btnY = offsets[1] + offsets[2] - BTN_SIZE - FALLBACK_BOTTOM_MARGIN;
        ourButton = new TexturedButton(BTN_ID, btnX, btnY, BTN_SIZE, BTN_SIZE, SETTINGS_TEXTURE);
        event.buttonList.add(ourButton);
    }

    /**
     * Before each frame is drawn, reposition the button to sit directly below
     * SU's sidebar block (by reading the {@code GuiSidebar} button's actual
     * on-screen bounds) so the two never overlap regardless of how many SU
     * buttons are visible or whether the sidebar has been dragged.
     */
    @SubscribeEvent
    public void onDrawScreenPre(GuiScreenEvent.DrawScreenEvent.Pre event) {
        if (ourButton == null) return;
        int[] offsets = guiOffsets(event.gui);
        if (offsets == null) return;

        Rectangle suArea = getSuArea(event.gui);
        int btnX;
        int btnY;
        if (suArea != null && suArea.width > 0 && suArea.height > 0) {
            // Align with SU's drawn area: same left edge, just below the bottom.
            btnX = suArea.x + 2; // +2 skips SU's 2-px bounding-box padding
            btnY = suArea.y + suArea.height; // bottom of SU area (includes 2-px padding)
        } else {
            // SU absent or has no visible buttons: fall back to the bottom of the
            // inventory panel so the button never overlaps with SU buttons.
            btnX = offsets[0] - SU_OFFSET_X;
            btnY = offsets[1] + offsets[2] - BTN_SIZE - FALLBACK_BOTTOM_MARGIN;
        }
        ourButton.xPosition = btnX;
        ourButton.yPosition = btnY;
    }

    /**
     * After each frame is drawn, render a tooltip over our button when the mouse is hovering
     * on it. Drawing is deferred to Post so the tooltip always renders on top of everything else.
     * The tooltip is drawn manually with GL11 to avoid accessing the protected
     * {@code GuiScreen.drawHoveringText} method.
     */
    @SubscribeEvent
    public void onDrawScreenPost(GuiScreenEvent.DrawScreenEvent.Post event) {
        if (ourButton == null || !ourButton.visible) return;
        if (guiOffsets(event.gui) == null) return;
        if (event.mouseX >= ourButton.xPosition && event.mouseY >= ourButton.yPosition
            && event.mouseX < ourButton.xPosition + BTN_SIZE
            && event.mouseY < ourButton.yPosition + BTN_SIZE) {
            drawTooltip(
                Minecraft.getMinecraft().fontRenderer,
                I18n.format("ezminer.gui.inventory_button"),
                event.mouseX,
                event.mouseY,
                event.gui.width);
        }
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
     * Returns {@code [guiLeft, guiTop, invHeight]} for a supported inventory GUI,
     * or {@code null} if the GUI is not a recognised inventory screen.
     * The invHeight is used for computing the fallback button position at the
     * bottom of the inventory panel.
     */
    private static int[] guiOffsets(net.minecraft.client.gui.GuiScreen gui) {
        if (gui instanceof GuiInventory) {
            int guiLeft = (gui.width - INV_X_SIZE) / 2;
            int guiTop = (gui.height - INV_Y_SIZE) / 2;
            return new int[] { guiLeft, guiTop, INV_Y_SIZE };
        }
        if (gui instanceof GuiContainerCreative) {
            int guiLeft = (gui.width - CREATIVE_X_SIZE) / 2;
            int guiTop = (gui.height - CREATIVE_Y_SIZE) / 2;
            return new int[] { guiLeft, guiTop, CREATIVE_Y_SIZE };
        }
        return null;
    }

    /**
     * Draws a MC-style tooltip box at the given screen position.
     * Rendered manually with GL11/Tessellator to avoid accessing the protected
     * {@code GuiScreen.drawHoveringText}. Supports Minecraft {@code §} colour codes in
     * {@code text} via {@link FontRenderer#drawStringWithShadow}.
     */
    private static void drawTooltip(FontRenderer font, String text, int mx, int my, int screenW) {
        // Strip colour codes for width measurement so the box fits the visible text.
        int w = font.getStringWidth(net.minecraft.util.EnumChatFormatting.getTextWithoutFormattingCodes(text));
        int h = font.FONT_HEIGHT;
        int pad = 3;

        int tx = mx + 10;
        int ty = my - h - pad * 2 - 2;
        if (tx + w + pad * 2 > screenW) tx = screenW - w - pad * 2;
        if (ty < 2) ty = my + 10;

        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glDisable(GL11.GL_TEXTURE_2D);

        // Dark background: 0xF0100010
        GL11.glColor4f(16 / 255f, 0f, 16 / 255f, 240 / 255f);
        fillRect(tx - pad, ty - pad, tx + w + pad, ty + h + pad);

        // Purple border: 0xFF500050
        GL11.glColor4f(80 / 255f, 0f, 80 / 255f, 1f);
        fillRect(tx - pad, ty - pad, tx + w + pad, ty - pad + 1);
        fillRect(tx - pad, ty + h + pad - 1, tx + w + pad, ty + h + pad);
        fillRect(tx - pad, ty - pad + 1, tx - pad + 1, ty + h + pad - 1);
        fillRect(tx + w + pad - 1, ty - pad + 1, tx + w + pad, ty + h + pad - 1);

        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glColor4f(1f, 1f, 1f, 1f);
        font.drawStringWithShadow(text, tx, ty, 0xFFFFFF);

        GL11.glEnable(GL11.GL_DEPTH_TEST);
        GL11.glDisable(GL11.GL_BLEND);
        GL11.glColor4f(1f, 1f, 1f, 1f);
    }

    /** Draws a filled axis-aligned rectangle using the current GL colour. */
    private static void fillRect(int x1, int y1, int x2, int y2) {
        Tessellator t = Tessellator.instance;
        t.startDrawingQuads();
        t.addVertex(x1, y2, 0);
        t.addVertex(x2, y2, 0);
        t.addVertex(x2, y1, 0);
        t.addVertex(x1, y1, 0);
        t.draw();
    }

    /**
     * Finds the {@code serverutils.client.gui.GuiSidebar} button instance in the
     * current screen's button list and returns its bounds as a Rectangle.
     * {@code GuiSidebar} extends {@code GuiButton} and updates its own
     * {@code xPosition / yPosition / width / height} in {@code drawButton()}
     * to the bounding box of all visible sidebar buttons (with 2 px padding).
     *
     * <p>
     * Returns {@code null} when SU is not installed, the sidebar is not in the
     * button list, or its bounds are still zero (before the first draw call).
     */
    private static Rectangle getSuArea(GuiScreen gui) {
        if (!suLookupDone) {
            suLookupDone = true;
            try {
                suSidebarClass = Class.forName("serverutils.client.gui.GuiSidebar");
                suButtonListField = getDeclaredFieldMcpSrg(GuiScreen.class, "buttonList", SRG_BUTTON_LIST);
                suXField = getFieldMcpSrg(GuiButton.class, "xPosition", SRG_X_POS);
                suYField = getFieldMcpSrg(GuiButton.class, "yPosition", SRG_Y_POS);
                suWField = getFieldMcpSrg(GuiButton.class, "width", SRG_WIDTH);
                suHField = getFieldMcpSrg(GuiButton.class, "height", SRG_HEIGHT);
            } catch (Throwable ignored) {
                // SU not on the class-path; all fields stay null, subsequent calls are no-ops.
            }
        }
        if (suSidebarClass == null) return null;
        try {
            @SuppressWarnings("unchecked")
            List<GuiButton> buttonList = (List<GuiButton>) suButtonListField.get(gui);
            for (GuiButton btn : buttonList) {
                if (suSidebarClass.isInstance(btn)) {
                    int w = suWField.getInt(btn);
                    int h = suHField.getInt(btn);
                    // Ignore the sidebar before its first drawButton() call (bounds are 0).
                    if (w <= 0 || h <= 0) return null;
                    int x = suXField.getInt(btn);
                    int y = suYField.getInt(btn);
                    // Reject bogus bounds from integer overflow when all SU sidebar
                    // buttons are hidden (GuiSidebar's bounding-box computation
                    // produces x/y near Integer.MAX_VALUE in that case).
                    if (x < 0 || y < 0 || x >= gui.width || y >= gui.height) return null;
                    return new Rectangle(x, y, w, h);
                }
            }
        } catch (Throwable ignored) {}
        return null;
    }
}
