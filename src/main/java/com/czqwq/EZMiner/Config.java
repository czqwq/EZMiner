package com.czqwq.EZMiner;

import java.io.File;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.config.Configuration;

import cpw.mods.fml.client.event.ConfigChangedEvent;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;

public class Config {

    public static Configuration configuration;

    // ===== Server-side limits (max allowed per-player values) =====
    public static int bigRadius = 8;
    public static int blockLimit = 1024;
    public static int smallRadius = 2;
    public static int tunnelWidth = 1;
    /**
     * Maximum number of blocks broken per server tick during a chain operation.
     * Lower values reduce per-tick light-propagation and entity-tracking load;
     * higher values complete the chain faster. Hard cap: 64.
     */
    public static int breakPerTick = 16;

    // ===== Client-side settings =====
    public static final String CLIENT_CATEGORY = "Client";
    /** Exhaustion added per block mined via chain. Negative values restore food. */
    public static double addExhaustion = 0.025;
    /** When true, batched drops spawn at the player's feet; when false they spawn at the origin block. */
    public static boolean dropToPlayer = true;
    public static boolean usePreview = true;
    public static boolean useChainDoneMessage = true;
    public static int hudPosX = 5;
    public static int hudPosY = 5;
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
    /** Server-authoritative cap for preview radius, synced via PacketServerConfig. */
    public static int serverMaxPreviewBigRadius = 8;
    /** Server-authoritative cap for preview block count, synced via PacketServerConfig. */
    public static int serverMaxPreviewBlockLimit = 1024;
    /**
     * Chain key activation mode.
     * <ul>
     * <li>0 = Hold – keep the key pressed to keep chain active (default).</li>
     * <li>1 = Toggle – press once to start chain, press again to stop.</li>
     * </ul>
     */
    public static int chainActivationMode = 0;

    private static final String CHAIN_ACTIVATION_MODE_COMMENT = "Controls how the chain key activates mining. "
        + "0 = Hold (keep the key held to keep mining, release to stop). "
        + "1 = Toggle (press once to start, press again to stop).";

    public static void init(File configFile) {
        if (configuration == null) {
            configuration = new Configuration(configFile);
        }
        load();
    }

    public static void load() {
        // Re-read from disk so that external edits (including via text editor) are picked up.
        configuration.load();
        bigRadius = configuration.getInt(
            "bigRadius",
            Configuration.CATEGORY_GENERAL,
            8,
            0,
            Integer.MAX_VALUE,
            "Maximum radius (in blocks) for chain and blast operations.");
        blockLimit = configuration.getInt(
            "blockLimit",
            Configuration.CATEGORY_GENERAL,
            1024,
            0,
            Integer.MAX_VALUE,
            "Maximum number of blocks that can be harvested in a single chain operation.");
        smallRadius = configuration.getInt(
            "smallRadius",
            Configuration.CATEGORY_GENERAL,
            2,
            0,
            Integer.MAX_VALUE,
            "Adjacency detection radius for chain mode. Two ore blocks are considered connected "
                + "if they are within this many blocks of each other.");
        tunnelWidth = configuration.getInt(
            "tunnelWidth",
            Configuration.CATEGORY_GENERAL,
            1,
            0,
            Integer.MAX_VALUE,
            "Half-width of the tunnel dug by Tunnel blast sub-mode (0 = 1-block wide, 1 = 3-block wide, etc.).");
        breakPerTick = configuration.getInt(
            "breakPerTick",
            Configuration.CATEGORY_GENERAL,
            16,
            1,
            64,
            "Maximum blocks broken per server tick during a chain operation. "
                + "Lower values reduce light-update lag on large veins (recommended: 16). "
                + "Hard cap: 64.");
        loadClientOnlyInternal();
        // On dedicated server this also defines the caps sent to clients.
        serverMaxPreviewBigRadius = bigRadius;
        serverMaxPreviewBlockLimit = blockLimit;
        clampClientPreviewToServerCaps();
        saveIfChanged();
    }

    /**
     * Updates {@link #chainActivationMode} in memory and writes only that value
     * back to the config file immediately, without touching any other entries.
     *
     * @param mode 0 = hold to activate; 1 = click to toggle
     */
    public static void saveChainActivationMode(int mode) {
        chainActivationMode = mode;
        configuration.get(CLIENT_CATEGORY, "chainActivationMode", 0, CHAIN_ACTIVATION_MODE_COMMENT, 0, 1)
            .set(mode);
        configuration.save();
    }

    public static void saveHudPos(int x, int y) {
        hudPosX = x;
        hudPosY = y;
        configuration.get(CLIENT_CATEGORY, "hudPosX", 5)
            .set(x);
        configuration.get(CLIENT_CATEGORY, "hudPosY", 5)
            .set(y);
        configuration.save();
    }

    /** Reload only client-side options from disk. */
    public static void reloadClientOnly() {
        configuration.load();
        loadClientOnlyInternal();
        clampClientPreviewToServerCaps();
        saveIfChanged();
    }

    public static void setServerPreviewCaps(int maxBigRadius, int maxBlockLimit) {
        serverMaxPreviewBigRadius = Math.max(0, maxBigRadius);
        serverMaxPreviewBlockLimit = Math.max(0, maxBlockLimit);
        clampClientPreviewToServerCaps();
    }

    public static void clampClientPreviewToServerCaps() {
        previewBigRadius = Math.max(0, Math.min(previewBigRadius, serverMaxPreviewBigRadius));
        previewBlockLimit = Math.max(0, Math.min(previewBlockLimit, serverMaxPreviewBlockLimit));
    }

    private static void loadClientOnlyInternal() {
        addExhaustion = configuration
            .get(
                CLIENT_CATEGORY,
                "addExhaustion",
                0.025,
                "Food exhaustion added to the player for each block mined during a chain operation. "
                    + "Set to a negative value to restore food instead.",
                -Double.MAX_VALUE,
                Double.MAX_VALUE)
            .getDouble();
        dropToPlayer = configuration.getBoolean(
            "dropToPlayer",
            CLIENT_CATEGORY,
            true,
            "Controls where batched drops are spawned after a chain operation. "
                + "true = at the player's current feet position (default). "
                + "false = at the center of the first block that was mined.");
        usePreview = configuration.getBoolean(
            "usePreview",
            CLIENT_CATEGORY,
            true,
            "When true, block outlines are rendered around all blocks that would be included in the "
                + "current chain while the chain key is held.");
        useChainDoneMessage = configuration.getBoolean(
            "useChainDoneMessage",
            CLIENT_CATEGORY,
            true,
            "When true, a summary message is shown in chat after each chain operation finishes, "
                + "reporting the number of blocks mined and the time taken.");
        previewBigRadius = configuration.getInt(
            "previewBigRadius",
            CLIENT_CATEGORY,
            8,
            0,
            Integer.MAX_VALUE,
            "Maximum search radius for the client-side block-outline preview. "
                + "Use a smaller value than bigRadius to keep preview snappy on large veins.");
        previewBlockLimit = configuration.getInt(
            "previewBlockLimit",
            CLIENT_CATEGORY,
            256,
            0,
            Integer.MAX_VALUE,
            "Maximum number of blocks shown in the client-side block-outline preview. "
                + "Lower values finish the preview search faster and reduce GPU vertex load.");
        hudPosX = configuration.getInt(
            "hudPosX",
            CLIENT_CATEGORY,
            5,
            Integer.MIN_VALUE,
            Integer.MAX_VALUE,
            "HUD X position in screen pixels (origin at top-left).");
        hudPosY = configuration.getInt(
            "hudPosY",
            CLIENT_CATEGORY,
            5,
            Integer.MIN_VALUE,
            Integer.MAX_VALUE,
            "HUD Y position in screen pixels (origin at top-left).");
        chainActivationMode = configuration
            .getInt("chainActivationMode", CLIENT_CATEGORY, 0, 0, 1, CHAIN_ACTIVATION_MODE_COMMENT);
    }

    private static void saveIfChanged() {
        if (configuration.hasChanged()) {
            configuration.save();
        }
    }

    @SubscribeEvent
    public void onConfigChanged(ConfigChangedEvent event) {
        if (!event.modID.equalsIgnoreCase(EZMiner.MODID)) return;
        EZMiner.LOG.info("Config change event triggered, reloading...");
        load();
    }

    public static void register() {
        Config instance = new Config();
        MinecraftForge.EVENT_BUS.register(instance);
        FMLCommonHandler.instance()
            .bus()
            .register(instance);
    }
}
