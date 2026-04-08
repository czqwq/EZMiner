package com.czqwq.EZMiner.core;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.event.world.WorldEvent;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.czqwq.EZMiner.Config;
import com.czqwq.EZMiner.EZMiner;
import com.czqwq.EZMiner.network.PacketServerConfig;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent;

/**
 * Singleton that tracks a {@link Manager} per online player.
 */
public class PlayerManager {

    public static final Logger LOG = LogManager.getLogger();

    /** Singleton instance – set in serverStarting. */
    public static PlayerManager instance;

    public final Map<UUID, Manager> managers = new HashMap<>();

    @SubscribeEvent
    public void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.player instanceof EntityPlayerMP)) return;
        EntityPlayerMP mp = (EntityPlayerMP) event.player;
        EZMiner.chainLifecycleService.onPlayerLogin(mp);
        Manager mgr = new Manager(mp);
        managers.put(mp.getUniqueID(), mgr);
        mgr.registry();
        // Push server config limits to the client so preview/HUD reflects actual constraints
        // immediately on join, before any chain operation is started.
        EZMiner.network.network.sendTo(
            new PacketServerConfig(
                Config.bigRadius,
                Config.blockLimit,
                Config.smallRadius,
                Config.tunnelWidth,
                Config.breakPerTick),
            mp);
        LOG.info("Registered manager for player: {} ({})", mp.getDisplayName(), mp.getUniqueID());
    }

    @SubscribeEvent
    public void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.player instanceof EntityPlayerMP)) return;
        EntityPlayerMP mp = (EntityPlayerMP) event.player;
        if (!managers.containsKey(mp.getUniqueID())) {
            LOG.warn("No manager found for logging-out player: {}", mp.getDisplayName());
            return;
        }
        EZMiner.chainLifecycleService.onPlayerLogout(mp.getUniqueID(), managers);
        LOG.info("Unregistered manager for player: {} ({})", mp.getDisplayName(), mp.getUniqueID());
    }

    @SubscribeEvent
    public void onPlayerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (!(event.player instanceof EntityPlayerMP)) return;
        EntityPlayerMP mp = (EntityPlayerMP) event.player;
        EZMiner.chainLifecycleService.onPlayerDimensionChanged(mp.getUniqueID(), managers);
    }

    @SubscribeEvent
    public void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (!(event.player instanceof EntityPlayerMP)) return;
        EntityPlayerMP mp = (EntityPlayerMP) event.player;
        EZMiner.chainLifecycleService.onPlayerRespawn(mp.getUniqueID(), managers);
    }

    @SubscribeEvent
    public void onWorldUnload(WorldEvent.Unload event) {
        if (event.world == null || event.world.isRemote) return;
        EZMiner.chainLifecycleService.onWorldUnload(managers);
    }

    public void registry() {
        FMLCommonHandler.instance()
            .bus()
            .register(this);
        LOG.info("PlayerManager registered.");
    }
}
