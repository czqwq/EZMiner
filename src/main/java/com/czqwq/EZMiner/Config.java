package com.czqwq.EZMiner;

import java.io.File;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.config.Configuration;

import com.czqwq.EZMiner.core.MinerConfig;

import cpw.mods.fml.client.event.ConfigChangedEvent;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;

public class Config {

    public static Configuration clientConfiguration;
    public static Configuration serverConfiguration;

    // ===== Server-side authoritative settings =====
    public static int bigRadius = 8;
    public static int blockLimit = 1024;
    public static int smallRadius = 2;
    public static int tunnelWidth = 1;
    public static double addExhaustion = 0.025;
    public static boolean dropToPlayer = true;
    public static boolean serverUsePreview = true;
    public static int serverMaxPreviewBigRadius = 8;
    public static int serverMaxPreviewBlockLimit = 1024;
    /**
     * Maximum number of blocks broken per server tick during a chain operation.
     * Lower values reduce per-tick light-propagation and entity-tracking load;
     * higher values complete the chain faster. Hard cap: 64.
     */
    public static int breakPerTick = 16;
    /**
     * Maximum number of blocks broken per server tick during <strong>cached</strong>
     * chain operations. Cached chains have no background founder search overhead,
     * so a higher rate (default 64) matches Bandit's throughput without increasing
     * per-tick load beyond what the server already handles.
     * Hard cap: 64.
     */
    public static int cachedBreakPerTick = 64;
    /** Spawn drops immediately per-block instead of batching at chain end (avoids lag spike). */
    public static boolean dropImmediately = false;
    /** Enable cached chain sub-modes (2/3). WIP experimental feature — enable at own risk. */
    public static boolean enableCachedChain = false;
    /**
     * Cooldown in seconds between successive minesweeper auto-flag operations
     * when the chain key is held in Special / Minesweeper mode. Minimum: 0.1 s (2 ticks).
     */
    public static double minesweeperProbeCooldownSeconds = 5.0;
    /**
     * Cooldown in seconds between successive Sudoku auto-fill operations
     * when the chain key is held in Special / Sudoku mode. Minimum: 0.1 s (2 ticks).
     */
    public static double sudokuProbeCooldownSeconds = 5.0;

    /** Remove Fortune III cap for GT/BW ore drops. Mixin-based — requires game restart. */
    public static boolean enableUnlimitedOreFortune = false;

    /** Max Fortune level for GT/BW ores (clamped to 255). Mixin-based — requires game restart. */
    public static int maxFortuneLevel = 3;

    /**
     * When {@code true}, ores that were placed by a player (i.e. not naturally generated) are
     * treated as naturally generated and therefore also benefit from the Fortune enchantment bonus when
     * {@link #enableUnlimitedOreFortune} is {@code true}.
     *
     * <p>
     * <strong>Like {@link #enableUnlimitedOreFortune}, this value is read once at startup via
     * Mixin and cannot be changed without restarting the game.</strong>
     *
     * <p>
     * Default: {@code false}.
     */
    public static boolean enableFortuneForPlacedOre = false;

    // ===== Client-side settings =====
    public static final String CLIENT_CATEGORY = "Client";
    public static boolean usePreview = true;
    public static boolean useChainDoneMessage = true;
    public static int hudPosX = 5;
    public static int hudPosY = 5;
    /** Client preferred chain radius (capped by server at runtime). */
    public static int clientBigRadius = 8;
    /** Client preferred chain block limit (capped by server at runtime). */
    public static int clientBlockLimit = 1024;
    /** Client preferred adjacency radius (capped by server at runtime). */
    public static int clientSmallRadius = 2;
    /** Client preferred tunnel width (capped by server at runtime). */
    public static int clientTunnelWidth = 1;
    /**
     * Maximum search radius used by the client-side preview renderer.
     * Independent of the server's {@code bigRadius}: the preview can use a smaller value
     * to keep frame-rate smooth on large ore veins.
     */
    public static int previewBigRadius = 8;
    /**
     * Maximum number of blocks shown in the client-side preview.
     * A lower limit makes the preview search finish faster and keeps GPU vertex data small.
     */
    public static int previewBlockLimit = 256;
    // ===== Runtime server overrides (client memory only) =====
    public static int runtimeServerMaxBigRadius = Integer.MAX_VALUE;
    public static int runtimeServerMaxBlockLimit = Integer.MAX_VALUE;
    public static int runtimeServerMaxSmallRadius = Integer.MAX_VALUE;
    public static int runtimeServerMaxTunnelWidth = Integer.MAX_VALUE;
    public static int runtimeServerMaxPreviewBigRadius = Integer.MAX_VALUE;
    public static int runtimeServerMaxPreviewBlockLimit = Integer.MAX_VALUE;
    public static boolean runtimeServerUsePreview = true;
    /** Chain key mode: 0 = Hold (default), 1 = Toggle. */
    public static int chainActivationMode = 0;
    /**
     * When {@code true} and InGame Info XML is installed, EZMiner will temporarily set
     * {@code ConfigurationHandler.showHUD = false} while its own HUD is visible (i.e. while
     * the chain key is held), and restore the original value when the key is released.
     * This prevents the two HUDs from overlapping on screen.
     */
    public static boolean suppressIngameInfoHud = false;

    /** HUD brand animation: 0 = Rainbow Bounce, 1 = Wave Highlight. */
    public static int hudAnimationStyle = 0;

    /** Preview render style: 0 = Native wireframe, 1 = Modern two-pass (Ultimine style). */
    public static int renderStyle = 0;
    /** Block hotbar scrolling while chain key held (reserved for sub-mode switching). */
    public static boolean blockScrollOnChainKey = true;

    /** Master switch for Smart Tool Switching. */
    public static boolean smartToolSwitchEnabled = true;

    /** Smart Tool Switch mode: 0 = Hold, 1 = Toggle (default). */
    public static int smartToolSwitchActivationMode = 1;

    private static final String BLOCK_SCROLL_ON_CHAIN_KEY_COMMENT = "When true, mouse-wheel scrolling is blocked from changing the inventory hotbar slot while the chain key is held, so that scrolling is reserved for switching EZMiner sub-modes.";

    private static final String SMART_TOOL_SWITCH_ENABLED_COMMENT = "Master switch for Smart Tool Switching. When false, the feature is completely disabled.";

    private static final String SMART_TOOL_SWITCH_ACTIVATION_MODE_COMMENT = "Smart Tool Switch key activation mode. "
        + "0 = Hold (tool switching active only while key is held). "
        + "1 = Toggle (press once to activate, press again to deactivate, default).";

    private static final String RENDER_STYLE_COMMENT = "Preview outline rendering style. "
        + "0 = Native (single-pass wireframe, original). "
        + "1 = Modern (two-pass: solid visible lines + translucent hidden lines, FTB-Ultimine inspired).";

    private static final String HUD_ANIMATION_STYLE_COMMENT = "HUD brand animation style. 0 = Rainbow Bounce (original). 1 = Wave Highlight (letters light up one-by-one then fill left-to-right).";

    private static final String CHAIN_ACTIVATION_MODE_COMMENT = "Controls how the chain key activates mining. "
        + "0 = Hold (keep the key held to keep mining, release to stop). "
        + "1 = Toggle (press once to start, press again to stop).";
    private static final String SUPPRESS_INGAMEINFO_HUD_COMMENT = "When true and InGame Info XML is installed, EZMiner will temporarily hide the InGame Info XML HUD "
        + "while its own HUD is visible (chain key held), then restore it when the key is released. "
        + "Prevents the two HUDs from overlapping. Default: false.";

    public static void init(File clientConfigFile, File serverConfigFile) {
        if (clientConfiguration == null) {
            clientConfiguration = new Configuration(clientConfigFile);
        }
        if (serverConfiguration == null) {
            serverConfiguration = new Configuration(serverConfigFile);
        }
        load();
    }

    public static void load() {
        if (serverConfiguration != null) {
            loadServerOnlyInternal();
            if (FMLCommonHandler.instance()
                .getSide()
                .isServer()) {
                runtimeServerMaxBigRadius = bigRadius;
                runtimeServerMaxBlockLimit = blockLimit;
                runtimeServerMaxSmallRadius = smallRadius;
                runtimeServerMaxTunnelWidth = tunnelWidth;
                runtimeServerMaxPreviewBigRadius = serverMaxPreviewBigRadius;
                runtimeServerMaxPreviewBlockLimit = serverMaxPreviewBlockLimit;
                runtimeServerUsePreview = serverUsePreview;
            }
            if (serverConfiguration.hasChanged()) {
                serverConfiguration.save();
            }
        }
        if (clientConfiguration != null) {
            loadClientOnlyInternal();
            clampClientMiningToServerCaps();
            clampClientPreviewToServerCaps();
            if (clientConfiguration.hasChanged()) {
                clientConfiguration.save();
            }
        }
    }

    private static void loadServerOnlyInternal() {
        serverConfiguration.load();
        bigRadius = serverConfiguration.getInt(
            "bigRadius",
            Configuration.CATEGORY_GENERAL,
            8,
            0,
            Integer.MAX_VALUE,
            "Maximum radius (in blocks) for chain and blast operations.");
        blockLimit = serverConfiguration.getInt(
            "blockLimit",
            Configuration.CATEGORY_GENERAL,
            1024,
            0,
            Integer.MAX_VALUE,
            "Maximum number of blocks that can be harvested in a single chain operation.");
        smallRadius = serverConfiguration.getInt(
            "smallRadius",
            Configuration.CATEGORY_GENERAL,
            2,
            0,
            Integer.MAX_VALUE,
            "Adjacency detection radius for chain mode. Two ore blocks are considered connected "
                + "if they are within this many blocks of each other.");
        tunnelWidth = serverConfiguration.getInt(
            "tunnelWidth",
            Configuration.CATEGORY_GENERAL,
            1,
            0,
            Integer.MAX_VALUE,
            "Half-width of the tunnel dug by Tunnel blast sub-mode (0 = 1-block wide, 1 = 3-block wide, etc.).");
        breakPerTick = serverConfiguration.getInt(
            "breakPerTick",
            Configuration.CATEGORY_GENERAL,
            16,
            1,
            64,
            "Maximum blocks broken per server tick during a chain operation. "
                + "Lower values reduce light-update lag on large veins (recommended: 16). "
                + "Hard cap: 64.");
        cachedBreakPerTick = serverConfiguration.getInt(
            "cachedBreakPerTick",
            Configuration.CATEGORY_GENERAL,
            64,
            1,
            64,
            "Maximum blocks broken per server tick during a cached chain operation. "
                + "Cached chains have no background founder overhead, so a higher rate "
                + "(default 64) is safe. Hard cap: 64.");
        dropImmediately = serverConfiguration.getBoolean(
            "dropImmediately",
            Configuration.CATEGORY_GENERAL,
            false,
            "When true, drops are spawned immediately after each block harvest during a "
                + "chain, instead of being batched and flushed at the end. "
                + "Eliminates the end-of-chain lag spike on large veins.");
        enableCachedChain = serverConfiguration.getBoolean(
            "enableCachedChain",
            Configuration.CATEGORY_GENERAL,
            false,
            "WIP — When true, the cached chain sub-modes appear in the chain mode cycle. "
                + "Cached chain pre-calculates block positions on the server thread and "
                + "feeds them to the operator without a background founder. "
                + "May have bugs with certain modded ores. Default: false.");
        addExhaustion = serverConfiguration
            .get(
                Configuration.CATEGORY_GENERAL,
                "addExhaustion",
                0.025,
                "Food exhaustion added to the player for each block mined during a chain operation. "
                    + "Negative values restore food.",
                -Double.MAX_VALUE,
                Double.MAX_VALUE)
            .getDouble();
        dropToPlayer = serverConfiguration.getBoolean(
            "dropToPlayer",
            Configuration.CATEGORY_GENERAL,
            true,
            "Controls where batched drops are spawned after a chain operation. "
                + "true = at the player's current feet position (default). "
                + "false = at the center of the first block that was mined.");
        serverUsePreview = serverConfiguration.getBoolean(
            "serverUsePreview",
            Configuration.CATEGORY_GENERAL,
            true,
            "Server global preview switch. false disables preview for all clients.");
        serverMaxPreviewBigRadius = serverConfiguration.getInt(
            "serverMaxPreviewBigRadius",
            Configuration.CATEGORY_GENERAL,
            8,
            0,
            Integer.MAX_VALUE,
            "Server-side maximum allowed preview radius.");
        serverMaxPreviewBlockLimit = serverConfiguration.getInt(
            "serverMaxPreviewBlockLimit",
            Configuration.CATEGORY_GENERAL,
            1024,
            0,
            Integer.MAX_VALUE,
            "Server-side maximum allowed preview block count.");
        minesweeperProbeCooldownSeconds = serverConfiguration
            .get(
                "minesweeperProbeCooldownSeconds",
                Configuration.CATEGORY_GENERAL,
                5.0,
                "Cooldown in seconds between successive auto-flag operations in Special / Minesweeper mode "
                    + "(while the chain key is held). Minimum: 0.1. Default: 5.",
                0.1,
                3600.0)
            .getDouble();
        sudokuProbeCooldownSeconds = serverConfiguration
            .get(
                "sudokuProbeCooldownSeconds",
                Configuration.CATEGORY_GENERAL,
                5.0,
                "Cooldown in seconds between successive auto-fill operations in Special / Sudoku mode "
                    + "(while the chain key is held). Minimum: 0.1. Default: 5.",
                0.1,
                3600.0)
            .getDouble();
        enableUnlimitedOreFortune = serverConfiguration.getBoolean(
            "enableUnlimitedOreFortune",
            Configuration.CATEGORY_GENERAL,
            false,
            "When true, removes the Fortune III cap for GT / BartWorks ore drops so that Fortune levels "
                + "above III yield additional drops. "
                + "IMPORTANT: This option is implemented via Mixin and is applied once at JVM startup. "
                + "It CANNOT be changed via /EZMiner reloadConfig — a full game restart is required. "
                + "Default: false.");
        maxFortuneLevel = serverConfiguration.getInt(
            "maxFortuneLevel",
            Configuration.CATEGORY_GENERAL,
            3,
            3,
            255,
            "Maximum Fortune enchantment level that GT / BartWorks ores will respond to when "
                + "enableUnlimitedOreFortune is true. "
                + "IMPORTANT: This option is implemented via Mixin and is applied once at JVM startup. "
                + "It CANNOT be changed via /EZMiner reloadConfig — a full game restart is required. "
                + "Default: 3 (no cap extension).");
        enableFortuneForPlacedOre = serverConfiguration.getBoolean(
            "enableFortuneForPlacedOre",
            Configuration.CATEGORY_GENERAL,
            false,
            "When true, ores placed by players (not naturally generated) are treated as natural ores "
                + "and also benefit from the Fortune bonus when enableUnlimitedOreFortune is true. "
                + "IMPORTANT: This option is implemented via Mixin and is applied once at JVM startup. "
                + "It CANNOT be changed via /EZMiner reloadConfig — a full game restart is required. "
                + "Default: false.");
    }

    public static void applyServerRuntimeLimits(int maxBigRadius, int maxBlockLimit, int maxSmallRadius,
        int maxTunnelWidth, int maxPreviewBigRadius, int maxPreviewBlockLimit, boolean allowPreview,
        int syncedBreakPerTick) {
        runtimeServerMaxBigRadius = Math.max(0, maxBigRadius);
        runtimeServerMaxBlockLimit = Math.max(0, maxBlockLimit);
        runtimeServerMaxSmallRadius = Math.max(0, maxSmallRadius);
        runtimeServerMaxTunnelWidth = Math.max(0, maxTunnelWidth);
        runtimeServerMaxPreviewBigRadius = Math.max(0, maxPreviewBigRadius);
        runtimeServerMaxPreviewBlockLimit = Math.max(0, maxPreviewBlockLimit);
        runtimeServerUsePreview = allowPreview;
        breakPerTick = Math.max(1, syncedBreakPerTick);
        // Keep server fields mirrored on client memory for compatibility in existing reads.
        serverMaxPreviewBigRadius = Math.max(0, maxPreviewBigRadius);
        serverMaxPreviewBlockLimit = Math.max(0, maxPreviewBlockLimit);
        serverUsePreview = allowPreview;
        clampClientMiningToServerCaps();
        clampClientPreviewToServerCaps();
    }

    /**
     * Updates {@link #chainActivationMode} in memory and writes only that value
     * back to the config file immediately, without touching any other entries.
     *
     * @param mode 0 = hold to activate; 1 = click to toggle
     */
    public static void saveChainActivationMode(int mode) {
        chainActivationMode = mode;
        if (clientConfiguration == null) return;
        clientConfiguration.get(CLIENT_CATEGORY, "chainActivationMode", 0, CHAIN_ACTIVATION_MODE_COMMENT, 0, 1)
            .set(mode);
        clientConfiguration.save();
    }

    public static void saveHudPos(int x, int y) {
        hudPosX = x;
        hudPosY = y;
        if (clientConfiguration == null) return;
        clientConfiguration.get(CLIENT_CATEGORY, "hudPosX", 5)
            .set(x);
        clientConfiguration.get(CLIENT_CATEGORY, "hudPosY", 5)
            .set(y);
        clientConfiguration.save();
    }

    /** Reload only client-side options from disk. */
    public static void reloadClientOnly() {
        if (clientConfiguration == null) return;
        clientConfiguration.load();
        loadClientOnlyInternal();
        clampClientMiningToServerCaps();
        clampClientPreviewToServerCaps();
        if (clientConfiguration.hasChanged()) {
            clientConfiguration.save();
        }
    }

    public static void clearServerRuntimeOverridesAndReloadClient() {
        runtimeServerMaxBigRadius = Integer.MAX_VALUE;
        runtimeServerMaxBlockLimit = Integer.MAX_VALUE;
        runtimeServerMaxSmallRadius = Integer.MAX_VALUE;
        runtimeServerMaxTunnelWidth = Integer.MAX_VALUE;
        runtimeServerMaxPreviewBigRadius = Integer.MAX_VALUE;
        runtimeServerMaxPreviewBlockLimit = Integer.MAX_VALUE;
        runtimeServerUsePreview = true;
        reloadClientOnly();
    }

    public static MinerConfig buildClientMinerConfigForSync() {
        MinerConfig cfg = new MinerConfig();
        cfg.bigRadius = Math.max(0, Math.min(clientBigRadius, runtimeServerMaxBigRadius));
        cfg.blockLimit = Math.max(0, Math.min(clientBlockLimit, runtimeServerMaxBlockLimit));
        cfg.smallRadius = Math.max(0, Math.min(clientSmallRadius, runtimeServerMaxSmallRadius));
        cfg.tunnelWidth = Math.max(0, Math.min(clientTunnelWidth, runtimeServerMaxTunnelWidth));
        cfg.useChainDoneMessage = useChainDoneMessage;
        return cfg;
    }

    public static boolean isPreviewEnabled() {
        return usePreview && runtimeServerUsePreview;
    }

    public static void clampClientMiningToServerCaps() {
        clientBigRadius = Math.max(0, Math.min(clientBigRadius, runtimeServerMaxBigRadius));
        clientBlockLimit = Math.max(0, Math.min(clientBlockLimit, runtimeServerMaxBlockLimit));
        clientSmallRadius = Math.max(0, Math.min(clientSmallRadius, runtimeServerMaxSmallRadius));
        clientTunnelWidth = Math.max(0, Math.min(clientTunnelWidth, runtimeServerMaxTunnelWidth));
    }

    public static void clampClientPreviewToServerCaps() {
        previewBigRadius = Math.max(0, Math.min(previewBigRadius, runtimeServerMaxPreviewBigRadius));
        previewBlockLimit = Math.max(0, Math.min(previewBlockLimit, runtimeServerMaxPreviewBlockLimit));
    }

    private static void loadClientOnlyInternal() {
        if (clientConfiguration == null) return;
        usePreview = clientConfiguration.getBoolean(
            "usePreview",
            CLIENT_CATEGORY,
            true,
            "When true, block outlines are rendered around all blocks that would be included in the "
                + "current chain while the chain key is held.");
        useChainDoneMessage = clientConfiguration.getBoolean(
            "useChainDoneMessage",
            CLIENT_CATEGORY,
            true,
            "When true, a summary message is shown in chat after each chain operation finishes, "
                + "reporting the number of blocks mined and the time taken.");
        clientBigRadius = clientConfiguration.getInt(
            "clientBigRadius",
            CLIENT_CATEGORY,
            8,
            0,
            Integer.MAX_VALUE,
            "Client preferred chain radius. Effective value is clamped by server max.");
        clientBlockLimit = clientConfiguration.getInt(
            "clientBlockLimit",
            CLIENT_CATEGORY,
            1024,
            0,
            Integer.MAX_VALUE,
            "Client preferred chain block limit. Effective value is clamped by server max.");
        clientSmallRadius = clientConfiguration.getInt(
            "clientSmallRadius",
            CLIENT_CATEGORY,
            2,
            0,
            Integer.MAX_VALUE,
            "Client preferred chain adjacency radius. Effective value is clamped by server max.");
        clientTunnelWidth = clientConfiguration.getInt(
            "clientTunnelWidth",
            CLIENT_CATEGORY,
            1,
            0,
            Integer.MAX_VALUE,
            "Client preferred tunnel width. Effective value is clamped by server max.");
        previewBigRadius = clientConfiguration.getInt(
            "previewBigRadius",
            CLIENT_CATEGORY,
            8,
            0,
            Integer.MAX_VALUE,
            "Maximum search radius for the client-side block-outline preview. "
                + "Effective value is clamped by server max preview radius.");
        previewBlockLimit = clientConfiguration.getInt(
            "previewBlockLimit",
            CLIENT_CATEGORY,
            256,
            0,
            Integer.MAX_VALUE,
            "Maximum number of blocks shown in the client-side block-outline preview. "
                + "Effective value is clamped by server max preview block limit.");
        hudPosX = clientConfiguration.getInt(
            "hudPosX",
            CLIENT_CATEGORY,
            5,
            Integer.MIN_VALUE,
            Integer.MAX_VALUE,
            "HUD X position in screen pixels (origin at top-left).");
        hudPosY = clientConfiguration.getInt(
            "hudPosY",
            CLIENT_CATEGORY,
            5,
            Integer.MIN_VALUE,
            Integer.MAX_VALUE,
            "HUD Y position in screen pixels (origin at top-left).");
        chainActivationMode = clientConfiguration
            .getInt("chainActivationMode", CLIENT_CATEGORY, 0, 0, 1, CHAIN_ACTIVATION_MODE_COMMENT);
        suppressIngameInfoHud = clientConfiguration
            .getBoolean("suppressIngameInfoHud", CLIENT_CATEGORY, false, SUPPRESS_INGAMEINFO_HUD_COMMENT);
        hudAnimationStyle = clientConfiguration
            .getInt("hudAnimationStyle", CLIENT_CATEGORY, 0, 0, 1, HUD_ANIMATION_STYLE_COMMENT);
        renderStyle = clientConfiguration.getInt("renderStyle", CLIENT_CATEGORY, 0, 0, 1, RENDER_STYLE_COMMENT);
        blockScrollOnChainKey = clientConfiguration
            .getBoolean("blockScrollOnChainKey", CLIENT_CATEGORY, true, BLOCK_SCROLL_ON_CHAIN_KEY_COMMENT);
        smartToolSwitchEnabled = clientConfiguration
            .getBoolean("smartToolSwitchEnabled", CLIENT_CATEGORY, true, SMART_TOOL_SWITCH_ENABLED_COMMENT);
        smartToolSwitchActivationMode = clientConfiguration.getInt(
            "smartToolSwitchActivationMode",
            CLIENT_CATEGORY,
            1,
            0,
            1,
            SMART_TOOL_SWITCH_ACTIVATION_MODE_COMMENT);
    }

    public static void setLegacyServerPreviewCaps(int maxBigRadius, int maxBlockLimit) {
        runtimeServerMaxPreviewBigRadius = Math.max(0, maxBigRadius);
        runtimeServerMaxPreviewBlockLimit = Math.max(0, maxBlockLimit);
        clampClientPreviewToServerCaps();
    }

    @SubscribeEvent
    public void onConfigChanged(ConfigChangedEvent event) {
        if (!event.modID.equalsIgnoreCase(EZMiner.MODID)) return;
        EZMiner.LOG.info("Config change event triggered, reloading...");
        reloadClientOnly();
    }

    public static void register() {
        Config instance = new Config();
        MinecraftForge.EVENT_BUS.register(instance);
        FMLCommonHandler.instance()
            .bus()
            .register(instance);
    }

    /** Writes all current server-side config values to the server config file. */
    public static void saveServerConfig() {
        if (serverConfiguration == null) return;
        serverConfiguration.get(Configuration.CATEGORY_GENERAL, "bigRadius", 8)
            .set(bigRadius);
        serverConfiguration.get(Configuration.CATEGORY_GENERAL, "blockLimit", 1024)
            .set(blockLimit);
        serverConfiguration.get(Configuration.CATEGORY_GENERAL, "smallRadius", 2)
            .set(smallRadius);
        serverConfiguration.get(Configuration.CATEGORY_GENERAL, "tunnelWidth", 1)
            .set(tunnelWidth);
        serverConfiguration.get(Configuration.CATEGORY_GENERAL, "breakPerTick", 16)
            .set(breakPerTick);
        serverConfiguration.get(Configuration.CATEGORY_GENERAL, "cachedBreakPerTick", 64)
            .set(cachedBreakPerTick);
        serverConfiguration.get(Configuration.CATEGORY_GENERAL, "dropImmediately", false)
            .set(dropImmediately);
        serverConfiguration.get(Configuration.CATEGORY_GENERAL, "enableCachedChain", false)
            .set(enableCachedChain);
        serverConfiguration.get(Configuration.CATEGORY_GENERAL, "addExhaustion", 0.025)
            .set(addExhaustion);
        serverConfiguration.get(Configuration.CATEGORY_GENERAL, "dropToPlayer", true)
            .set(dropToPlayer);
        serverConfiguration.get(Configuration.CATEGORY_GENERAL, "serverUsePreview", true)
            .set(serverUsePreview);
        serverConfiguration.get(Configuration.CATEGORY_GENERAL, "serverMaxPreviewBigRadius", 8)
            .set(serverMaxPreviewBigRadius);
        serverConfiguration.get(Configuration.CATEGORY_GENERAL, "serverMaxPreviewBlockLimit", 1024)
            .set(serverMaxPreviewBlockLimit);
        serverConfiguration.get(Configuration.CATEGORY_GENERAL, "minesweeperProbeCooldownSeconds", 5.0)
            .set(minesweeperProbeCooldownSeconds);
        serverConfiguration.get(Configuration.CATEGORY_GENERAL, "sudokuProbeCooldownSeconds", 5.0)
            .set(sudokuProbeCooldownSeconds);
        serverConfiguration.save();
    }

    /** Writes all current client-side config values to the client config file. */
    public static void saveClientConfig() {
        if (clientConfiguration == null) return;
        clientConfiguration.get(CLIENT_CATEGORY, "usePreview", true)
            .set(usePreview);
        clientConfiguration.get(CLIENT_CATEGORY, "useChainDoneMessage", true)
            .set(useChainDoneMessage);
        clientConfiguration.get(CLIENT_CATEGORY, "clientBigRadius", 8)
            .set(clientBigRadius);
        clientConfiguration.get(CLIENT_CATEGORY, "clientBlockLimit", 1024)
            .set(clientBlockLimit);
        clientConfiguration.get(CLIENT_CATEGORY, "clientSmallRadius", 2)
            .set(clientSmallRadius);
        clientConfiguration.get(CLIENT_CATEGORY, "clientTunnelWidth", 1)
            .set(clientTunnelWidth);
        clientConfiguration.get(CLIENT_CATEGORY, "previewBigRadius", 8)
            .set(previewBigRadius);
        clientConfiguration.get(CLIENT_CATEGORY, "previewBlockLimit", 256)
            .set(previewBlockLimit);
        clientConfiguration.get(CLIENT_CATEGORY, "chainActivationMode", 0)
            .set(chainActivationMode);
        clientConfiguration.get(CLIENT_CATEGORY, "suppressIngameInfoHud", false)
            .set(suppressIngameInfoHud);
        clientConfiguration.get(CLIENT_CATEGORY, "hudAnimationStyle", 0)
            .set(hudAnimationStyle);
        clientConfiguration.get(CLIENT_CATEGORY, "renderStyle", 0)
            .set(renderStyle);
        clientConfiguration.get(CLIENT_CATEGORY, "blockScrollOnChainKey", true)
            .set(blockScrollOnChainKey);
        clientConfiguration.get(CLIENT_CATEGORY, "smartToolSwitchEnabled", true)
            .set(smartToolSwitchEnabled);
        clientConfiguration.get(CLIENT_CATEGORY, "smartToolSwitchActivationMode", 1)
            .set(smartToolSwitchActivationMode);
        clientConfiguration.save();
    }
}
