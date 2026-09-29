package com.czqwq.EZMiner;

import java.io.File;

import com.czqwq.EZMiner.chain.mode.ChainModeBootstrap;
import com.czqwq.EZMiner.chain.mode.ChainSubModeBootstrap;
import com.czqwq.EZMiner.command.ReloadConfigCommand;
import com.czqwq.EZMiner.compat.GT5ToolCompat;
import com.czqwq.EZMiner.core.PlayerManager;
import com.czqwq.EZMiner.core.crop.CropAdapterRegistry;
import com.czqwq.EZMiner.permission.ServerOwnerWhitelist;
import com.czqwq.EZMiner.thread.SearchWorkerPool;

import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.event.FMLServerStartedEvent;
import cpw.mods.fml.common.event.FMLServerStartingEvent;
import cpw.mods.fml.common.event.FMLServerStoppingEvent;
import gregtech.api.enums.Mods;

public class CommonProxy {

    public void preInit(FMLPreInitializationEvent event) {
        // Client config: config/EZMiner/EZMiner.cfg
        File clientConfigDir = new File(
            event.getSuggestedConfigurationFile()
                .getParentFile(),
            "EZMiner");
        clientConfigDir.mkdirs();
        // Server config: <game_root>/EZMiner/EZMiner_Server.cfg
        // On a dedicated server this is ./EZMiner/; on a client it is .minecraft/EZMiner/
        File serverConfigDir = new File(
            event.getSuggestedConfigurationFile()
                .getParentFile()
                .getParentFile(),
            "EZMiner");
        serverConfigDir.mkdirs();
        // Clean up legacy server config that was previously stored alongside the client config.
        // The server config now lives under <game_root>/EZMiner/ instead of config/EZMiner/.
        File legacyServerConfig = new File(clientConfigDir, "EZMiner_Server.cfg");
        if (legacyServerConfig.exists()) {
            if (legacyServerConfig.delete()) {
                EZMiner.LOG.info("Removed legacy server config at {}", legacyServerConfig.getAbsolutePath());
            } else {
                EZMiner.LOG.warn("Failed to remove legacy server config at {}", legacyServerConfig.getAbsolutePath());
            }
        }
        Config.init(new File(clientConfigDir, "EZMiner.cfg"), new File(serverConfigDir, "EZMiner_Server.cfg"));
        Config.register();
        // Initialise the optional-mod compat bridge on BOTH sides. The GT tool bridge feeds
        // ToolHarvestEligibility, which runs on the dedicated server; with only the client
        // call, gtLoaded stayed false there and tool eligibility fell through to
        // ForgeHooks.canToolHarvestBlock (which accepts a GT wrench for stone).
        if (Mods.GregTech.isModLoaded()) {
            GT5ToolCompat.init();
        }
        // Qz-Miner is a sibling 1.7.10 chain miner whose default chain key is also
        // Keyboard.KEY_GRAVE. With both mods installed, holding `~` starts both miners on the
        // same break event. EZMiner has no reliable way to read Qz-Miner's activation state,
        // so it does not yield; warn once and let the user rebind one of the two keys.
        if (Loader.isModLoaded("qz_miner")) {
            EZMiner.LOG.warn(
                "Qz-Miner is installed and EZMiner's default chain key ({}) collides with its default "
                    + "chain key. Hold one key and BOTH miners will start on the same block. Rebind the chain "
                    + "key of either mod to avoid double-mining.",
                "~");
        }
        EZMiner.network.registry();
        ChainModeBootstrap.bootstrap(EZMiner.chainModeRegistry);
        ChainSubModeBootstrap.bootstrap(EZMiner.chainSubModeRegistry);
        new TickEventHandler().registry();
    }

    public void init(FMLInitializationEvent event) {}

    public void postInit(FMLPostInitializationEvent event) {
        CropAdapterRegistry.init();
    }

    public void serverStarting(FMLServerStartingEvent event) {
        // Defensive: a JVM-internal world reload (/reload, back to the main menu and into a
        // new world) keeps EZMiner.parallelTick alive as a static singleton. Clear any stale
        // founders so a dead task cannot be unpaused against the previous world.
        EZMiner.parallelTick.clearAllTasks();
        PlayerManager.instance = new PlayerManager();
        PlayerManager.instance.registry();
        event.registerServerCommand(new ReloadConfigCommand());
    }

    public void serverStarted(FMLServerStartedEvent event) {
        SearchWorkerPool.start(Config.searchWorkerThreads);
        ServerOwnerWhitelist.init(
            net.minecraft.server.MinecraftServer.getServer().worldServers[0].getSaveHandler()
                .getWorldDirectory());
    }

    public void serverStopping(FMLServerStoppingEvent event) {
        SearchWorkerPool.stop();
        // Stale founders must not survive into the next world: ParallelTick only prunes
        // stopped tasks at tick END, so a still-started task would be unpaused at the first
        // tick START of the new world and could dispatch into a shut-down SearchWorkerPool.
        EZMiner.parallelTick.clearAllTasks();
    }
}
