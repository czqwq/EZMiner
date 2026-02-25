package com.czqwq.EZMiner.core;

import java.util.concurrent.LinkedBlockingQueue;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;

import org.joml.Vector3i;

import com.czqwq.EZMiner.Config;
import com.czqwq.EZMiner.EZMiner;
import com.czqwq.EZMiner.core.founder.BasePositionFounder;
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

    public BaseOperator(Vector3i pos, Manager manager) {
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
}
