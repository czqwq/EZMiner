package com.czqwq.EZMiner;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.czqwq.EZMiner.network.NetworkMain;
import com.czqwq.EZMiner.thread.ParallelTick;

import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.SidedProxy;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.event.FMLServerStartingEvent;

@Mod(modid = EZMiner.MODID, version = Tags.VERSION, name = "EZMiner", acceptedMinecraftVersions = "[1.7.10]")
public class EZMiner {

    public static final String MODID = "EZMiner";
    public static final Logger LOG = LogManager.getLogger(MODID);

    @SidedProxy(clientSide = "com.czqwq.EZMiner.ClientProxy", serverSide = "com.czqwq.EZMiner.CommonProxy")
    public static CommonProxy proxy;

    public static final NetworkMain network = new NetworkMain();
    public static final ParallelTick parallelTick = new ParallelTick();

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        proxy.preInit(event);
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        proxy.init(event);
    }

    @Mod.EventHandler
    public void postInit(FMLPostInitializationEvent event) {
        proxy.postInit(event);
    }

    @Mod.EventHandler
    public void serverStarting(FMLServerStartingEvent event) {
        proxy.serverStarting(event);
    }
}
