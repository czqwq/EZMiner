package com.czqwq.EZMiner.client.gui;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.resources.I18n;

import org.lwjgl.input.Keyboard;

import com.czqwq.EZMiner.Config;
import com.czqwq.EZMiner.EZMiner;
import com.czqwq.EZMiner.network.PacketMinerConfig;
import com.czqwq.EZMiner.network.PacketReloadServerConfig;
import com.czqwq.EZMiner.network.PacketSaveServerConfig;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * In-game configuration GUI for EZMiner.
 *
 * <ul>
 * <li><strong>Client Settings</strong> tab – edits the local client config file.
 * <em>Reload</em> re-fetches server runtime limits and reloads client config from disk.
 * <em>Save &amp; Apply</em> writes in-memory edits to disk and syncs to server.</li>
 * <li><strong>Server Settings</strong> tab – visible only to OP players. Edits are
 * sent to the server via {@link PacketSaveServerConfig}.
 * <em>Reload</em> makes the server re-read its config file.</li>
 * </ul>
 */
@SideOnly(Side.CLIENT)
public class EZMinerConfigGui extends GuiScreen {

    // ── Tab IDs ──────────────────────────────────────────────────────────────
    private static final int TAB_CLIENT = 0;
    private static final int TAB_SERVER = 1;

    // ── Button IDs ───────────────────────────────────────────────────────────
    private static final int BTN_TAB_CLIENT = 0;
    private static final int BTN_TAB_SERVER = 1;
    private static final int BTN_USE_PREVIEW = 2;
    private static final int BTN_USE_CHAIN_DONE_MSG = 3;
    private static final int BTN_CHAIN_ACTIVATION_MODE = 4;
    private static final int BTN_SUPPRESS_INGAME_INFO = 5;
    private static final int BTN_CLIENT_RELOAD = 6;
    private static final int BTN_CLIENT_SAVE = 7;
    private static final int BTN_SERVER_DROP_TO_PLAYER = 8;
    private static final int BTN_SERVER_USE_PREVIEW = 9;
    private static final int BTN_SERVER_RELOAD = 10;
    private static final int BTN_SERVER_SAVE = 11;
    private static final int BTN_CLOSE = 12;
    private static final int BTN_HUD_ANIM_STYLE = 13;
    private static final int BTN_RENDER_STYLE = 14;

    // ── Layout constants ──────────────────────────────────────────────────────
    private static final int GUI_W = 256;
    /** Maximum GUI height; actual height is capped to the available screen height. */
    private static final int MAX_GUI_H = 280;
    /** X offset of the label column relative to guiLeft. */
    private static final int LABEL_X = 8;
    /** X offset of the value text-field column relative to guiLeft. */
    private static final int FIELD_X = 148;
    /** Width of each text field. */
    private static final int FIELD_W = 76;
    /**
     * Height of each text field / toggle button.
     * Kept at 12 px so it always fits within the minimum adaptive row height.
     */
    private static final int FIELD_H = 12;
    /** Y position of the first content row relative to guiTop. */
    private static final int CONTENT_START_Y = 38;
    /**
     * Number of content rows that must fit between {@link #CONTENT_START_Y} and the
     * action-button strip. The server tab has the most rows (9 fields + 2 toggles = 11).
     */
    private static final int MAX_CONTENT_ROWS = 12;
    /** Minimum row height in pixels (must be >= FIELD_H + 1). */
    private static final int MIN_ROW_H = 13;
    /** Height of the action-button strip at the bottom of the panel (px). */
    private static final int ACTION_STRIP_H = 44;

    // ── Adaptive layout (computed per initGui call) ───────────────────────────
    /** Actual panel height for the current screen. */
    private int guiH;
    /** Pixels between content rows for the current screen. */
    private int rowH;
    /** Y offset (relative to guiTop) of the bottom action-button row. */
    private int actionBtnY;

    // ── State ────────────────────────────────────────────────────────────────
    private int activeTab = TAB_CLIENT;
    private int guiLeft;
    private int guiTop;

    // ── Client tab text fields ────────────────────────────────────────────────
    private GuiTextField tfClientBigRadius;
    private GuiTextField tfClientBlockLimit;
    private GuiTextField tfClientSmallRadius;
    private GuiTextField tfClientTunnelWidth;
    private GuiTextField tfPreviewBigRadius;
    private GuiTextField tfPreviewBlockLimit;

    // ── Server tab text fields ────────────────────────────────────────────────
    private GuiTextField tfServerBigRadius;
    private GuiTextField tfServerBlockLimit;
    private GuiTextField tfServerSmallRadius;
    private GuiTextField tfServerTunnelWidth;
    private GuiTextField tfBreakPerTick;
    private GuiTextField tfAddExhaustion;
    private GuiTextField tfMinesweeperCooldown;
    private GuiTextField tfServerMaxPreviewRadius;
    private GuiTextField tfServerMaxPreviewLimit;

    // ── Toggle button references (for updating display text on click) ─────────
    private GuiButton btnUsePreview;
    private GuiButton btnUseChainDoneMsg;
    private GuiButton btnChainActivationMode;
    private GuiButton btnSuppressIngameInfo;
    private GuiButton btnHudAnimStyle;
    private GuiButton btnRenderStyle;
    private GuiButton btnServerDropToPlayer;
    private GuiButton btnServerUsePreview;

    // ── GuiScreen overrides ───────────────────────────────────────────────────

    @Override
    @SuppressWarnings("unchecked")
    public void initGui() {
        // ── Adaptive layout ───────────────────────────────────────────────────
        guiH = Math.min(MAX_GUI_H, height - 10);
        rowH = Math.max(MIN_ROW_H, (guiH - CONTENT_START_Y - ACTION_STRIP_H) / MAX_CONTENT_ROWS);
        actionBtnY = guiH - 26;
        guiLeft = (width - GUI_W) / 2;
        guiTop = (height - guiH) / 2;
        buttonList.clear();

        // Tab selector buttons
        buttonList.add(
            new GuiButton(BTN_TAB_CLIENT, guiLeft + 4, guiTop + 18, 116, 16, I18n.format("ezminer.gui.tab.client")));
        if (EZMiner.clientIsOp) {
            buttonList.add(
                new GuiButton(
                    BTN_TAB_SERVER,
                    guiLeft + 124,
                    guiTop + 18,
                    116,
                    16,
                    I18n.format("ezminer.gui.tab.server")));
        }

        // Close button (top-right)
        buttonList.add(new GuiButton(BTN_CLOSE, guiLeft + GUI_W - 20, guiTop + 4, 16, 12, "X"));

        // ── Client tab toggles ────────────────────────────────────────────────
        int bx = guiLeft + LABEL_X;
        int bw = GUI_W - 2 * LABEL_X;
        int y = clientRowY(6);
        btnUsePreview = new GuiButton(
            BTN_USE_PREVIEW,
            bx,
            y,
            bw,
            FIELD_H,
            boolLabel("ezminer.config.usePreview", Config.usePreview));
        buttonList.add(btnUsePreview);

        btnUseChainDoneMsg = new GuiButton(
            BTN_USE_CHAIN_DONE_MSG,
            bx,
            clientRowY(7),
            bw,
            FIELD_H,
            boolLabel("ezminer.config.useChainDoneMessage", Config.useChainDoneMessage));
        buttonList.add(btnUseChainDoneMsg);

        btnChainActivationMode = new GuiButton(
            BTN_CHAIN_ACTIVATION_MODE,
            bx,
            clientRowY(8),
            bw,
            FIELD_H,
            activationModeLabel());
        buttonList.add(btnChainActivationMode);

        btnSuppressIngameInfo = new GuiButton(
            BTN_SUPPRESS_INGAME_INFO,
            bx,
            clientRowY(9),
            bw,
            FIELD_H,
            boolLabel("ezminer.config.suppressIngameInfoHud", Config.suppressIngameInfoHud));
        buttonList.add(btnSuppressIngameInfo);

        btnHudAnimStyle = new GuiButton(BTN_HUD_ANIM_STYLE, bx, clientRowY(10), bw, FIELD_H, hudAnimStyleLabel());
        buttonList.add(btnHudAnimStyle);

        btnRenderStyle = new GuiButton(BTN_RENDER_STYLE, bx, clientRowY(11), bw, FIELD_H, renderStyleLabel());
        buttonList.add(btnRenderStyle);

        // Client bottom buttons
        buttonList.add(
            new GuiButton(
                BTN_CLIENT_RELOAD,
                guiLeft + 4,
                guiTop + actionBtnY,
                120,
                18,
                I18n.format("ezminer.gui.apply")));
        buttonList.add(
            new GuiButton(
                BTN_CLIENT_SAVE,
                guiLeft + 128,
                guiTop + actionBtnY,
                120,
                18,
                I18n.format("ezminer.gui.saveAndExit")));

        // ── Server tab toggles ────────────────────────────────────────────────
        if (EZMiner.clientIsOp) {
            btnServerDropToPlayer = new GuiButton(
                BTN_SERVER_DROP_TO_PLAYER,
                bx,
                serverRowY(9),
                bw,
                FIELD_H,
                boolLabel("ezminer.config.dropToPlayer", Config.dropToPlayer));
            buttonList.add(btnServerDropToPlayer);

            btnServerUsePreview = new GuiButton(
                BTN_SERVER_USE_PREVIEW,
                bx,
                serverRowY(10),
                bw,
                FIELD_H,
                boolLabel("ezminer.config.serverUsePreview", Config.serverUsePreview));
            buttonList.add(btnServerUsePreview);

            buttonList.add(
                new GuiButton(
                    BTN_SERVER_RELOAD,
                    guiLeft + 4,
                    guiTop + actionBtnY,
                    120,
                    18,
                    I18n.format("ezminer.gui.reload.server")));
            buttonList.add(
                new GuiButton(
                    BTN_SERVER_SAVE,
                    guiLeft + 128,
                    guiTop + actionBtnY,
                    120,
                    18,
                    I18n.format("ezminer.gui.save")));
        }

        // ── Text fields ───────────────────────────────────────────────────────
        initClientFields();
        if (EZMiner.clientIsOp) {
            initServerFields();
        }

        updateTabVisibility();
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        // Panel background
        drawRect(guiLeft, guiTop, guiLeft + GUI_W, guiTop + guiH, 0xCC000000);
        drawRect(guiLeft + 1, guiTop + 1, guiLeft + GUI_W - 1, guiTop + guiH - 1, 0xFF1A1A2E);
        // Title
        drawCenteredString(
            mc.fontRenderer,
            "§b[EZMiner] §f" + I18n.format("ezminer.gui.title"),
            guiLeft + GUI_W / 2,
            guiTop + 6,
            0xFFFFFF);
        // Horizontal dividers
        drawRect(guiLeft + 2, guiTop + 36, guiLeft + GUI_W - 2, guiTop + 37, 0xFF4444AA);
        drawRect(guiLeft + 2, guiTop + actionBtnY - 4, guiLeft + GUI_W - 2, guiTop + actionBtnY - 3, 0xFF444466);

        if (activeTab == TAB_CLIENT) {
            drawClientTab();
        } else {
            drawServerTab();
        }

        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        switch (button.id) {
            case BTN_TAB_CLIENT:
                activeTab = TAB_CLIENT;
                updateTabVisibility();
                break;
            case BTN_TAB_SERVER:
                activeTab = TAB_SERVER;
                updateTabVisibility();
                break;
            case BTN_CLOSE:
                mc.displayGuiScreen(null);
                break;

            // ── Client toggles ────────────────────────────────────────────────
            case BTN_USE_PREVIEW:
                Config.usePreview = !Config.usePreview;
                btnUsePreview.displayString = boolLabel("ezminer.config.usePreview", Config.usePreview);
                break;
            case BTN_USE_CHAIN_DONE_MSG:
                Config.useChainDoneMessage = !Config.useChainDoneMessage;
                btnUseChainDoneMsg.displayString = boolLabel(
                    "ezminer.config.useChainDoneMessage",
                    Config.useChainDoneMessage);
                break;
            case BTN_CHAIN_ACTIVATION_MODE:
                Config.chainActivationMode = 1 - Config.chainActivationMode;
                btnChainActivationMode.displayString = activationModeLabel();
                break;
            case BTN_SUPPRESS_INGAME_INFO:
                Config.suppressIngameInfoHud = !Config.suppressIngameInfoHud;
                btnSuppressIngameInfo.displayString = boolLabel(
                    "ezminer.config.suppressIngameInfoHud",
                    Config.suppressIngameInfoHud);
                break;
            case BTN_HUD_ANIM_STYLE:
                Config.hudAnimationStyle = 1 - Config.hudAnimationStyle;
                btnHudAnimStyle.displayString = hudAnimStyleLabel();
                break;
            case BTN_RENDER_STYLE:
                Config.renderStyle = 1 - Config.renderStyle;
                btnRenderStyle.displayString = renderStyleLabel();
                break;

            // ── Client actions ────────────────────────────────────────────────
            case BTN_CLIENT_RELOAD:
                // "Apply" – saves current field values to disk and syncs to server, stays open.
                applyAndSaveClientConfig();
                break;
            case BTN_CLIENT_SAVE:
                // "Save & Exit" – saves then closes the GUI.
                applyAndSaveClientConfig();
                mc.displayGuiScreen(null);
                break;

            // ── Server toggles ────────────────────────────────────────────────
            case BTN_SERVER_DROP_TO_PLAYER:
                Config.dropToPlayer = !Config.dropToPlayer;
                btnServerDropToPlayer.displayString = boolLabel("ezminer.config.dropToPlayer", Config.dropToPlayer);
                break;
            case BTN_SERVER_USE_PREVIEW:
                Config.serverUsePreview = !Config.serverUsePreview;
                btnServerUsePreview.displayString = boolLabel(
                    "ezminer.config.serverUsePreview",
                    Config.serverUsePreview);
                break;

            // ── Server actions ────────────────────────────────────────────────
            case BTN_SERVER_RELOAD:
                EZMiner.network.network.sendToServer(new PacketReloadServerConfig());
                break;
            case BTN_SERVER_SAVE:
                applyAndSaveServerConfig();
                break;

            default:
                break;
        }
    }

    @Override
    protected void mouseClicked(int x, int y, int mouseButton) {
        super.mouseClicked(x, y, mouseButton);
        if (activeTab == TAB_CLIENT) {
            tfClientBigRadius.mouseClicked(x, y, mouseButton);
            tfClientBlockLimit.mouseClicked(x, y, mouseButton);
            tfClientSmallRadius.mouseClicked(x, y, mouseButton);
            tfClientTunnelWidth.mouseClicked(x, y, mouseButton);
            tfPreviewBigRadius.mouseClicked(x, y, mouseButton);
            tfPreviewBlockLimit.mouseClicked(x, y, mouseButton);
        } else if (activeTab == TAB_SERVER && EZMiner.clientIsOp) {
            tfServerBigRadius.mouseClicked(x, y, mouseButton);
            tfServerBlockLimit.mouseClicked(x, y, mouseButton);
            tfServerSmallRadius.mouseClicked(x, y, mouseButton);
            tfServerTunnelWidth.mouseClicked(x, y, mouseButton);
            tfBreakPerTick.mouseClicked(x, y, mouseButton);
            tfAddExhaustion.mouseClicked(x, y, mouseButton);
            tfMinesweeperCooldown.mouseClicked(x, y, mouseButton);
            tfServerMaxPreviewRadius.mouseClicked(x, y, mouseButton);
            tfServerMaxPreviewLimit.mouseClicked(x, y, mouseButton);
        }
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) {
        if (keyCode == Keyboard.KEY_ESCAPE) {
            mc.displayGuiScreen(null);
            return;
        }
        if (activeTab == TAB_CLIENT) {
            tfClientBigRadius.textboxKeyTyped(typedChar, keyCode);
            tfClientBlockLimit.textboxKeyTyped(typedChar, keyCode);
            tfClientSmallRadius.textboxKeyTyped(typedChar, keyCode);
            tfClientTunnelWidth.textboxKeyTyped(typedChar, keyCode);
            tfPreviewBigRadius.textboxKeyTyped(typedChar, keyCode);
            tfPreviewBlockLimit.textboxKeyTyped(typedChar, keyCode);
        } else if (activeTab == TAB_SERVER && EZMiner.clientIsOp) {
            tfServerBigRadius.textboxKeyTyped(typedChar, keyCode);
            tfServerBlockLimit.textboxKeyTyped(typedChar, keyCode);
            tfServerSmallRadius.textboxKeyTyped(typedChar, keyCode);
            tfServerTunnelWidth.textboxKeyTyped(typedChar, keyCode);
            tfBreakPerTick.textboxKeyTyped(typedChar, keyCode);
            tfAddExhaustion.textboxKeyTyped(typedChar, keyCode);
            tfMinesweeperCooldown.textboxKeyTyped(typedChar, keyCode);
            tfServerMaxPreviewRadius.textboxKeyTyped(typedChar, keyCode);
            tfServerMaxPreviewLimit.textboxKeyTyped(typedChar, keyCode);
        }
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /** Y coordinate (absolute screen) for client-tab row {@code index} (0-based). */
    private int clientRowY(int index) {
        return guiTop + CONTENT_START_Y + index * rowH;
    }

    /** Y coordinate (absolute screen) for server-tab row {@code index} (0-based). */
    private int serverRowY(int index) {
        return guiTop + CONTENT_START_Y + index * rowH;
    }

    /** Initialises all client-tab text fields at their correct positions. */
    private void initClientFields() {
        int fx = guiLeft + FIELD_X;
        tfClientBigRadius = field(fx, clientRowY(0), String.valueOf(Config.clientBigRadius));
        tfClientBlockLimit = field(fx, clientRowY(1), String.valueOf(Config.clientBlockLimit));
        tfClientSmallRadius = field(fx, clientRowY(2), String.valueOf(Config.clientSmallRadius));
        tfClientTunnelWidth = field(fx, clientRowY(3), String.valueOf(Config.clientTunnelWidth));
        tfPreviewBigRadius = field(fx, clientRowY(4), String.valueOf(Config.previewBigRadius));
        tfPreviewBlockLimit = field(fx, clientRowY(5), String.valueOf(Config.previewBlockLimit));
    }

    /** Initialises all server-tab text fields at their correct positions. */
    private void initServerFields() {
        int fx = guiLeft + FIELD_X;
        tfServerBigRadius = field(fx, serverRowY(0), String.valueOf(Config.bigRadius));
        tfServerBlockLimit = field(fx, serverRowY(1), String.valueOf(Config.blockLimit));
        tfServerSmallRadius = field(fx, serverRowY(2), String.valueOf(Config.smallRadius));
        tfServerTunnelWidth = field(fx, serverRowY(3), String.valueOf(Config.tunnelWidth));
        tfBreakPerTick = field(fx, serverRowY(4), String.valueOf(Config.breakPerTick));
        tfAddExhaustion = field(fx, serverRowY(5), String.valueOf(Config.addExhaustion));
        tfMinesweeperCooldown = field(fx, serverRowY(6), String.valueOf(Config.minesweeperProbeCooldownSeconds));
        tfServerMaxPreviewRadius = field(fx, serverRowY(7), String.valueOf(Config.serverMaxPreviewBigRadius));
        tfServerMaxPreviewLimit = field(fx, serverRowY(8), String.valueOf(Config.serverMaxPreviewBlockLimit));
    }

    /** Convenience factory for a positioned, pre-filled text field. */
    private GuiTextField field(int x, int y, String initialText) {
        GuiTextField tf = new GuiTextField(mc.fontRenderer, x, y, FIELD_W, FIELD_H);
        tf.setText(initialText);
        return tf;
    }

    /** Draws labels and text fields for the client settings tab. */
    private void drawClientTab() {
        int lx = guiLeft + LABEL_X;
        int lc = 0xCCCCCC;
        drawRow(lx, clientRowY(0), lc, "ezminer.config.bigRadius", tfClientBigRadius);
        drawRow(lx, clientRowY(1), lc, "ezminer.config.blockLimit", tfClientBlockLimit);
        drawRow(lx, clientRowY(2), lc, "ezminer.config.smallRadius", tfClientSmallRadius);
        drawRow(lx, clientRowY(3), lc, "ezminer.config.tunnelWidth", tfClientTunnelWidth);
        drawRow(lx, clientRowY(4), lc, "ezminer.config.previewBigRadius", tfPreviewBigRadius);
        drawRow(lx, clientRowY(5), lc, "ezminer.config.previewBlockLimit", tfPreviewBlockLimit);
        // Boolean toggles are rendered by super.drawScreen via the buttonList.
    }

    /** Draws labels and text fields for the server settings tab. */
    private void drawServerTab() {
        int lx = guiLeft + LABEL_X;
        int lc = 0xCCCCCC;
        drawRow(lx, serverRowY(0), lc, "ezminer.config.bigRadius", tfServerBigRadius);
        drawRow(lx, serverRowY(1), lc, "ezminer.config.blockLimit", tfServerBlockLimit);
        drawRow(lx, serverRowY(2), lc, "ezminer.config.smallRadius", tfServerSmallRadius);
        drawRow(lx, serverRowY(3), lc, "ezminer.config.tunnelWidth", tfServerTunnelWidth);
        drawRow(lx, serverRowY(4), lc, "ezminer.config.breakPerTick", tfBreakPerTick);
        drawRow(lx, serverRowY(5), lc, "ezminer.config.addExhaustion", tfAddExhaustion);
        drawRow(lx, serverRowY(6), lc, "ezminer.config.minesweeperCooldown", tfMinesweeperCooldown);
        drawRow(lx, serverRowY(7), lc, "ezminer.config.serverPreviewRadius", tfServerMaxPreviewRadius);
        drawRow(lx, serverRowY(8), lc, "ezminer.config.serverPreviewLimit", tfServerMaxPreviewLimit);
    }

    /** Draws a label and its associated text field on a single row. */
    private void drawRow(int labelX, int y, int color, String labelKey, GuiTextField field) {
        mc.fontRenderer.drawStringWithShadow(I18n.format(labelKey) + ":", labelX, y + 2, color);
        field.drawTextBox();
    }

    /** Shows only buttons belonging to the currently-active tab. */
    @SuppressWarnings("unchecked")
    private void updateTabVisibility() {
        for (Object obj : buttonList) {
            GuiButton btn = (GuiButton) obj;
            boolean isClientOnly = btn.id == BTN_USE_PREVIEW || btn.id == BTN_USE_CHAIN_DONE_MSG
                || btn.id == BTN_CHAIN_ACTIVATION_MODE
                || btn.id == BTN_SUPPRESS_INGAME_INFO
                || btn.id == BTN_HUD_ANIM_STYLE
                || btn.id == BTN_RENDER_STYLE
                || btn.id == BTN_CLIENT_RELOAD
                || btn.id == BTN_CLIENT_SAVE;
            boolean isServerOnly = btn.id == BTN_SERVER_DROP_TO_PLAYER || btn.id == BTN_SERVER_USE_PREVIEW
                || btn.id == BTN_SERVER_RELOAD
                || btn.id == BTN_SERVER_SAVE;
            if (isClientOnly) btn.visible = (activeTab == TAB_CLIENT);
            if (isServerOnly) btn.visible = (activeTab == TAB_SERVER);
        }
    }

    /** Reads client text fields, updates Config, and writes to disk. */
    private void applyAndSaveClientConfig() {
        Config.clientBigRadius = parseI(tfClientBigRadius, Config.clientBigRadius, 0);
        Config.clientBlockLimit = parseI(tfClientBlockLimit, Config.clientBlockLimit, 0);
        Config.clientSmallRadius = parseI(tfClientSmallRadius, Config.clientSmallRadius, 0);
        Config.clientTunnelWidth = parseI(tfClientTunnelWidth, Config.clientTunnelWidth, 0);
        Config.previewBigRadius = parseI(tfPreviewBigRadius, Config.previewBigRadius, 0);
        Config.previewBlockLimit = parseI(tfPreviewBlockLimit, Config.previewBlockLimit, 0);
        Config.clampClientMiningToServerCaps();
        Config.clampClientPreviewToServerCaps();
        Config.saveClientConfig();
        // Sync updated values to server
        EZMiner.network.network.sendToServer(new PacketMinerConfig(Config.buildClientMinerConfigForSync()));
    }

    /** Reads server text fields and sends a save packet to the server. */
    private void applyAndSaveServerConfig() {
        EZMiner.network.network.sendToServer(
            new PacketSaveServerConfig(
                parseI(tfServerBigRadius, Config.bigRadius, 0),
                parseI(tfServerBlockLimit, Config.blockLimit, 0),
                parseI(tfServerSmallRadius, Config.smallRadius, 0),
                parseI(tfServerTunnelWidth, Config.tunnelWidth, 0),
                parseI(tfBreakPerTick, Config.breakPerTick, 1),
                parseD(tfAddExhaustion, Config.addExhaustion),
                Config.dropToPlayer,
                Config.serverUsePreview,
                parseI(tfServerMaxPreviewRadius, Config.serverMaxPreviewBigRadius, 0),
                parseI(tfServerMaxPreviewLimit, Config.serverMaxPreviewBlockLimit, 0),
                parseI(tfMinesweeperCooldown, Config.minesweeperProbeCooldownSeconds, 1)));
    }

    // ── Static helpers ────────────────────────────────────────────────────────

    private static int parseI(GuiTextField field, int fallback, int min) {
        try {
            return Math.max(
                min,
                Integer.parseInt(
                    field.getText()
                        .trim()));
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static double parseD(GuiTextField field, double fallback) {
        try {
            return Double.parseDouble(
                field.getText()
                    .trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static String boolLabel(String key, boolean value) {
        return I18n.format(key) + ": " + (value ? "§aON§r" : "§cOFF§r");
    }

    private static String activationModeLabel() {
        String desc = Config.chainActivationMode == 0 ? I18n.format("ezminer.command.active_mode.desc.0")
            : I18n.format("ezminer.command.active_mode.desc.1");
        return I18n.format("ezminer.config.chainActivationMode") + ": " + desc;
    }

    private static String hudAnimStyleLabel() {
        String style = Config.hudAnimationStyle == 0 ? I18n.format("ezminer.config.hudAnimStyle.rainbow")
            : I18n.format("ezminer.config.hudAnimStyle.wave");
        return I18n.format("ezminer.config.hudAnimationStyle") + ": §e" + style + "§r";
    }

    private static String renderStyleLabel() {
        String style = Config.renderStyle == 0 ? I18n.format("ezminer.config.renderStyle.native")
            : I18n.format("ezminer.config.renderStyle.modern");
        return I18n.format("ezminer.config.renderStyle") + ": §e" + style + "§r";
    }
}
