package com.czqwq.EZMiner;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

/**
 * Drives the {@link com.czqwq.EZMiner.thread.ParallelTick} in sync with server ticks.
 * <p>
 * START → resume search threads so they run during the server tick.<br>
 * END → pause search threads until the next server tick.
 */
public class TickEventHandler {

    @SubscribeEvent
    public void onServerTickStart(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;
        EZMiner.parallelTick.processPreTickTasks(true);
    }

    @SubscribeEvent
    public void onServerTickEnd(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        EZMiner.parallelTick.processPreTickTasks(false);
    }

    @SubscribeEvent
    public void onClientTickStart(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;
        EZMiner.parallelTick.processNormalTasks();
    }

    public void registry() {
        FMLCommonHandler.instance()
            .bus()
            .register(this);
    }
}
