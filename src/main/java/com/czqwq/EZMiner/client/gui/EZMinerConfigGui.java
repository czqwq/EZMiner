package com.czqwq.EZMiner.client.gui;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.resources.I18n;
import net.minecraft.util.MathHelper;

import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

import com.czqwq.EZMiner.Config;
import com.czqwq.EZMiner.EZMiner;
import com.czqwq.EZMiner.network.PacketMinerConfig;
import com.czqwq.EZMiner.network.PacketReloadServerConfig;
import com.czqwq.EZMiner.network.PacketSaveServerConfig;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/** In-game configuration GUI with scrollable content area. */
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
    private static final int BTN_BLOCK_SCROLL_ON_CHAIN_KEY = 15;
    private static final int BTN_SMART_TOOL_SWITCH_ENABLED = 16;
    private static final int BTN_SMART_TOOL_SWITCH_ACTIVATION_MODE = 17;
    private static final int BTN_SERVER_DROP_IMMEDIATELY = 18;
    private static final int BTN_SERVER_ENABLE_CACHED_CHAIN = 19;
    private static final int BTN_SERVER_SUPPRESS_HODGEPODGE_WARNINGS = 20;
    private static final int BTN_SERVER_ENABLE_CHAIN_CHUNK_LOADING = 21;
    private static final int BTN_SERVER_USE_CHUNK_CACHED_HARVEST = 22;
    private static final int BTN_SERVER_CRAZY_MODE = 23;
    private static final int BTN_SERVER_STOP_ON_UNBREAKABLE = 24;
    private static final int BTN_SERVER_XP_DROP_MODE = 25;
    private static final int BTN_SERVER_MERGE_XP_ORBS = 26;
    private static final int BTN_SERVER_ENABLE_BLOCK_SWAP = 27;
    private static final int BTN_SERVER_FIRE_BREAK_EVENT = 28;

    // ── Layout constants ──────────────────────────────────────────────────────
    private static final int GUI_W = 290;
    /** Maximum GUI height before clamping to screen. */
    private static final int MAX_GUI_H = 358;
    private static final int LABEL_X = 10;
    private static final int FIELD_X = 162;
    private static final int FIELD_W = 100;
    private static final int FIELD_H = 14;
    /** Y (relative to guiTop) where scrollable content begins. */
    private static final int CONTENT_START_Y = 42;
    private static final int MAX_CONTENT_ROWS = 18; // Client tab row count
    /** Server tab row count (has more fields than client tab). */
    private static final int SERVER_CONTENT_ROWS = 31;
    private static final int ROW_H = 20;
    /** Extra vertical spacing added between lines when a label contains \n. */
    private static final int EXTRA_LINE_SPACING = 2;
    /** Gap after each section's last row for the section header. */
    private static final int SECTION_GAP = 18;
    /** Top padding inside the scrollable area for the first section header. */
    private static final int TOP_PAD = 12;
    private static final int ACTION_STRIP_H = 48;
    private static final int MIN_VIEWPORT_H = ROW_H * 3;
    private static final int SCROLLBAR_W = 4;

    // ── Adaptive layout (computed in initGui) ─────────────────────────────────
    /** Actual panel height for the current screen. */
    private int guiH;
    /** Y of the action-button row, relative to guiTop. */
    private int actionBtnY;
    /** Top of scrollable viewport, in absolute screen Y. */
    private int viewportTop;
    /** Bottom of scrollable viewport (= top of action-button strip), in absolute screen Y. */
    private int viewportBottom;
    /** Height of the scrollable viewport in pixels. */
    private int viewportH;
    /** Total height of all content rows for the active tab. */
    private int totalContentH;

    // ── Scroll state ──────────────────────────────────────────────────────────
    /** Current scroll offset in pixels (0 = top). */
    private int scrollY = 0;

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
    private GuiTextField tfCachedBreakPerTick;
    private GuiTextField tfSearchWorkerThreads;
    private GuiTextField tfAddExhaustion;
    private GuiTextField tfMinesweeperCooldown;
    private GuiTextField tfSudokuCooldown;
    private GuiTextField tfServerMaxPreviewRadius;
    private GuiTextField tfServerMaxPreviewLimit;
    private GuiTextField tfChainIdleTimeout;
    private GuiTextField tfChainIdleCountdown;
    private GuiTextField tfChainCooldownTicks;
    private GuiTextField tfClientBlockSwapRadius;
    private GuiTextField tfClientBlockSwapLimit;
    private GuiTextField tfClientBlockSwapAdjacencyRadius;
    private GuiTextField tfServerBlockSwapRadius;
    private GuiTextField tfServerBlockSwapLimit;
    private GuiTextField tfServerBlockSwapAdjacencyRadius;

    // ── Toggle button references ──────────────────────────────────────────────
    private GuiButton btnUsePreview;
    private GuiButton btnUseChainDoneMsg;
    private GuiButton btnChainActivationMode;
    private GuiButton btnSuppressIngameInfo;
    private GuiButton btnHudAnimStyle;
    private GuiButton btnRenderStyle;
    private GuiButton btnBlockScrollOnChainKey;
    private GuiButton btnSmartToolSwitchEnabled;
    private GuiButton btnSmartToolSwitchActivationMode;
    private GuiButton btnServerDropToPlayer;
    private GuiButton btnServerDropImmediately;
    private GuiButton btnServerUsePreview;
    private GuiButton btnServerEnableCachedChain;
    private GuiButton btnServerSuppressHodgepodgeWarnings;
    private GuiButton btnServerEnableChainChunkLoading;
    private GuiButton btnServerUseChunkCachedHarvest;
    private GuiButton btnServerCrazyMode;
    private GuiButton btnServerStopOnUnbreakable;
    private GuiButton btnServerXPDropMode;
    private GuiButton btnServerMergeXPOrbs;
    private GuiButton btnServerEnableBlockSwap;
    private GuiButton btnServerFireBreakEvent;

    // ── GuiScreen overrides ───────────────────────────────────────────────────

    @Override
    @SuppressWarnings("unchecked")
    public void initGui() {
        // ── Panel sizing ──────────────────────────────────────────────────────
        // Reserve at least MIN_VIEWPORT_H for content + fixed header/footer.
        int minH = CONTENT_START_Y + MIN_VIEWPORT_H + ACTION_STRIP_H + 6;
        guiH = MathHelper.clamp_int(MAX_GUI_H, minH, height - 6);
        guiLeft = (width - GUI_W) / 2;
        guiTop = (height - guiH) / 2;

        actionBtnY = guiH - ACTION_STRIP_H + 10;
        viewportTop = guiTop + CONTENT_START_Y;
        // 14 px dead zone between scissor bottom and the action-button strip so
        // scrolled content never visually collides with the fixed Save/Apply row.
        viewportBottom = guiTop + actionBtnY - 14;
        viewportH = viewportBottom - viewportTop;

        // Reset scroll when GUI is (re)opened.
        scrollY = 0;
        buttonList.clear();

        // ── Fixed: tab selector buttons ───────────────────────────────────────
        buttonList.add(
            new GuiButton(BTN_TAB_CLIENT, guiLeft + 4, guiTop + 18, 138, 18, I18n.format("ezminer.gui.tab.client")));
        if (EZMiner.clientIsOp) {
            buttonList.add(
                new GuiButton(
                    BTN_TAB_SERVER,
                    guiLeft + 148,
                    guiTop + 18,
                    138,
                    18,
                    I18n.format("ezminer.gui.tab.server")));
        }

        // Close button (top-right corner)
        buttonList.add(new GuiButton(BTN_CLOSE, guiLeft + GUI_W - 20, guiTop + 4, 16, 12, "X"));

        // ── Scrollable: client tab toggle buttons ─────────────────────────────
        // Multi-line labels use a compact two-column layout (label left, button
        // right) to avoid text overlapping the button background. Single-line
        // labels keep the full-width button that serves as both label and control.
        int bx = guiLeft + LABEL_X;
        int bw = GUI_W - 2 * LABEL_X - SCROLLBAR_W - 2;

        btnUsePreview = new GuiButton(
            BTN_USE_PREVIEW,
            bx,
            contentRowScreenY(6),
            bw,
            FIELD_H,
            boolLabel("ezminer.config.usePreview", Config.usePreview));
        buttonList.add(btnUsePreview);

        btnUseChainDoneMsg = new GuiButton(
            BTN_USE_CHAIN_DONE_MSG,
            bx,
            contentRowScreenY(7),
            bw,
            FIELD_H,
            boolLabel("ezminer.config.useChainDoneMessage", Config.useChainDoneMessage));
        buttonList.add(btnUseChainDoneMsg);

        btnChainActivationMode = new GuiButton(
            BTN_CHAIN_ACTIVATION_MODE,
            bx,
            contentRowScreenY(8),
            bw,
            FIELD_H,
            activationModeLabel());
        buttonList.add(btnChainActivationMode);

        btnSuppressIngameInfo = new GuiButton(
            BTN_SUPPRESS_INGAME_INFO,
            bx,
            contentRowScreenY(9),
            bw,
            FIELD_H,
            boolLabel("ezminer.config.suppressIngameInfoHud", Config.suppressIngameInfoHud));
        buttonList.add(btnSuppressIngameInfo);

        btnHudAnimStyle = new GuiButton(
            BTN_HUD_ANIM_STYLE,
            bx,
            contentRowScreenY(10),
            bw,
            FIELD_H,
            hudAnimStyleLabel());
        buttonList.add(btnHudAnimStyle);

        btnRenderStyle = new GuiButton(BTN_RENDER_STYLE, bx, contentRowScreenY(11), bw, FIELD_H, renderStyleLabel());
        buttonList.add(btnRenderStyle);

        btnBlockScrollOnChainKey = newOptionButton(
            BTN_BLOCK_SCROLL_ON_CHAIN_KEY,
            12,
            "ezminer.config.blockScrollOnChainKey",
            boolLabel("ezminer.config.blockScrollOnChainKey", Config.blockScrollOnChainKey),
            boolValue(Config.blockScrollOnChainKey));
        buttonList.add(btnBlockScrollOnChainKey);

        // Smart Tool Switch section
        btnSmartToolSwitchEnabled = newOptionButton(
            BTN_SMART_TOOL_SWITCH_ENABLED,
            13,
            "ezminer.config.smartToolSwitchEnabled",
            boolLabel("ezminer.config.smartToolSwitchEnabled", Config.smartToolSwitchEnabled),
            boolValue(Config.smartToolSwitchEnabled));
        buttonList.add(btnSmartToolSwitchEnabled);

        btnSmartToolSwitchActivationMode = newOptionButton(
            BTN_SMART_TOOL_SWITCH_ACTIVATION_MODE,
            14,
            "ezminer.config.smartToolSwitchActivationMode",
            smartToolSwitchActivationModeLabel(),
            smartToolSwitchActivationModeValue());
        buttonList.add(btnSmartToolSwitchActivationMode);

        // ── Fixed: client action buttons ──────────────────────────────────────
        buttonList.add(
            new GuiButton(
                BTN_CLIENT_RELOAD,
                guiLeft + 6,
                guiTop + actionBtnY,
                134,
                20,
                I18n.format("ezminer.gui.apply")));
        buttonList.add(
            new GuiButton(
                BTN_CLIENT_SAVE,
                guiLeft + 150,
                guiTop + actionBtnY,
                134,
                20,
                I18n.format("ezminer.gui.saveAndExit")));

        // ── Scrollable: server tab toggle buttons ─────────────────────────────
        if (EZMiner.clientIsOp) {
            btnServerDropToPlayer = newOptionButton(
                BTN_SERVER_DROP_TO_PLAYER,
                11,
                "ezminer.config.dropToPlayer",
                boolLabel("ezminer.config.dropToPlayer", Config.dropToPlayer),
                boolValue(Config.dropToPlayer));
            buttonList.add(btnServerDropToPlayer);

            btnServerDropImmediately = newOptionButton(
                BTN_SERVER_DROP_IMMEDIATELY,
                12,
                "ezminer.config.dropImmediately",
                boolLabel("ezminer.config.dropImmediately", Config.dropImmediately),
                boolValue(Config.dropImmediately));
            buttonList.add(btnServerDropImmediately);

            btnServerUsePreview = newOptionButton(
                BTN_SERVER_USE_PREVIEW,
                13,
                "ezminer.config.serverUsePreview",
                boolLabel("ezminer.config.serverUsePreview", Config.serverUsePreview),
                boolValue(Config.serverUsePreview));
            buttonList.add(btnServerUsePreview);

            btnServerEnableCachedChain = newOptionButton(
                BTN_SERVER_ENABLE_CACHED_CHAIN,
                14,
                "ezminer.config.enableCachedChain",
                boolLabel("ezminer.config.enableCachedChain", Config.enableCachedChain),
                boolValue(Config.enableCachedChain));
            buttonList.add(btnServerEnableCachedChain);

            btnServerSuppressHodgepodgeWarnings = newOptionButton(
                BTN_SERVER_SUPPRESS_HODGEPODGE_WARNINGS,
                15,
                "ezminer.config.suppressHodgepodgeWarnings",
                boolLabel("ezminer.config.suppressHodgepodgeWarnings", Config.suppressHodgepodgeWarnings),
                boolValue(Config.suppressHodgepodgeWarnings));
            buttonList.add(btnServerSuppressHodgepodgeWarnings);

            btnServerEnableChainChunkLoading = newOptionButton(
                BTN_SERVER_ENABLE_CHAIN_CHUNK_LOADING,
                17,
                "ezminer.config.enableChainChunkLoading",
                boolLabel("ezminer.config.enableChainChunkLoading", Config.enableChainChunkLoading),
                boolValue(Config.enableChainChunkLoading));
            buttonList.add(btnServerEnableChainChunkLoading);

            btnServerUseChunkCachedHarvest = newOptionButton(
                BTN_SERVER_USE_CHUNK_CACHED_HARVEST,
                18,
                "ezminer.config.useChunkCachedHarvest",
                boolLabel("ezminer.config.useChunkCachedHarvest", Config.useChunkCachedHarvest),
                boolValue(Config.useChunkCachedHarvest));
            buttonList.add(btnServerUseChunkCachedHarvest);

            btnServerCrazyMode = newOptionButton(
                BTN_SERVER_CRAZY_MODE,
                19,
                "ezminer.config.crazyMode",
                boolLabel("ezminer.config.crazyMode", Config.crazyMode),
                boolValue(Config.crazyMode));
            buttonList.add(btnServerCrazyMode);

            btnServerStopOnUnbreakable = newOptionButton(
                BTN_SERVER_STOP_ON_UNBREAKABLE,
                23,
                "ezminer.config.stopOnUnbreakable",
                boolLabel("ezminer.config.stopOnUnbreakable", Config.stopOnUnbreakable),
                boolValue(Config.stopOnUnbreakable));
            buttonList.add(btnServerStopOnUnbreakable);

            btnServerXPDropMode = newOptionButton(
                BTN_SERVER_XP_DROP_MODE,
                24,
                "ezminer.config.xpDropMode",
                xpDropModeLabel(),
                xpDropModeValue());
            buttonList.add(btnServerXPDropMode);

            btnServerMergeXPOrbs = newOptionButton(
                BTN_SERVER_MERGE_XP_ORBS,
                25,
                "ezminer.config.mergeXPOrbs",
                boolLabel("ezminer.config.mergeXPOrbs", Config.mergeXPOrbs),
                boolValue(Config.mergeXPOrbs));
            buttonList.add(btnServerMergeXPOrbs);

            btnServerFireBreakEvent = newOptionButton(
                BTN_SERVER_FIRE_BREAK_EVENT,
                26,
                "ezminer.config.fireBreakEvent",
                boolLabel("ezminer.config.fireBreakEvent", Config.fireBreakEvent),
                boolValue(Config.fireBreakEvent));
            buttonList.add(btnServerFireBreakEvent);

            btnServerEnableBlockSwap = newOptionButton(
                BTN_SERVER_ENABLE_BLOCK_SWAP,
                29,
                "ezminer.config.enableBlockSwapMode",
                boolLabel("ezminer.config.enableBlockSwapMode", Config.enableBlockSwapMode),
                boolValue(Config.enableBlockSwapMode));
            buttonList.add(btnServerEnableBlockSwap);

            // Fixed: server action buttons
            buttonList.add(
                new GuiButton(
                    BTN_SERVER_RELOAD,
                    guiLeft + 6,
                    guiTop + actionBtnY,
                    134,
                    20,
                    I18n.format("ezminer.gui.reload.server")));
            buttonList.add(
                new GuiButton(
                    BTN_SERVER_SAVE,
                    guiLeft + 150,
                    guiTop + actionBtnY,
                    134,
                    20,
                    I18n.format("ezminer.gui.save")));
        }

        // ── Text fields ───────────────────────────────────────────────────────
        initClientFields();
        if (EZMiner.clientIsOp) {
            initServerFields();
        }

        recalcTotalContentH();
        updateTabVisibility();
    }

    @Override
    public void handleMouseInput() {
        super.handleMouseInput();
        int wheel = Mouse.getEventDWheel();
        if (wheel != 0) {
            int delta = wheel > 0 ? -ROW_H : ROW_H;
            scrollY = MathHelper.clamp_int(scrollY + delta, 0, maxScroll());
            updateScrolledPositions();
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();

        // Outer shadow / panel
        drawRect(guiLeft - 1, guiTop - 1, guiLeft + GUI_W + 1, guiTop + guiH + 1, 0x88000000);
        drawRect(guiLeft, guiTop, guiLeft + GUI_W, guiTop + guiH, 0xCC000000);
        drawRect(guiLeft + 1, guiTop + 1, guiLeft + GUI_W - 1, guiTop + guiH - 1, 0xFF1A1A2E);
        // Top accent line
        drawRect(guiLeft + 1, guiTop + 1, guiLeft + GUI_W - 1, guiTop + 3, 0xFF4466CC);
        // Title
        drawCenteredString(
            mc.fontRenderer,
            "§b[EZMiner] §f" + I18n.format("ezminer.gui.title"),
            guiLeft + GUI_W / 2,
            guiTop + 6,
            0xFFFFFF);
        // Tab divider
        drawRect(guiLeft + 2, guiTop + 36, guiLeft + GUI_W - 2, guiTop + 37, 0xFF4455AA);
        // Action-strip divider
        drawRect(guiLeft + 2, guiTop + actionBtnY - 6, guiLeft + GUI_W - 2, guiTop + actionBtnY - 5, 0xFF333355);

        // ── Viewport clipping ─────────────────────────────────────────────────
        enableScissor(guiLeft + 1, viewportTop, GUI_W - 2, viewportH);

        if (activeTab == TAB_CLIENT) {
            drawClientTab();
        } else {
            drawServerTab();
        }

        GL11.glDisable(GL11.GL_SCISSOR_TEST);

        // ── Scrollbar ────────────────────────────────────────────────────────
        drawScrollbar();

        // Draw fixed buttons and everything else (outside scissor)
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        switch (button.id) {
            case BTN_TAB_CLIENT:
                activeTab = TAB_CLIENT;
                scrollY = 0;
                recalcTotalContentH();
                updateTabVisibility();
                updateScrolledPositions();
                break;
            case BTN_TAB_SERVER:
                activeTab = TAB_SERVER;
                scrollY = 0;
                recalcTotalContentH();
                updateTabVisibility();
                updateScrolledPositions();
                break;
            case BTN_CLOSE:
                mc.displayGuiScreen(null);
                break;

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

            case BTN_BLOCK_SCROLL_ON_CHAIN_KEY:
                Config.blockScrollOnChainKey = !Config.blockScrollOnChainKey;
                btnBlockScrollOnChainKey.displayString = boolDisplayText(
                    "ezminer.config.blockScrollOnChainKey",
                    Config.blockScrollOnChainKey);
                break;
            case BTN_SMART_TOOL_SWITCH_ENABLED:
                Config.smartToolSwitchEnabled = !Config.smartToolSwitchEnabled;
                btnSmartToolSwitchEnabled.displayString = boolDisplayText(
                    "ezminer.config.smartToolSwitchEnabled",
                    Config.smartToolSwitchEnabled);
                break;
            case BTN_SMART_TOOL_SWITCH_ACTIVATION_MODE:
                Config.smartToolSwitchActivationMode = 1 - Config.smartToolSwitchActivationMode;
                btnSmartToolSwitchActivationMode.displayString = smartToolSwitchActivationModeLabel();
                break;

            case BTN_CLIENT_RELOAD:
                applyAndSaveClientConfig();
                break;
            case BTN_CLIENT_SAVE:
                applyAndSaveClientConfig();
                mc.displayGuiScreen(null);
                break;

            case BTN_SERVER_DROP_TO_PLAYER:
                Config.dropToPlayer = !Config.dropToPlayer;
                btnServerDropToPlayer.displayString = boolDisplayText(
                    "ezminer.config.dropToPlayer",
                    Config.dropToPlayer);
                break;
            case BTN_SERVER_DROP_IMMEDIATELY:
                Config.dropImmediately = !Config.dropImmediately;
                btnServerDropImmediately.displayString = boolDisplayText(
                    "ezminer.config.dropImmediately",
                    Config.dropImmediately);
                break;
            case BTN_SERVER_USE_PREVIEW:
                Config.serverUsePreview = !Config.serverUsePreview;
                btnServerUsePreview.displayString = boolDisplayText(
                    "ezminer.config.serverUsePreview",
                    Config.serverUsePreview);
                break;
            case BTN_SERVER_ENABLE_CACHED_CHAIN:
                Config.enableCachedChain = !Config.enableCachedChain;
                btnServerEnableCachedChain.displayString = boolDisplayText(
                    "ezminer.config.enableCachedChain",
                    Config.enableCachedChain);
                break;
            case BTN_SERVER_SUPPRESS_HODGEPODGE_WARNINGS:
                Config.suppressHodgepodgeWarnings = !Config.suppressHodgepodgeWarnings;
                btnServerSuppressHodgepodgeWarnings.displayString = boolDisplayText(
                    "ezminer.config.suppressHodgepodgeWarnings",
                    Config.suppressHodgepodgeWarnings);
                break;
            case BTN_SERVER_ENABLE_CHAIN_CHUNK_LOADING:
                Config.enableChainChunkLoading = !Config.enableChainChunkLoading;
                btnServerEnableChainChunkLoading.displayString = boolDisplayText(
                    "ezminer.config.enableChainChunkLoading",
                    Config.enableChainChunkLoading);
                break;
            case BTN_SERVER_USE_CHUNK_CACHED_HARVEST:
                Config.useChunkCachedHarvest = !Config.useChunkCachedHarvest;
                btnServerUseChunkCachedHarvest.displayString = boolDisplayText(
                    "ezminer.config.useChunkCachedHarvest",
                    Config.useChunkCachedHarvest);
                break;
            case BTN_SERVER_CRAZY_MODE:
                Config.crazyMode = !Config.crazyMode;
                btnServerCrazyMode.displayString = boolDisplayText("ezminer.config.crazyMode", Config.crazyMode);
                break;
            case BTN_SERVER_STOP_ON_UNBREAKABLE:
                Config.stopOnUnbreakable = !Config.stopOnUnbreakable;
                btnServerStopOnUnbreakable.displayString = boolDisplayText(
                    "ezminer.config.stopOnUnbreakable",
                    Config.stopOnUnbreakable);
                break;
            case BTN_SERVER_XP_DROP_MODE:
                Config.xpDropMode = 1 - Config.xpDropMode;
                btnServerXPDropMode.displayString = xpDropModeDisplayText();
                break;
            case BTN_SERVER_MERGE_XP_ORBS:
                Config.mergeXPOrbs = !Config.mergeXPOrbs;
                btnServerMergeXPOrbs.displayString = boolDisplayText("ezminer.config.mergeXPOrbs", Config.mergeXPOrbs);
                break;
            case BTN_SERVER_ENABLE_BLOCK_SWAP:
                Config.enableBlockSwapMode = !Config.enableBlockSwapMode;
                btnServerEnableBlockSwap.displayString = boolDisplayText(
                    "ezminer.config.enableBlockSwapMode",
                    Config.enableBlockSwapMode);
                break;
            case BTN_SERVER_FIRE_BREAK_EVENT:
                Config.fireBreakEvent = !Config.fireBreakEvent;
                btnServerFireBreakEvent.displayString = boolDisplayText(
                    "ezminer.config.fireBreakEvent",
                    Config.fireBreakEvent);
                break;

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
        // Only forward clicks that land inside the scrollable viewport.
        if (y < viewportTop || y >= viewportBottom) return;
        if (activeTab == TAB_CLIENT) {
            tfClientBigRadius.mouseClicked(x, y, mouseButton);
            tfClientBlockLimit.mouseClicked(x, y, mouseButton);
            tfClientSmallRadius.mouseClicked(x, y, mouseButton);
            tfClientTunnelWidth.mouseClicked(x, y, mouseButton);
            tfPreviewBigRadius.mouseClicked(x, y, mouseButton);
            tfPreviewBlockLimit.mouseClicked(x, y, mouseButton);
            tfClientBlockSwapRadius.mouseClicked(x, y, mouseButton);
            tfClientBlockSwapLimit.mouseClicked(x, y, mouseButton);
            tfClientBlockSwapAdjacencyRadius.mouseClicked(x, y, mouseButton);
        } else if (activeTab == TAB_SERVER && EZMiner.clientIsOp) {
            tfServerBigRadius.mouseClicked(x, y, mouseButton);
            tfServerBlockLimit.mouseClicked(x, y, mouseButton);
            tfServerSmallRadius.mouseClicked(x, y, mouseButton);
            tfServerTunnelWidth.mouseClicked(x, y, mouseButton);
            tfBreakPerTick.mouseClicked(x, y, mouseButton);
            tfCachedBreakPerTick.mouseClicked(x, y, mouseButton);
            tfSearchWorkerThreads.mouseClicked(x, y, mouseButton);
            tfAddExhaustion.mouseClicked(x, y, mouseButton);
            tfMinesweeperCooldown.mouseClicked(x, y, mouseButton);
            tfSudokuCooldown.mouseClicked(x, y, mouseButton);
            tfServerMaxPreviewRadius.mouseClicked(x, y, mouseButton);
            tfServerMaxPreviewLimit.mouseClicked(x, y, mouseButton);
            tfChainIdleTimeout.mouseClicked(x, y, mouseButton);
            tfChainIdleCountdown.mouseClicked(x, y, mouseButton);
            tfChainCooldownTicks.mouseClicked(x, y, mouseButton);
            tfServerBlockSwapRadius.mouseClicked(x, y, mouseButton);
            tfServerBlockSwapLimit.mouseClicked(x, y, mouseButton);
            tfServerBlockSwapAdjacencyRadius.mouseClicked(x, y, mouseButton);
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
            tfClientBlockSwapRadius.textboxKeyTyped(typedChar, keyCode);
            tfClientBlockSwapLimit.textboxKeyTyped(typedChar, keyCode);
            tfClientBlockSwapAdjacencyRadius.textboxKeyTyped(typedChar, keyCode);
        } else if (activeTab == TAB_SERVER && EZMiner.clientIsOp) {
            tfServerBigRadius.textboxKeyTyped(typedChar, keyCode);
            tfServerBlockLimit.textboxKeyTyped(typedChar, keyCode);
            tfServerSmallRadius.textboxKeyTyped(typedChar, keyCode);
            tfServerTunnelWidth.textboxKeyTyped(typedChar, keyCode);
            tfBreakPerTick.textboxKeyTyped(typedChar, keyCode);
            tfCachedBreakPerTick.textboxKeyTyped(typedChar, keyCode);
            tfSearchWorkerThreads.textboxKeyTyped(typedChar, keyCode);
            tfAddExhaustion.textboxKeyTyped(typedChar, keyCode);
            tfMinesweeperCooldown.textboxKeyTyped(typedChar, keyCode);
            tfSudokuCooldown.textboxKeyTyped(typedChar, keyCode);
            tfServerMaxPreviewRadius.textboxKeyTyped(typedChar, keyCode);
            tfServerMaxPreviewLimit.textboxKeyTyped(typedChar, keyCode);
            tfChainIdleTimeout.textboxKeyTyped(typedChar, keyCode);
            tfChainIdleCountdown.textboxKeyTyped(typedChar, keyCode);
            tfChainCooldownTicks.textboxKeyTyped(typedChar, keyCode);
            tfServerBlockSwapRadius.textboxKeyTyped(typedChar, keyCode);
            tfServerBlockSwapLimit.textboxKeyTyped(typedChar, keyCode);
            tfServerBlockSwapAdjacencyRadius.textboxKeyTyped(typedChar, keyCode);
        }
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    // ── Layout helpers ────────────────────────────────────────────────────────

    /**
     * Returns the current absolute screen-Y of content row {@code index}, accounting
     * for the current scroll offset and any multi-line rows above it.
     */
    private int contentRowScreenY(int index) {
        int y = viewportTop + TOP_PAD - scrollY;
        for (int i = 0; i < index; i++) {
            y += getRowHeight(i);
        }
        return y;
    }

    /** Maximum allowed scrollY for the active tab. */
    private int maxScroll() {
        return Math.max(0, totalContentH - viewportH);
    }

    // ── Multi-line label support ─────────────────────────────────────────────────

    /** I18n key for the content row, or {@code null}. */
    private String getRowLabelKey(int index) {
        if (activeTab == TAB_CLIENT) {
            switch (index) {
                case 0:
                    return "ezminer.config.bigRadius";
                case 1:
                    return "ezminer.config.blockLimit";
                case 2:
                    return "ezminer.config.smallRadius";
                case 3:
                    return "ezminer.config.tunnelWidth";
                case 4:
                    return "ezminer.config.previewBigRadius";
                case 5:
                    return "ezminer.config.previewBlockLimit";
                case 6:
                    return "ezminer.config.usePreview";
                case 7:
                    return "ezminer.config.useChainDoneMessage";
                case 8:
                    return "ezminer.config.chainActivationMode";
                case 9:
                    return "ezminer.config.suppressIngameInfoHud";
                case 10:
                    return "ezminer.config.hudAnimationStyle";
                case 11:
                    return "ezminer.config.renderStyle";
                case 12:
                    return "ezminer.config.blockScrollOnChainKey";
                case 13:
                    return "ezminer.config.smartToolSwitchEnabled";
                case 14:
                    return "ezminer.config.smartToolSwitchActivationMode";
                case 15:
                    return "ezminer.config.blockSwapRadius";
                case 16:
                    return "ezminer.config.blockSwapLimit";
                case 17:
                    return "ezminer.config.blockSwapAdjacencyRadius";
                default:
                    return null;
            }
        } else {
            switch (index) {
                case 0:
                    return "ezminer.config.bigRadius";
                case 1:
                    return "ezminer.config.blockLimit";
                case 2:
                    return "ezminer.config.smallRadius";
                case 3:
                    return "ezminer.config.tunnelWidth";
                case 4:
                    return "ezminer.config.breakPerTick";
                case 5:
                    return "ezminer.config.cachedBreakPerTick";
                case 6:
                    return "ezminer.config.searchWorkerThreads";
                case 7:
                    return "ezminer.config.addExhaustion";
                case 8:
                    return "ezminer.config.minesweeperCooldown";
                case 9:
                    return "ezminer.config.sudokuCooldown";
                case 10:
                    return "ezminer.config.serverPreviewRadius";
                case 11:
                    return "ezminer.config.serverPreviewLimit";
                case 12:
                    return "ezminer.config.dropToPlayer";
                case 13:
                    return "ezminer.config.dropImmediately";
                case 14:
                    return "ezminer.config.serverUsePreview";
                case 15:
                    return "ezminer.config.enableCachedChain";
                case 16:
                    return "ezminer.config.suppressHodgepodgeWarnings";
                case 17:
                    return "ezminer.config.enableChainChunkLoading";
                case 18:
                    return "ezminer.config.useChunkCachedHarvest";
                case 19:
                    return "ezminer.config.crazyMode";
                case 20:
                    return "ezminer.config.chainIdleTimeoutSeconds";
                case 21:
                    return "ezminer.config.chainIdleCountdownSeconds";
                case 22:
                    return "ezminer.config.chainCooldownTicks";
                case 23:
                    return "ezminer.config.stopOnUnbreakable";
                case 24:
                    return "ezminer.config.xpDropMode";
                case 25:
                    return "ezminer.config.mergeXPOrbs";
                case 26:
                    return "ezminer.config.fireBreakEvent";
                case 27:
                    return "ezminer.config.blockSwapRadius";
                case 28:
                    return "ezminer.config.blockSwapLimit";
                case 29:
                    return "ezminer.config.blockSwapAdjacencyRadius";
                case 30:
                    return "ezminer.config.enableBlockSwapMode";
                default:
                    return null;
            }
        }
    }

    /** Converts literal {@code \n} chars stored by MC 1.7.10's .lang parser to real newlines. */
    private static String resolveNewlines(String text) {
        return text.replace("\\n", "\n");
    }

    /** Returns {@code true} when the localised label contains a {@code \n} newline. */
    private static boolean isMultiLineLabel(String key) {
        return I18n.format(key)
            .contains("\\n");
    }

    /** Value-only button text for boolean options (no label prefix). */
    private static String boolValue(boolean value) {
        return value ? "§aON§r" : "§cOFF§r";
    }

    /** Value-only button text for activation mode (no label prefix). */
    private static String activationModeValue() {
        return Config.chainActivationMode == 0 ? I18n.format("ezminer.command.active_mode.desc.0")
            : I18n.format("ezminer.command.active_mode.desc.1");
    }

    /** Value-only button text for HUD animation style (no label prefix). */
    private static String hudAnimStyleValue() {
        String style = Config.hudAnimationStyle == 0 ? I18n.format("ezminer.config.hudAnimStyle.rainbow")
            : I18n.format("ezminer.config.hudAnimStyle.wave");
        return "§e" + style + "§r";
    }

    /** Value-only button text for render style (no label prefix). */
    private static String renderStyleValue() {
        String style = Config.renderStyle == 0 ? I18n.format("ezminer.config.renderStyle.native")
            : I18n.format("ezminer.config.renderStyle.modern");
        return "§e" + style + "§r";
    }

    /** Counts the number of {@code \n}-separated lines in the localised label. */
    private int getLabelLines(String key) {
        if (key == null) return 1;
        String text = resolveNewlines(I18n.format(key));
        int count = 1;
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == '\n') count++;
        }
        return count;
    }

    /**
     * Returns {@code true} when {@code index} is the last content row before a
     * section change. An extra {@link #SECTION_GAP} is appended to these rows so
     * the next section's header can be drawn without overlapping any content.
     */
    private boolean isSectionBreak(int index) {
        if (activeTab == TAB_CLIENT) {
            return index == 3 || index == 5 || index == 12 || index == 14; // after Mining, after Preview, before Smart
                                                                           // Tool Switch, after Smart Tool Switch
        }
        return index == 9 || index == 11 || index == 26; // after Mining, after Preview, after Options
    }

    /** Content height of a row excluding any section gap. */
    private int getContentRowHeight(int index) {
        String key = getRowLabelKey(index);
        int lines = getLabelLines(key);
        return ROW_H + (lines - 1) * (mc.fontRenderer.FONT_HEIGHT + EXTRA_LINE_SPACING);
    }

    /** Total height including any {@link #SECTION_GAP} after section breaks. */
    private int getRowHeight(int index) {
        int h = getContentRowHeight(index);
        if (isSectionBreak(index)) {
            h += SECTION_GAP;
        }
        return h;
    }

    /** First line of text before any {@code \n}, or the whole string. */
    private static String firstLine(String text) {
        String converted = resolveNewlines(text);
        int idx = converted.indexOf('\n');
        return idx >= 0 ? converted.substring(0, idx) : converted;
    }

    /** Recalculates totalContentH based on active tab row count and multi-line labels. */
    private void recalcTotalContentH() {
        int rows = (activeTab == TAB_CLIENT) ? MAX_CONTENT_ROWS : SERVER_CONTENT_ROWS;
        int total = TOP_PAD;
        for (int i = 0; i < rows; i++) {
            total += getRowHeight(i);
        }
        totalContentH = total;
    }

    /** Vertically-centered Y for a control on the given row (gap excluded). */
    private int getControlY(int index) {
        int rowTop = contentRowScreenY(index);
        int contentH = getContentRowHeight(index);
        if (contentH <= ROW_H) return rowTop;
        return rowTop + (contentH - FIELD_H) / 2;
    }

    /** Re-positions all scrollable controls after scroll/tab change. */
    private void updateScrolledPositions() {
        // Clamp first
        scrollY = MathHelper.clamp_int(scrollY, 0, maxScroll());

        if (activeTab == TAB_CLIENT) {
            int fx = guiLeft + FIELD_X;
            tfClientBigRadius.yPosition = getControlY(0);
            tfClientBlockLimit.yPosition = getControlY(1);
            tfClientSmallRadius.yPosition = getControlY(2);
            tfClientTunnelWidth.yPosition = getControlY(3);
            tfPreviewBigRadius.yPosition = getControlY(4);
            tfPreviewBlockLimit.yPosition = getControlY(5);

            setScrolledButtonY(BTN_USE_PREVIEW, getControlY(6));
            setScrolledButtonY(BTN_USE_CHAIN_DONE_MSG, getControlY(7));
            setScrolledButtonY(BTN_CHAIN_ACTIVATION_MODE, getControlY(8));
            setScrolledButtonY(BTN_SUPPRESS_INGAME_INFO, getControlY(9));
            setScrolledButtonY(BTN_HUD_ANIM_STYLE, getControlY(10));
            setScrolledButtonY(BTN_RENDER_STYLE, getControlY(11));
            setScrolledButtonY(BTN_BLOCK_SCROLL_ON_CHAIN_KEY, getControlY(12));
            setScrolledButtonY(BTN_SMART_TOOL_SWITCH_ENABLED, getControlY(13));
            setScrolledButtonY(BTN_SMART_TOOL_SWITCH_ACTIVATION_MODE, getControlY(14));

            tfClientBlockSwapRadius.yPosition = getControlY(15);
            tfClientBlockSwapLimit.yPosition = getControlY(16);
            tfClientBlockSwapAdjacencyRadius.yPosition = getControlY(17);
        } else if (EZMiner.clientIsOp) {
            tfServerBigRadius.yPosition = getControlY(0);
            tfServerBlockLimit.yPosition = getControlY(1);
            tfServerSmallRadius.yPosition = getControlY(2);
            tfServerTunnelWidth.yPosition = getControlY(3);
            tfBreakPerTick.yPosition = getControlY(4);
            tfCachedBreakPerTick.yPosition = getControlY(5);
            tfSearchWorkerThreads.yPosition = getControlY(6);
            tfAddExhaustion.yPosition = getControlY(7);
            tfMinesweeperCooldown.yPosition = getControlY(8);
            tfSudokuCooldown.yPosition = getControlY(9);
            tfServerMaxPreviewRadius.yPosition = getControlY(10);
            tfServerMaxPreviewLimit.yPosition = getControlY(11);
            tfChainIdleTimeout.yPosition = getControlY(20);
            tfChainIdleCountdown.yPosition = getControlY(21);
            tfChainCooldownTicks.yPosition = getControlY(22);

            setScrolledButtonY(BTN_SERVER_DROP_TO_PLAYER, getControlY(12));
            setScrolledButtonY(BTN_SERVER_DROP_IMMEDIATELY, getControlY(13));
            setScrolledButtonY(BTN_SERVER_USE_PREVIEW, getControlY(14));
            setScrolledButtonY(BTN_SERVER_ENABLE_CACHED_CHAIN, getControlY(15));
            setScrolledButtonY(BTN_SERVER_SUPPRESS_HODGEPODGE_WARNINGS, getControlY(16));
            setScrolledButtonY(BTN_SERVER_ENABLE_CHAIN_CHUNK_LOADING, getControlY(17));
            setScrolledButtonY(BTN_SERVER_USE_CHUNK_CACHED_HARVEST, getControlY(18));
            setScrolledButtonY(BTN_SERVER_CRAZY_MODE, getControlY(19));
            setScrolledButtonY(BTN_SERVER_STOP_ON_UNBREAKABLE, getControlY(23));
            setScrolledButtonY(BTN_SERVER_XP_DROP_MODE, getControlY(24));
            setScrolledButtonY(BTN_SERVER_MERGE_XP_ORBS, getControlY(25));

            setScrolledButtonY(BTN_SERVER_FIRE_BREAK_EVENT, getControlY(26));

            tfServerBlockSwapRadius.yPosition = getControlY(27);
            tfServerBlockSwapLimit.yPosition = getControlY(28);
            tfServerBlockSwapAdjacencyRadius.yPosition = getControlY(29);
            setScrolledButtonY(BTN_SERVER_ENABLE_BLOCK_SWAP, getControlY(30));
        }

        // Update per-button visibility: hide when scrolled out of viewport.
        updateTabVisibility();
    }

    @SuppressWarnings("unchecked")
    private void setScrolledButtonY(int id, int y) {
        for (Object obj : buttonList) {
            GuiButton btn = (GuiButton) obj;
            if (btn.id == id) {
                btn.yPosition = y;
                return;
            }
        }
    }

    // ── Field initialisation ──────────────────────────────────────────────────

    private void initClientFields() {
        int fx = guiLeft + FIELD_X;
        tfClientBigRadius = field(fx, contentRowScreenY(0), String.valueOf(Config.clientBigRadius));
        tfClientBlockLimit = field(fx, contentRowScreenY(1), String.valueOf(Config.clientBlockLimit));
        tfClientSmallRadius = field(fx, contentRowScreenY(2), String.valueOf(Config.clientSmallRadius));
        tfClientTunnelWidth = field(fx, contentRowScreenY(3), String.valueOf(Config.clientTunnelWidth));
        tfPreviewBigRadius = field(fx, contentRowScreenY(4), String.valueOf(Config.previewBigRadius));
        tfPreviewBlockLimit = field(fx, contentRowScreenY(5), String.valueOf(Config.previewBlockLimit));
        tfClientBlockSwapRadius = field(fx, contentRowScreenY(15), String.valueOf(Config.clientBlockSwapRadius));
        tfClientBlockSwapLimit = field(fx, contentRowScreenY(16), String.valueOf(Config.clientBlockSwapLimit));
        tfClientBlockSwapAdjacencyRadius = field(
            fx,
            contentRowScreenY(17),
            String.valueOf(Config.clientBlockSwapAdjacencyRadius));
    }

    private void initServerFields() {
        int fx = guiLeft + FIELD_X;
        // Use runtime-synced server values for display, not the client's local Config.* fields.
        // In multiplayer the client's Config.bigRadius/blockLimit/etc. are loaded from the client's
        // local copy of the server config file and are NEVER updated after a GUI save — the server
        // writes to ITS file and syncs these limits back via PacketServerConfig, which stores them
        // in runtimeServerMax* fields. Reading from runtimeServerMax* ensures the GUI always shows
        // the actual server-side value.
        tfServerBigRadius = field(
            fx,
            contentRowScreenY(0),
            String.valueOf(serverValueForDisplay(Config.runtimeServerMaxBigRadius, Config.bigRadius)));
        tfServerBlockLimit = field(
            fx,
            contentRowScreenY(1),
            String.valueOf(serverValueForDisplay(Config.runtimeServerMaxBlockLimit, Config.blockLimit)));
        tfServerSmallRadius = field(
            fx,
            contentRowScreenY(2),
            String.valueOf(serverValueForDisplay(Config.runtimeServerMaxSmallRadius, Config.smallRadius)));
        tfServerTunnelWidth = field(
            fx,
            contentRowScreenY(3),
            String.valueOf(serverValueForDisplay(Config.runtimeServerMaxTunnelWidth, Config.tunnelWidth)));
        tfBreakPerTick = field(fx, contentRowScreenY(4), String.valueOf(Config.breakPerTick));
        tfCachedBreakPerTick = field(fx, contentRowScreenY(5), String.valueOf(Config.cachedBreakPerTick));
        tfSearchWorkerThreads = field(fx, contentRowScreenY(6), String.valueOf(Config.searchWorkerThreads));
        tfAddExhaustion = field(fx, contentRowScreenY(7), String.valueOf(Config.addExhaustion));
        tfMinesweeperCooldown = field(fx, contentRowScreenY(8), String.valueOf(Config.minesweeperProbeCooldownSeconds));
        tfSudokuCooldown = field(fx, contentRowScreenY(9), String.valueOf(Config.sudokuProbeCooldownSeconds));
        tfServerMaxPreviewRadius = field(fx, contentRowScreenY(10), String.valueOf(Config.serverMaxPreviewBigRadius));
        tfServerMaxPreviewLimit = field(fx, contentRowScreenY(11), String.valueOf(Config.serverMaxPreviewBlockLimit));
        tfChainIdleTimeout = field(fx, contentRowScreenY(20), String.valueOf(Config.chainIdleTimeoutSeconds));
        tfChainIdleCountdown = field(fx, contentRowScreenY(21), String.valueOf(Config.chainIdleCountdownSeconds));
        tfChainCooldownTicks = field(fx, contentRowScreenY(22), String.valueOf(Config.chainCooldownTicks));
        tfServerBlockSwapRadius = field(fx, contentRowScreenY(27), String.valueOf(Config.blockSwapRadius));
        tfServerBlockSwapLimit = field(fx, contentRowScreenY(28), String.valueOf(Config.blockSwapLimit));
        tfServerBlockSwapAdjacencyRadius = field(
            fx,
            contentRowScreenY(29),
            String.valueOf(Config.blockSwapAdjacencyRadius));
    }

    private GuiTextField field(int x, int y, String initialText) {
        GuiTextField tf = new GuiTextField(mc.fontRenderer, x, y, FIELD_W, FIELD_H);
        tf.setText(initialText);
        return tf;
    }

    /**
     * Creates an option-toggle button. If the label is multi-line ({@code \n}) the
     * button is rendered compact at the right edge so the full label text can sit
     * beside it without overlapping; otherwise it fills the entire row as both label
     * and control.
     *
     * @param id          button ID
     * @param row         content row index
     * @param labelKey    I18n key used to check for {@code \n}
     * @param fullText    display string for the full-width (single-line) variant
     * @param compactText display string for the compact (multi-line) variant
     */
    private GuiButton newOptionButton(int id, int row, String labelKey, String fullText, String compactText) {
        if (isMultiLineLabel(labelKey)) {
            return new GuiButton(id, guiLeft + FIELD_X, contentRowScreenY(row), FIELD_W, FIELD_H, compactText);
        }
        int bx = guiLeft + LABEL_X;
        int bw = GUI_W - 2 * LABEL_X - SCROLLBAR_W - 2;
        return new GuiButton(id, bx, contentRowScreenY(row), bw, FIELD_H, fullText);
    }

    // ── Draw helpers ──────────────────────────────────────────────────────────

    private void drawClientTab() {
        int lx = guiLeft + LABEL_X;
        int lc = 0xCCCCCC;

        // Mining section — header drawn inside the TOP_PAD area above row 0
        drawSectionHeader(lx, contentRowScreenY(0) - 10, "ezminer.gui.section.mining");
        drawRow(lx, contentRowScreenY(0), lc, "ezminer.config.bigRadius", tfClientBigRadius);
        drawRow(lx, contentRowScreenY(1), lc, "ezminer.config.blockLimit", tfClientBlockLimit);
        drawRow(lx, contentRowScreenY(2), lc, "ezminer.config.smallRadius", tfClientSmallRadius);
        drawRow(lx, contentRowScreenY(3), lc, "ezminer.config.tunnelWidth", tfClientTunnelWidth);

        // Preview section — header drawn inside the SECTION_GAP appended below row 3
        drawSectionHeader(lx, contentRowScreenY(3) + ROW_H + 4, "ezminer.gui.section.preview");
        drawRow(lx, contentRowScreenY(4), lc, "ezminer.config.previewBigRadius", tfPreviewBigRadius);
        drawRow(lx, contentRowScreenY(5), lc, "ezminer.config.previewBlockLimit", tfPreviewBlockLimit);

        // Options section — header inside the SECTION_GAP appended below row 5.
        // The header's own spacer bar replaces the old free-standing separator line.
        drawSectionHeader(lx, contentRowScreenY(5) + ROW_H + 4, "ezminer.gui.section.options");
        drawButtonRowLabel(lx, contentRowScreenY(6), lc, "ezminer.config.usePreview");
        drawButtonRowLabel(lx, contentRowScreenY(7), lc, "ezminer.config.useChainDoneMessage");
        drawButtonRowLabel(lx, contentRowScreenY(8), lc, "ezminer.config.chainActivationMode");
        drawButtonRowLabel(lx, contentRowScreenY(9), lc, "ezminer.config.suppressIngameInfoHud");
        drawButtonRowLabel(lx, contentRowScreenY(10), lc, "ezminer.config.hudAnimationStyle");
        drawButtonRowLabel(lx, contentRowScreenY(11), lc, "ezminer.config.renderStyle");
        drawButtonRowLabel(lx, contentRowScreenY(12), lc, "ezminer.config.blockScrollOnChainKey");
        // Smart Tool Switch section
        drawSectionHeader(lx, contentRowScreenY(12) + ROW_H + 4, "ezminer.gui.section.smartToolSwitch");
        drawButtonRowLabel(lx, contentRowScreenY(13), lc, "ezminer.config.smartToolSwitchEnabled");
        drawButtonRowLabel(lx, contentRowScreenY(14), lc, "ezminer.config.smartToolSwitchActivationMode");

        // Block Swap section
        drawSectionHeader(lx, contentRowScreenY(14) + ROW_H + 4, "ezminer.gui.section.blockSwap");
        drawRow(lx, contentRowScreenY(15), lc, "ezminer.config.blockSwapRadius", tfClientBlockSwapRadius);
        drawRow(lx, contentRowScreenY(16), lc, "ezminer.config.blockSwapLimit", tfClientBlockSwapLimit);
        drawRow(
            lx,
            contentRowScreenY(17),
            lc,
            "ezminer.config.blockSwapAdjacencyRadius",
            tfClientBlockSwapAdjacencyRadius);
    }

    private void drawServerTab() {
        int lx = guiLeft + LABEL_X;
        int lc = 0xCCCCCC;

        drawSectionHeader(lx, contentRowScreenY(0) - 10, "ezminer.gui.section.mining");
        drawRow(lx, contentRowScreenY(0), lc, "ezminer.config.bigRadius", tfServerBigRadius);
        drawRow(lx, contentRowScreenY(1), lc, "ezminer.config.blockLimit", tfServerBlockLimit);
        drawRow(lx, contentRowScreenY(2), lc, "ezminer.config.smallRadius", tfServerSmallRadius);
        drawRow(lx, contentRowScreenY(3), lc, "ezminer.config.tunnelWidth", tfServerTunnelWidth);
        drawRow(lx, contentRowScreenY(4), lc, "ezminer.config.breakPerTick", tfBreakPerTick);
        drawRow(lx, contentRowScreenY(5), lc, "ezminer.config.cachedBreakPerTick", tfCachedBreakPerTick);
        drawRow(lx, contentRowScreenY(6), lc, "ezminer.config.searchWorkerThreads", tfSearchWorkerThreads);
        drawRow(lx, contentRowScreenY(7), lc, "ezminer.config.addExhaustion", tfAddExhaustion);
        drawRow(lx, contentRowScreenY(8), lc, "ezminer.config.minesweeperCooldown", tfMinesweeperCooldown);
        drawRow(lx, contentRowScreenY(9), lc, "ezminer.config.sudokuCooldown", tfSudokuCooldown);

        // Preview section — header inside the SECTION_GAP appended below row 9
        drawSectionHeader(lx, contentRowScreenY(9) + ROW_H + 4, "ezminer.gui.section.preview");
        drawRow(lx, contentRowScreenY(10), lc, "ezminer.config.serverPreviewRadius", tfServerMaxPreviewRadius);
        drawRow(lx, contentRowScreenY(11), lc, "ezminer.config.serverPreviewLimit", tfServerMaxPreviewLimit);

        // Options section — header inside the SECTION_GAP appended below row 11
        drawSectionHeader(lx, contentRowScreenY(11) + ROW_H + 4, "ezminer.gui.section.options");
        drawButtonRowLabel(lx, contentRowScreenY(12), lc, "ezminer.config.dropToPlayer");
        drawButtonRowLabel(lx, contentRowScreenY(13), lc, "ezminer.config.dropImmediately");
        drawButtonRowLabel(lx, contentRowScreenY(14), lc, "ezminer.config.serverUsePreview");
        drawButtonRowLabel(lx, contentRowScreenY(15), lc, "ezminer.config.enableCachedChain");
        drawButtonRowLabel(lx, contentRowScreenY(16), lc, "ezminer.config.suppressHodgepodgeWarnings");
        drawButtonRowLabel(lx, contentRowScreenY(17), lc, "ezminer.config.enableChainChunkLoading");
        drawButtonRowLabel(lx, contentRowScreenY(18), lc, "ezminer.config.useChunkCachedHarvest");
        drawButtonRowLabel(lx, contentRowScreenY(19), lc, "ezminer.config.crazyMode");
        drawRow(lx, contentRowScreenY(20), lc, "ezminer.config.chainIdleTimeoutSeconds", tfChainIdleTimeout);
        drawRow(lx, contentRowScreenY(21), lc, "ezminer.config.chainIdleCountdownSeconds", tfChainIdleCountdown);
        drawRow(lx, contentRowScreenY(22), lc, "ezminer.config.chainCooldownTicks", tfChainCooldownTicks);
        drawButtonRowLabel(lx, contentRowScreenY(23), lc, "ezminer.config.stopOnUnbreakable");
        drawButtonRowLabel(lx, contentRowScreenY(24), lc, "ezminer.config.xpDropMode");
        drawButtonRowLabel(lx, contentRowScreenY(25), lc, "ezminer.config.mergeXPOrbs");
        drawButtonRowLabel(lx, contentRowScreenY(26), lc, "ezminer.config.fireBreakEvent");

        // Block Swap section
        drawSectionHeader(lx, contentRowScreenY(26) + getContentRowHeight(26) + 4, "ezminer.gui.section.blockSwap");
        drawRow(lx, contentRowScreenY(27), lc, "ezminer.config.blockSwapRadius", tfServerBlockSwapRadius);
        drawRow(lx, contentRowScreenY(28), lc, "ezminer.config.blockSwapLimit", tfServerBlockSwapLimit);
        drawRow(
            lx,
            contentRowScreenY(29),
            lc,
            "ezminer.config.blockSwapAdjacencyRadius",
            tfServerBlockSwapAdjacencyRadius);
        drawButtonRowLabel(lx, contentRowScreenY(30), lc, "ezminer.config.enableBlockSwapMode");

    }

    private void drawRow(int labelX, int y, int color, String labelKey, GuiTextField field) {
        String text = resolveNewlines(I18n.format(labelKey));
        String[] lines = text.split("\n", -1);
        int fontH = mc.fontRenderer.FONT_HEIGHT;
        for (int i = 0; i < lines.length; i++) {
            String line = (i == 0 && lines.length == 1) ? lines[i] + ":" : lines[i];
            mc.fontRenderer.drawStringWithShadow(line, labelX, y + 2 + i * (fontH + EXTRA_LINE_SPACING), color);
        }
        field.drawTextBox();
    }

    /**
     * Draws a multi-line label for a button row. For single-line labels this is a
     * no-op — the button's own displayString already shows the text. For multi-line
     * labels (containing {@code \n}), all lines are drawn here and the button only
     * shows the condensed first line.
     */
    private void drawButtonRowLabel(int labelX, int rowY, int color, String labelKey) {
        String text = resolveNewlines(I18n.format(labelKey));
        if (!text.contains("\n")) return; // single-line: button text is sufficient
        String[] lines = text.split("\n", -1);
        int fontH = mc.fontRenderer.FONT_HEIGHT;
        for (int i = 0; i < lines.length; i++) {
            mc.fontRenderer.drawStringWithShadow(lines[i], labelX, rowY + 2 + i * (fontH + EXTRA_LINE_SPACING), color);
        }
    }

    private void drawSectionHeader(int x, int y, String labelKey) {
        String text = "§9§l— §7" + I18n.format(labelKey) + " §9§l—";
        // Subtle spacer strip above the header for visual separation
        int spacerY = y - 1;
        drawRect(x, spacerY, x + GUI_W - 2 * LABEL_X - SCROLLBAR_W - 2, spacerY + 1, 0xFF222244);
        mc.fontRenderer.drawStringWithShadow(text, x, y + 2, 0xFF7788BB);
    }

    private void drawScrollbar() {
        if (totalContentH <= viewportH) return;
        int trackX = guiLeft + GUI_W - SCROLLBAR_W - 2;
        int trackY = viewportTop;
        int trackH = viewportH;
        // Track background
        drawRect(trackX, trackY, trackX + SCROLLBAR_W, trackY + trackH, 0xFF222233);
        // Thumb
        int thumbH = Math.max(12, trackH * viewportH / totalContentH);
        int thumbY = trackY + (trackH - thumbH) * scrollY / Math.max(1, maxScroll());
        drawRect(trackX, thumbY, trackX + SCROLLBAR_W, thumbY + thumbH, 0xFF6688CC);
    }

    // ── Tab visibility / button management ───────────────────────────────────

    @SuppressWarnings("unchecked")
    private void updateTabVisibility() {
        for (Object obj : buttonList) {
            GuiButton btn = (GuiButton) obj;

            boolean isClientContent = btn.id == BTN_USE_PREVIEW || btn.id == BTN_USE_CHAIN_DONE_MSG
                || btn.id == BTN_CHAIN_ACTIVATION_MODE
                || btn.id == BTN_SUPPRESS_INGAME_INFO
                || btn.id == BTN_HUD_ANIM_STYLE
                || btn.id == BTN_RENDER_STYLE
                || btn.id == BTN_BLOCK_SCROLL_ON_CHAIN_KEY
                || btn.id == BTN_SMART_TOOL_SWITCH_ENABLED
                || btn.id == BTN_SMART_TOOL_SWITCH_ACTIVATION_MODE;
            boolean isClientAction = btn.id == BTN_CLIENT_RELOAD || btn.id == BTN_CLIENT_SAVE;
            boolean isServerContent = btn.id == BTN_SERVER_DROP_TO_PLAYER || btn.id == BTN_SERVER_DROP_IMMEDIATELY
                || btn.id == BTN_SERVER_USE_PREVIEW
                || btn.id == BTN_SERVER_ENABLE_CACHED_CHAIN
                || btn.id == BTN_SERVER_SUPPRESS_HODGEPODGE_WARNINGS
                || btn.id == BTN_SERVER_ENABLE_CHAIN_CHUNK_LOADING
                || btn.id == BTN_SERVER_USE_CHUNK_CACHED_HARVEST
                || btn.id == BTN_SERVER_CRAZY_MODE
                || btn.id == BTN_SERVER_STOP_ON_UNBREAKABLE
                || btn.id == BTN_SERVER_XP_DROP_MODE
                || btn.id == BTN_SERVER_MERGE_XP_ORBS
                || btn.id == BTN_SERVER_ENABLE_BLOCK_SWAP
                || btn.id == BTN_SERVER_FIRE_BREAK_EVENT;
            boolean isServerAction = btn.id == BTN_SERVER_RELOAD || btn.id == BTN_SERVER_SAVE;

            if (isClientContent || isClientAction) {
                btn.visible = (activeTab == TAB_CLIENT);
                // Also hide if scrolled out of the viewport
                if (isClientContent && btn.visible) {
                    btn.visible = isInViewport(btn.yPosition);
                }
            }
            if (isServerContent || isServerAction) {
                btn.visible = (activeTab == TAB_SERVER);
                if (isServerContent && btn.visible) {
                    btn.visible = isInViewport(btn.yPosition);
                }
            }
        }
    }

    /** Returns true if a row at screen-Y {@code y} (top edge) is at least partially visible. */
    private boolean isInViewport(int y) {
        return y + FIELD_H > viewportTop && y < viewportBottom;
    }

    // ── Config I/O ────────────────────────────────────────────────────────────

    private void applyAndSaveClientConfig() {
        Config.clientBigRadius = parseI(tfClientBigRadius, Config.clientBigRadius, 0);
        Config.clientBlockLimit = parseI(tfClientBlockLimit, Config.clientBlockLimit, 0);
        Config.clientSmallRadius = parseI(tfClientSmallRadius, Config.clientSmallRadius, 0);
        Config.clientTunnelWidth = parseI(tfClientTunnelWidth, Config.clientTunnelWidth, 0);
        Config.previewBigRadius = parseI(tfPreviewBigRadius, Config.previewBigRadius, 0);
        Config.previewBlockLimit = parseI(tfPreviewBlockLimit, Config.previewBlockLimit, 0);
        Config.clientBlockSwapRadius = parseI(tfClientBlockSwapRadius, Config.clientBlockSwapRadius, 0);
        Config.clientBlockSwapLimit = parseI(tfClientBlockSwapLimit, Config.clientBlockSwapLimit, 0);
        Config.clientBlockSwapAdjacencyRadius = parseI(
            tfClientBlockSwapAdjacencyRadius,
            Config.clientBlockSwapAdjacencyRadius,
            0);
        Config.clampClientMiningToServerCaps();
        Config.clampClientPreviewToServerCaps();
        Config.saveClientConfig();
        EZMiner.network.network.sendToServer(new PacketMinerConfig(Config.buildClientMinerConfigForSync()));
    }

    private void applyAndSaveServerConfig() {
        EZMiner.network.network.sendToServer(
            new PacketSaveServerConfig(
                parseI(tfServerBigRadius, serverValueForDisplay(Config.runtimeServerMaxBigRadius, Config.bigRadius), 0),
                parseI(
                    tfServerBlockLimit,
                    serverValueForDisplay(Config.runtimeServerMaxBlockLimit, Config.blockLimit),
                    0),
                parseI(
                    tfServerSmallRadius,
                    serverValueForDisplay(Config.runtimeServerMaxSmallRadius, Config.smallRadius),
                    0),
                parseI(
                    tfServerTunnelWidth,
                    serverValueForDisplay(Config.runtimeServerMaxTunnelWidth, Config.tunnelWidth),
                    0),
                parseI(tfBreakPerTick, Config.breakPerTick, 1),
                parseI(tfCachedBreakPerTick, Config.cachedBreakPerTick, 1),
                Config.dropImmediately,
                parseD(tfAddExhaustion, Config.addExhaustion),
                Config.dropToPlayer,
                Config.serverUsePreview,
                parseI(tfServerMaxPreviewRadius, Config.serverMaxPreviewBigRadius, 0),
                parseI(tfServerMaxPreviewLimit, Config.serverMaxPreviewBlockLimit, 0),
                parseD(tfMinesweeperCooldown, Config.minesweeperProbeCooldownSeconds),
                parseD(tfSudokuCooldown, Config.sudokuProbeCooldownSeconds),
                Config.enableCachedChain,
                parseI(tfSearchWorkerThreads, Config.searchWorkerThreads, 0),
                Config.suppressHodgepodgeWarnings,
                Config.enableChainChunkLoading,
                Config.useChunkCachedHarvest,
                Config.crazyMode,
                parseI(tfChainIdleTimeout, Config.chainIdleTimeoutSeconds, -1),
                parseI(tfChainIdleCountdown, Config.chainIdleCountdownSeconds, -1),
                Config.stopOnUnbreakable,
                parseI(tfChainCooldownTicks, Config.chainCooldownTicks, 0),
                Config.xpDropMode,
                Config.mergeXPOrbs,
                Config.fireBreakEvent,
                parseI(tfServerBlockSwapRadius, Config.blockSwapRadius, 0),
                parseI(tfServerBlockSwapLimit, Config.blockSwapLimit, 0),
                parseI(tfServerBlockSwapAdjacencyRadius, Config.blockSwapAdjacencyRadius, 0),
                Config.enableBlockSwapMode));
    }

    // ── GL scissor helper ─────────────────────────────────────────────────────

    /** Enables GL scissor test for the given GUI-space rectangle. */
    private void enableScissor(int x, int y, int w, int h) {
        // Use ScaledResolution so the scale factor is always correct,
        // including guiScale = 0 (auto) where manual detection was broken.
        ScaledResolution sr = new ScaledResolution(mc, mc.displayWidth, mc.displayHeight);
        int factor = sr.getScaleFactor();
        int glX = x * factor;
        int glY = mc.displayHeight - (y + h) * factor;
        int glW = Math.max(0, w * factor);
        int glH = Math.max(0, h * factor);
        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        GL11.glScissor(glX, glY, glW, glH);
    }

    // ── Static parse helpers ──────────────────────────────────────────────────

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

    /**
     * Returns the effective server-side value for display in the GUI server tab.
     * If the runtime-synced limit is still at its default sentinel ({@link Integer#MAX_VALUE}),
     * the client-local Config value is used as a fallback (e.g. before the first sync or in
     * single-player where both values match anyway).
     */
    private static int serverValueForDisplay(int runtimeValue, int localFallback) {
        return runtimeValue == Integer.MAX_VALUE ? localFallback : runtimeValue;
    }

    private static String boolLabel(String key, boolean value) {
        return firstLine(I18n.format(key)) + ": " + (value ? "§aON§r" : "§cOFF§r");
    }

    /** Compact display text when label is multi-line, full text otherwise. */
    private static String boolDisplayText(String key, boolean value) {
        return isMultiLineLabel(key) ? boolValue(value) : boolLabel(key, value);
    }

    private static String activationModeLabel() {
        String desc = Config.chainActivationMode == 0 ? I18n.format("ezminer.command.active_mode.desc.0")
            : I18n.format("ezminer.command.active_mode.desc.1");
        return firstLine(I18n.format("ezminer.config.chainActivationMode")) + ": " + desc;
    }

    private static String hudAnimStyleLabel() {
        String style = Config.hudAnimationStyle == 0 ? I18n.format("ezminer.config.hudAnimStyle.rainbow")
            : I18n.format("ezminer.config.hudAnimStyle.wave");
        return firstLine(I18n.format("ezminer.config.hudAnimationStyle")) + ": §e" + style + "§r";
    }

    private static String renderStyleLabel() {
        String style = Config.renderStyle == 0 ? I18n.format("ezminer.config.renderStyle.native")
            : I18n.format("ezminer.config.renderStyle.modern");
        return firstLine(I18n.format("ezminer.config.renderStyle")) + ": §e" + style + "§r";
    }

    private String xpDropModeLabel() {
        return firstLine(I18n.format("ezminer.config.xpDropMode")) + ": "
            + (Config.xpDropMode == 0 ? I18n.format("ezminer.config.xpDropMode.immediate")
                : I18n.format("ezminer.config.xpDropMode.delayed"));
    }

    private String xpDropModeValue() {
        return Config.xpDropMode == 0 ? I18n.format("ezminer.config.xpDropMode.immediate")
            : I18n.format("ezminer.config.xpDropMode.delayed");
    }

    private String xpDropModeDisplayText() {
        return isMultiLineLabel("ezminer.config.xpDropMode") ? xpDropModeValue() : xpDropModeLabel();
    }

    private static String smartToolSwitchActivationModeLabel() {
        String desc = Config.smartToolSwitchActivationMode == 0 ? I18n.format("ezminer.command.active_mode.desc.0")
            : I18n.format("ezminer.command.active_mode.desc.1");
        return firstLine(I18n.format("ezminer.config.smartToolSwitchActivationMode")) + ": " + desc;
    }

    private static String smartToolSwitchActivationModeValue() {
        return Config.smartToolSwitchActivationMode == 0 ? I18n.format("ezminer.command.active_mode.desc.0")
            : I18n.format("ezminer.command.active_mode.desc.1");
    }
}
