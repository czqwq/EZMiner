package com.czqwq.EZMiner.core;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.entity.player.EntityPlayerMP;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

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
        Manager mgr = new Manager(mp);
        managers.put(mp.getUniqueID(), mgr);
        mgr.registry();
        LOG.info("Registered manager for player: {} ({})", mp.getDisplayName(), mp.getUniqueID());
    }

    @SubscribeEvent
    public void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.player instanceof EntityPlayerMP)) return;
        EntityPlayerMP mp = (EntityPlayerMP) event.player;
        Manager mgr = managers.remove(mp.getUniqueID());
        if (mgr == null) {
            LOG.warn("No manager found for logging-out player: {}", mp.getDisplayName());
            return;
        }
        mgr.unRegistry();
        LOG.info("Unregistered manager for player: {} ({})", mp.getDisplayName(), mp.getUniqueID());
    }

    public void registry() {
        FMLCommonHandler.instance()
            .bus()
            .register(this);
        LOG.info("PlayerManager registered.");
    }
}
