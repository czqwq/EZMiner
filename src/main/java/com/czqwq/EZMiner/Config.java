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

    // ===== Client-side settings =====
    public static final String CLIENT_CATEGORY = "Client";
    /** Exhaustion added per block mined via chain. Negative values restore food. */
    public static double addExhaustion = 0.025;
    /** When true, drops are placed into the player's inventory; overflow falls at feet. */
    public static boolean dropToInventory = true;
    public static boolean usePreview = true;
    public static boolean useChainDoneMessage = true;

    public static void init(File configFile) {
        if (configuration == null) {
            configuration = new Configuration(configFile);
        }
        load();
    }

    public static void load() {
        // Re-read from disk so that external edits (including via text editor) are picked up.
        configuration.load();
        bigRadius = configuration
            .getInt("bigRadius", Configuration.CATEGORY_GENERAL, 8, 0, Integer.MAX_VALUE, "ezminer.config.bigRadius");
        blockLimit = configuration.getInt(
            "blockLimit",
            Configuration.CATEGORY_GENERAL,
            1024,
            0,
            Integer.MAX_VALUE,
            "ezminer.config.blockLimit");
        smallRadius = configuration.getInt(
            "smallRadius",
            Configuration.CATEGORY_GENERAL,
            2,
            0,
            Integer.MAX_VALUE,
            "ezminer.config.smallRadius");
        tunnelWidth = configuration.getInt(
            "tunnelWidth",
            Configuration.CATEGORY_GENERAL,
            1,
            0,
            Integer.MAX_VALUE,
            "ezminer.config.tunnelWidth");
        addExhaustion = configuration
            .get(
                CLIENT_CATEGORY,
                "addExhaustion",
                0.025,
                "ezminer.config.addExhaustion",
                -Double.MAX_VALUE,
                Double.MAX_VALUE)
            .getDouble();
        dropToInventory = configuration
            .getBoolean("dropToInventory", CLIENT_CATEGORY, true, "ezminer.config.dropToInventory");
        usePreview = configuration.getBoolean("usePreview", CLIENT_CATEGORY, true, "ezminer.config.usePreview");
        useChainDoneMessage = configuration
            .getBoolean("useChainDoneMessage", CLIENT_CATEGORY, true, "ezminer.config.useChainDoneMessage");

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
