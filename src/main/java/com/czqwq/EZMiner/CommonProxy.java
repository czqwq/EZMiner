package com.czqwq.EZMiner;

import com.czqwq.EZMiner.command.ReloadConfigCommand;
import com.czqwq.EZMiner.core.PlayerManager;

import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.event.FMLServerStartingEvent;

public class CommonProxy {

    public void preInit(FMLPreInitializationEvent event) {
        Config.init(event.getSuggestedConfigurationFile());
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
