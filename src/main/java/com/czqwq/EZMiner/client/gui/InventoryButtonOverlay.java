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
 * Injects an EZMiner config button into the vanilla inventory screen.
 * Dynamically positions below ServerUtilities sidebar when SU is present,
 * falls back to a fixed position otherwise. Uses Forge events — no Mixin.
 */
@SideOnly(Side.CLIENT)
public class InventoryButtonOverlay {

    private static final int BTN_ID = 1000;
    private static final int BTN_SIZE = 16;
    /** SU sidebar column X offset from guiLeft (GuiSidebar.setButtonLocations). */
    private static final int SU_OFFSET_X = 18;
    private static final int SU_FIRST_BTN_Y_SURVIVAL = 8;
    private static final int SU_FIRST_BTN_Y_CREATIVE = 6;
    private static final int INV_X_SIZE = 176;
    private static final int INV_Y_SIZE = 166;
    private static final int CREATIVE_X_SIZE = 195;
    private static final int CREATIVE_Y_SIZE = 136;
    private static final int FALLBACK_BOTTOM_MARGIN = 4;

    // SRG names for production (reobfuscated) environments. Try MCP first, then SRG.

    private static final String SRG_BUTTON_LIST = "field_146292_n"; // GuiScreen.buttonList
    private static final String SRG_X_POS = "field_146128_h"; // GuiButton.xPosition
    private static final String SRG_Y_POS = "field_146129_i"; // GuiButton.yPosition
    private static final String SRG_WIDTH = "field_146120_f"; // GuiButton.width
    private static final String SRG_HEIGHT = "field_146121_g"; // GuiButton.height

    /** MC 1.7.10 lowercases ResourceLocation domain — must match assets/ezminer/. */
    private static final ResourceLocation SETTINGS_TEXTURE = new ResourceLocation(
        "ezminer",
        "textures/icons/settings.png");

    // ── Reflection cache for GuiSidebar bounds ──────────────────────────────

    private static boolean suLookupDone = false;
    private static Class<?> suSidebarClass = null;
    private static Field suButtonListField;
    private static Field suXField, suYField, suWField, suHField;

    /** Try MCP field name first, fall back to SRG for production. */
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

    /** Try MCP declared field first, fall back to SRG for production. */
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

    private TexturedButton ourButton = null;

    // ── Event handlers ────────────────────────────────────────────────────────

    /** Inject config button after GUI init. Position refined each frame in onDrawScreenPre. */
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

    /** Reposition button below SU sidebar block each frame. */
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

    /** Render tooltip on hover (manually via GL11 to avoid protected drawHoveringText). */
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

    /** Open EZMiner config GUI on button click. */
    @SubscribeEvent
    public void onActionPerformed(GuiScreenEvent.ActionPerformedEvent.Post event) {
        if (guiOffsets(event.gui) == null) return;
        if (event.button.id != BTN_ID) return;
        Minecraft.getMinecraft()
            .displayGuiScreen(new EZMinerConfigGui());
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    public void registry() {
        MinecraftForge.EVENT_BUS.register(this);
        FMLCommonHandler.instance()
            .bus()
            .register(this);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** Returns [guiLeft, guiTop, invHeight] for supported inventory GUIs, or null. */
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

    /** MC-style tooltip box rendered via GL11 (avoids protected drawHoveringText). */
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

    private static void fillRect(int x1, int y1, int x2, int y2) {
        Tessellator t = Tessellator.instance;
        t.startDrawingQuads();
        t.addVertex(x1, y2, 0);
        t.addVertex(x2, y2, 0);
        t.addVertex(x2, y1, 0);
        t.addVertex(x1, y1, 0);
        t.draw();
    }

    /** Find GuiSidebar button bounds via reflection. Returns null if SU absent or not yet drawn. */
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
