package com.czqwq.EZMiner.core;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.LinkedBlockingQueue;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ChatComponentTranslation;

import org.joml.Vector3i;

import com.czqwq.EZMiner.Config;
import com.czqwq.EZMiner.EZMiner;
import com.czqwq.EZMiner.chain.execution.BlockHarvestActionExecutor;
import com.czqwq.EZMiner.chain.execution.ChainActionExecutor;
import com.czqwq.EZMiner.chain.execution.ChainExecutionErrorReporter;
import com.czqwq.EZMiner.chain.execution.ChainHarvestExhaustionStrategy;
import com.czqwq.EZMiner.chain.execution.VisualProspectingBridge;
import com.czqwq.EZMiner.chain.network.PacketChainStateSync;
import com.czqwq.EZMiner.core.founder.BasePositionFounder;
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
    private final ChainActionExecutor harvestActionExecutor = new BlockHarvestActionExecutor();
    private final ChainHarvestExhaustionStrategy exhaustionStrategy = new ChainHarvestExhaustionStrategy();
    private final VisualProspectingBridge vpBridge = new VisualProspectingBridge();

    public BaseOperator(Vector3i pos, Manager manager) {
        this.playerMP = manager.player;
        this.manager = manager;
        this.positionFounder = EZMiner.chainPlanningRuntimeFactory
            .createFounderForMode(manager.minerModeState, pos, canBreakPositions, playerMP, manager.pConfig);
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
                vpBridge.notifyOreDiscovery(playerMP, pos, vpNotifiedChunks);
                exhaustionStrategy.harvestWithConfiguredExhaustion(
                    playerMP,
                    pos,
                    (float) manager.pConfig.addExhaustion,
                    harvestActionExecutor);
            } catch (Exception e) {
                ChainExecutionErrorReporter.reportHarvestError(manager, pos, e);
            }
            operatorCount++;
            countThisTick++;
            if (countThisTick >= Config.breakPerTick) {
                // Send server-authoritative runtime projection to client.
                EZMiner.network.network.sendTo(
                    new PacketChainStateSync(
                        manager.playerUUID,
                        operatorCount,
                        System.currentTimeMillis() - startTime,
                        true),
                    playerMP);
                return;
            }
        }

        // Send server-authoritative runtime projection to client each tick.
        EZMiner.network.network.sendTo(
            new PacketChainStateSync(manager.playerUUID, operatorCount, System.currentTimeMillis() - startTime, true),
            playerMP);

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
                new ChatComponentTranslation(
                    "ezminer.message.chain.done",
                    operatorCount,
                    Math.round(ms / 10.0) / 100.0f),
                manager.playerUUID);
        }
        // Reset client-side chain count and elapsed time display
        if (playerMP != null && !playerMP.isDead) {
            EZMiner.network.network.sendTo(new PacketChainStateSync(null, 0, 0L, false), playerMP);
        }
        FMLCommonHandler.instance()
            .bus()
            .unregister(this);
        positionFounder.interrupt();
        manager.inOperate = false;
        EZMiner.chainStateService.markSessionStop(manager.playerUUID);
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
        EZMiner.chainStateService.markSessionStop(manager.playerUUID);
    }
}
