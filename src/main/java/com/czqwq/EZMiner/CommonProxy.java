package com.czqwq.EZMiner;

import java.io.File;

import com.czqwq.EZMiner.chain.mode.ChainModeBootstrap;
import com.czqwq.EZMiner.chain.mode.ChainSubModeBootstrap;
import com.czqwq.EZMiner.command.ReloadConfigCommand;
import com.czqwq.EZMiner.core.PlayerManager;
import com.czqwq.EZMiner.core.crop.CropAdapterRegistry;
import com.czqwq.EZMiner.thread.SearchWorkerPool;

import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.event.FMLServerStartedEvent;
import cpw.mods.fml.common.event.FMLServerStartingEvent;
import cpw.mods.fml.common.event.FMLServerStoppingEvent;

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
        PlayerManager.instance = new PlayerManager();
        PlayerManager.instance.registry();
        event.registerServerCommand(new ReloadConfigCommand());
    }

    public void serverStarted(FMLServerStartedEvent event) {
        SearchWorkerPool.start(Config.searchWorkerThreads);
    }

    public void serverStopping(FMLServerStoppingEvent event) {
        SearchWorkerPool.stop();
    }
}
