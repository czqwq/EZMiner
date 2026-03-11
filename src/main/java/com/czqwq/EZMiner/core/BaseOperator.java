package com.czqwq.EZMiner.core;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.LinkedBlockingQueue;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;

import org.joml.Vector3i;

import com.czqwq.EZMiner.Config;
import com.czqwq.EZMiner.EZMiner;
import com.czqwq.EZMiner.core.founder.BasePositionFounder;
import com.czqwq.EZMiner.core.founder.DeterminingIdentical;
import com.czqwq.EZMiner.network.PacketChainCount;
import com.czqwq.EZMiner.utils.MessageUtils;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

/**
 * Server-side chain operator. Dequeues block positions and harvests them
 * at a rate of up to 64 blocks per server tick.
 */
public class BaseOperator {

    public final EntityPlayerMP playerMP;
    public final Manager manager;
    public final BasePositionFounder positionFounder;
    public final LinkedBlockingQueue<Vector3i> canBreakPositions = new LinkedBlockingQueue<>();

    private long startTime;
    private int operatorCount = 0;
    /**
     * Tracks chunk coords (encoded as a single long) for which VP ore-vein
     * discovery has already been triggered this operation. Multiple ore blocks
     * in the same 16×16 chunk belong to the same vein; querying VP once per
     * chunk is sufficient and avoids redundant network packets in multiplayer.
     */
    private final Set<Long> vpNotifiedChunks = new HashSet<>();

    public BaseOperator(Vector3i pos, Manager manager) {
        checkCompatibility();
        this.playerMP = manager.player;
        this.manager = manager;
        this.positionFounder = manager.minerModeState
            .createPositionFounder(pos, canBreakPositions, playerMP, manager.pConfig);
        EZMiner.parallelTick.addPreServerTickTask(positionFounder);
    }

    @SubscribeEvent
    public void operatorTask(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;
        // Guard: stop if chain key released OR player is no longer online/alive
        if (!manager.inPressChainKey || !isPlayerOnline()) {
            unRegistry();
            return;
        }
        if (canBreakPositions.isEmpty()) {
            if (positionFounder.stopped.get()) unRegistry();
            return;
        }

        int countThisTick = 0;
        Vector3i pos;
        while ((pos = canBreakPositions.poll()) != null) {
            if (!canOperate() || !isPlayerOnline()) {
                unRegistry();
                return;
            }
            try {
                // Trigger Visual Prospecting ore vein discovery BEFORE the block is mined.
                // GT's onBlockActivated fires OreInteractEvent which VP's ServerCache intercepts
                // to send vein data to the player's map overlay.
                triggerVPOreDiscovery(pos);
                playerMP.theItemInWorldManager.tryHarvestBlock(pos.x, pos.y, pos.z);
                // Add configured exhaustion per block
                playerMP.addExhaustion((float) Config.addExhaustion);
            } catch (Exception e) {
                String msg = "EZMiner: Error while harvesting block at " + pos + ": " + e;
                EZMiner.LOG.error(msg, e);
                MessageUtils.serverSendPlayerMessage(msg, manager.playerUUID);
            }
            operatorCount++;
            countThisTick++;
            if (countThisTick >= 64) {
                // Send real-time count update to client
                EZMiner.network.network.sendTo(new PacketChainCount(operatorCount), playerMP);
                return;
            }
        }

        // Send real-time count update to client each tick
        EZMiner.network.network.sendTo(new PacketChainCount(operatorCount), playerMP);

        if (positionFounder.stopped.get()) unRegistry();
    }

    private boolean isPlayerOnline() {
        return playerMP != null && !playerMP.isDead && playerMP.worldObj != null;
    }

    private boolean canOperate() {
        if (!manager.inPressChainKey) return false;
        ItemStack item = playerMP.getCurrentEquippedItem();
        if (item != null && item.isItemStackDamageable()) {
            return (item.getMaxDamage() - item.getItemDamage()) > 1;
        }
        return true;
    }

    public void registry() {
        startTime = System.currentTimeMillis();
        FMLCommonHandler.instance()
            .bus()
            .register(this);
    }

    public void unRegistry() {
        long ms = System.currentTimeMillis() - startTime;
        if (manager.pConfig.useChainDoneMessage) {
            MessageUtils.serverSendPlayerMessage(
                "EZMiner: chain done; blocks=" + operatorCount + "; time=" + (Math.round(ms / 10.0) / 100.0f) + "s",
                manager.playerUUID);
        }
        FMLCommonHandler.instance()
            .bus()
            .unregister(this);
        positionFounder.interrupt();
        manager.inOperate = false;
    }

    /**
     * Emergency stop called when the player logs out mid-operation.
     * Does not attempt to send chat messages or deliver drops.
     */
    public void stopImmediately() {
        positionFounder.interrupt();
        canBreakPositions.clear();
        try {
            FMLCommonHandler.instance()
                .bus()
                .unregister(this);
        } catch (Exception ignored) {
            // already unregistered – safe to ignore
        }
        manager.inOperate = false;
    }

    // ===== Optional Visual Prospecting API compatibility =====
    private static volatile boolean compatibilityChecked = false;
    /** True when the Visual Prospecting mod is available on this installation. */
    public static volatile boolean hasVP_API = false;
    /**
     * Cached reference to {@code VisualProspecting_API.LogicalServer
     * .prospectOreVeinsWithinRadius(int, int, int, int)}.
     */
    private static volatile Method vpProspectMethod = null;
    /**
     * Cached reference to {@code VisualProspecting_API.LogicalServer
     * .sendProspectionResultsToClient(EntityPlayerMP, List, List)}.
     */
    private static volatile Method vpSendToClientMethod = null;

    /**
     * Detects the optional Visual Prospecting API once per JVM lifetime and
     * caches the two reflection method references needed for ore-vein discovery.
     * Synchronized so the once-only guarantee holds even under concurrent access.
     */
    public static synchronized void checkCompatibility() {
        if (compatibilityChecked) return;
        compatibilityChecked = true;
        try {
            Class<?> vpAPIClass = Class.forName("com.sinthoras.visualprospecting.VisualProspecting_API");
            Class<?> logicalServerClass = null;
            for (Class<?> inner : vpAPIClass.getDeclaredClasses()) {
                if ("LogicalServer".equals(inner.getSimpleName())) {
                    logicalServerClass = inner;
                    break;
                }
            }
            if (logicalServerClass == null) {
                EZMiner.LOG.warn("EZMiner: VisualProspecting_API found but LogicalServer class is missing.");
                return;
            }
            vpProspectMethod = logicalServerClass
                .getMethod("prospectOreVeinsWithinRadius", int.class, int.class, int.class, int.class);
            vpSendToClientMethod = logicalServerClass
                .getMethod("sendProspectionResultsToClient", EntityPlayerMP.class, List.class, List.class);
            hasVP_API = true;
            EZMiner.LOG.info("EZMiner: VisualProspecting_API detected – ore vein discovery enabled.");
        } catch (ClassNotFoundException e) {
            EZMiner.LOG.debug("EZMiner: VisualProspecting_API not found – ore vein discovery disabled.");
        } catch (NoSuchMethodException | SecurityException e) {
            EZMiner.LOG.warn(
                "EZMiner: VisualProspecting_API found but required methods could not be resolved: {}",
                e.getMessage());
        }
    }

    /**
     * Notifies Visual Prospecting of the ore vein at the given block position so
     * that it appears on the player's map overlay.
     *
     * <p>
     * Uses {@code VisualProspecting_API.LogicalServer.prospectOreVeinsWithinRadius}
     * to look up the vein in VP's server-side cache (a pure HashMap lookup — no
     * world access), then calls
     * {@code VisualProspecting_API.LogicalServer.sendProspectionResultsToClient}
     * which handles single-player (direct {@code ClientCache} update + chat
     * notification) and multiplayer (network packet) transparently.
     *
     * <p>
     * Each 16×16 chunk is only queried once per chain operation to avoid redundant
     * network traffic in multiplayer.
     *
     * <p>
     * This method is a no-op when VP is not installed or the block is not a GT ore.
     */
    private void triggerVPOreDiscovery(Vector3i pos) {
        if (!hasVP_API || vpProspectMethod == null || vpSendToClientMethod == null) return;
        if (!DeterminingIdentical.isGTOreBlock(pos, playerMP)) return;

        // One VP lookup per 16×16 chunk is enough – all blocks in the same chunk
        // belong to the same ore vein, and VP deduplicates on the client anyway.
        long chunkKey = ((long) (pos.x >> 4) << 32) | ((pos.z >> 4) & 0xFFFFFFFFL);
        if (!vpNotifiedChunks.add(chunkKey)) return;

        try {
            List<?> veins = (List<?>) vpProspectMethod.invoke(null, playerMP.dimension, pos.x, pos.z, 0);
            if (!veins.isEmpty()) {
                vpSendToClientMethod.invoke(null, playerMP, veins, Collections.emptyList());
            }
        } catch (Exception e) {
            EZMiner.LOG.debug("EZMiner: VP ore vein discovery call failed at {}: {}", pos, e.getMessage());
        }
    }
}
