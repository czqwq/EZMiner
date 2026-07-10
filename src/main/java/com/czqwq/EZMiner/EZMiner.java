package com.czqwq.EZMiner;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.Filter;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.filter.AbstractFilter;

import com.czqwq.EZMiner.chain.lifecycle.ChainLifecycleService;
import com.czqwq.EZMiner.chain.mode.ChainModeRegistry;
import com.czqwq.EZMiner.chain.mode.ChainSubModeRegistry;
import com.czqwq.EZMiner.chain.planning.ChainPlanningRuntimeFactory;
import com.czqwq.EZMiner.chain.state.ChainStateService;
import com.czqwq.EZMiner.network.NetworkMain;
import com.czqwq.EZMiner.thread.ParallelTick;

import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.SidedProxy;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.event.FMLServerStartedEvent;
import cpw.mods.fml.common.event.FMLServerStartingEvent;
import cpw.mods.fml.common.event.FMLServerStoppingEvent;

@Mod(
    modid = EZMiner.MODID,
    version = Tags.VERSION,
    name = "EZMiner",
    acceptedMinecraftVersions = "[1.7.10]",
    acceptableRemoteVersions = "*")
public class EZMiner {

    public static final String MODID = "EZMiner";
    public static final Logger LOG = LogManager.getLogger(MODID);

    @SidedProxy(clientSide = "com.czqwq.EZMiner.ClientProxy", serverSide = "com.czqwq.EZMiner.CommonProxy")
    public static CommonProxy proxy;

    public static final NetworkMain network = new NetworkMain();
    public static final ParallelTick parallelTick = new ParallelTick();
    public static final ChainStateService chainStateService = new ChainStateService();
    public static final ChainLifecycleService chainLifecycleService = new ChainLifecycleService();
    public static final ChainModeRegistry chainModeRegistry = new ChainModeRegistry();
    public static final ChainSubModeRegistry chainSubModeRegistry = new ChainSubModeRegistry();
    public static final ChainPlanningRuntimeFactory chainPlanningRuntimeFactory = new ChainPlanningRuntimeFactory();

    /** True when the server sends a {@code PacketServerConfig} with {@code isOp=true}. Client-side only. */
    public static volatile boolean clientIsOp = false;

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
        LOG.info("Ciallo～(∠・ω< )⌒★");
        installHodgepodgeLogFilter();
        proxy.postInit(event);
    }

    /** Installs a Log4j2 filter that suppresses Hodgepodge off-thread warnings for EZMiner threads. */
    private static void installHodgepodgeLogFilter() {
        try {
            org.apache.logging.log4j.core.Logger coreLogger = (org.apache.logging.log4j.core.Logger) LogManager
                .getLogger("ServerThreadLongHashMap");
            coreLogger.addFilter(new AbstractFilter() {

                @Override
                public Filter.Result filter(LogEvent event) {
                    if (Config.suppressHodgepodgeWarnings && Thread.currentThread()
                        .getName()
                        .startsWith("EZMiner-")) {
                        return Filter.Result.DENY;
                    }
                    return Filter.Result.NEUTRAL;
                }
            });
            LOG.info("Hodgepodge log filter installed for EZMiner threads");
        } catch (Exception e) {
            LOG.warn("Failed to install Hodgepodge log filter — off-thread warnings will appear in log", e);
        }
    }

    @Mod.EventHandler
    public void serverStarting(FMLServerStartingEvent event) {
        proxy.serverStarting(event);
    }

    @Mod.EventHandler
    public void serverStarted(FMLServerStartedEvent event) {
        proxy.serverStarted(event);
    }

    @Mod.EventHandler
    public void serverStopping(FMLServerStoppingEvent event) {
        proxy.serverStopping(event);
    }
}
