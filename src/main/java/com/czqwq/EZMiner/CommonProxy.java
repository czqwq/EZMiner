package com.czqwq.EZMiner;

import java.io.File;

import com.czqwq.EZMiner.command.ReloadConfigCommand;
import com.czqwq.EZMiner.core.PlayerManager;

import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.event.FMLServerStartingEvent;

public class CommonProxy {

    public void preInit(FMLPreInitializationEvent event) {
        // Put config into config/EZMiner/EZMiner.cfg instead of the default config root
        File configDir = new File(
            event.getSuggestedConfigurationFile()
                .getParentFile(),
            "EZMiner");
        configDir.mkdirs();
        Config.init(new File(configDir, "EZMiner.cfg"));
        Config.register();
        EZMiner.network.registry();
        new TickEventHandler().registry();
    }

    public void init(FMLInitializationEvent event) {}

    public void postInit(FMLPostInitializationEvent event) {}

    public void serverStarting(FMLServerStartingEvent event) {
        PlayerManager.instance = new PlayerManager();
        PlayerManager.instance.registry();
        event.registerServerCommand(new ReloadConfigCommand());
    }
}
