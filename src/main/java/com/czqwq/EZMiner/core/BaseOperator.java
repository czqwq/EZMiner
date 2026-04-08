package com.czqwq.EZMiner.core;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.LinkedBlockingQueue;

import net.minecraft.block.Block;
import net.minecraft.block.BlockCrops;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraftforge.common.util.ForgeDirection;

import org.joml.Vector3i;

import com.czqwq.EZMiner.Config;
import com.czqwq.EZMiner.EZMiner;
import com.czqwq.EZMiner.chain.execution.BlockHarvestActionExecutor;
import com.czqwq.EZMiner.chain.execution.ChainActionExecutor;
import com.czqwq.EZMiner.chain.execution.ChainExecutor;
import com.czqwq.EZMiner.chain.execution.ChainExecutionErrorReporter;
import com.czqwq.EZMiner.chain.execution.ChainHarvestExhaustionStrategy;
import com.czqwq.EZMiner.chain.execution.VisualProspectingBridge;
import com.czqwq.EZMiner.chain.network.PacketChainStateSync;
import com.czqwq.EZMiner.chain.planning.ChainPlanningTask;
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
    public final ChainPlanningTask planningTask;
    public final LinkedBlockingQueue<Vector3i> canBreakPositions = new LinkedBlockingQueue<>();

    private long startTime;
    private int operatorCount = 0;
    private boolean stopRequested = false;
    /**
     * Tracks chunk coords (encoded as a single long) for which VP ore-vein
     * discovery has already been triggered this operation. Multiple ore blocks
     * in the same 16×16 chunk belong to the same vein; querying VP once per
     * chunk is sufficient and avoids redundant network packets in multiplayer.
     */
    private final Set<Long> vpNotifiedChunks = new HashSet<>();
    private final ChainActionExecutor harvestActionExecutor = new BlockHarvestActionExecutor();
    private final ChainExecutor chainExecutor = new ChainExecutor(harvestActionExecutor);
    private final ChainHarvestExhaustionStrategy exhaustionStrategy = new ChainHarvestExhaustionStrategy();
    private final VisualProspectingBridge vpBridge = new VisualProspectingBridge();

    public BaseOperator(Vector3i pos, Manager manager) {
        this.playerMP = manager.player;
        this.manager = manager;
        this.planningTask = EZMiner.chainPlanningRuntimeFactory
            .createTaskForMode(manager.minerModeState, pos, canBreakPositions, playerMP, manager.pConfig);
    }

    @SubscribeEvent
    public void operatorTask(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;
        // Guard: stop if chain key released OR player is no longer online/alive
        if (!manager.isKeyPressed() || !isPlayerOnline()) {
            unRegistry();
            return;
        }
        if (canBreakPositions.isEmpty()) {
            if (planningTask.isStopped()) unRegistry();
            return;
        }

        if (!canOperate() || !isPlayerOnline()) {
            unRegistry();
            return;
        }

        stopRequested = false;
        chainExecutor.executeBatch(canBreakPositions, Config.breakPerTick, this::processCandidate);
        if (stopRequested) {
            unRegistry();
            return;
        }

        // Send server-authoritative runtime projection to client each tick.
        long elapsedMs = System.currentTimeMillis() - startTime;
        manager.updateRuntimeProjection(operatorCount, elapsedMs, canBreakPositions.size());
        EZMiner.network.network.sendTo(
            new PacketChainStateSync(
                manager.activeSession,
                operatorCount,
                elapsedMs,
                true),
            playerMP);

        if (planningTask.isStopped() && canBreakPositions.isEmpty()) unRegistry();
    }

    private boolean isPlayerOnline() {
        return playerMP != null && !playerMP.isDead
            && playerMP.worldObj != null
            && (manager.activeSession == null || playerMP.dimension == manager.activeSession.dimensionId);
    }

    private boolean canOperate() {
        if (!manager.isKeyPressed()) return false;
        ItemStack item = playerMP.getCurrentEquippedItem();
        if (item != null && item.isItemStackDamageable()) {
            return (item.getMaxDamage() - item.getItemDamage()) > 1;
        }
        return true;
    }

    public void registry() {
        startTime = System.currentTimeMillis();
        planningTask.schedule();
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
        planningTask.interrupt();
        manager.clearRuntimeProjection();
        EZMiner.chainStateService.markSessionStop(manager.playerUUID);
        manager.activeSession = null;
    }

    /**
     * Emergency stop called when the player logs out mid-operation.
     * Does not attempt to send chat messages or deliver drops.
     */
    public void stopImmediately() {
        planningTask.interrupt();
        canBreakPositions.clear();
        try {
            FMLCommonHandler.instance()
                .bus()
                .unregister(this);
        } catch (Exception ignored) {
            // already unregistered – safe to ignore
        }
        manager.clearRuntimeProjection();
        EZMiner.chainStateService.markSessionStop(manager.playerUUID);
        manager.activeSession = null;
    }

    private boolean shouldHarvest(Vector3i pos) {
        if (!manager.isBlastCropMode()) return true;
        return Manager.isMatureCrop(playerMP.worldObj, pos.x, pos.y, pos.z);
    }

    private boolean processCandidate(Vector3i pos) {
        if (!canOperate() || !isPlayerOnline()) {
            stopRequested = true;
            return false;
        }
        try {
            if (!shouldHarvest(pos)) return true;
            Block preHarvestBlock = playerMP.worldObj.getBlock(pos.x, pos.y, pos.z);
            int preHarvestMeta = playerMP.worldObj.getBlockMetadata(pos.x, pos.y, pos.z);
            vpBridge.notifyOreDiscovery(playerMP, pos, vpNotifiedChunks);
            boolean harvested = exhaustionStrategy.harvestWithConfiguredExhaustion(
                playerMP,
                pos,
                (float) manager.pConfig.addExhaustion,
                harvestActionExecutor);
            if (!harvested) return true;
            replantVanillaCropIfNeeded(pos, preHarvestBlock, preHarvestMeta);
            operatorCount++;
        } catch (Exception e) {
            manager.reportRuntimeError("harvest_error");
            ChainExecutionErrorReporter.reportHarvestError(manager, pos, e);
        }
        return true;
    }

    private void replantVanillaCropIfNeeded(Vector3i pos, Block preHarvestBlock, int preHarvestMeta) {
        if (!manager.isBlastCropMode()) return;
        if (!(preHarvestBlock instanceof BlockCrops)) return;
        if (preHarvestMeta < 7) return;
        Block current = playerMP.worldObj.getBlock(pos.x, pos.y, pos.z);
        if (current == Blocks.air && canSustainReplantedCrop(pos, (BlockCrops) preHarvestBlock)) {
            playerMP.worldObj.setBlock(pos.x, pos.y, pos.z, preHarvestBlock, 0, 3);
        }
    }

    private boolean canSustainReplantedCrop(Vector3i cropPos, BlockCrops cropBlock) {
        if (cropPos.y <= 0) return false;
        Block soil = playerMP.worldObj.getBlock(cropPos.x, cropPos.y - 1, cropPos.z);
        if (soil == null || soil == Blocks.air) return false;
        return soil == Blocks.farmland || soil
            .canSustainPlant(playerMP.worldObj, cropPos.x, cropPos.y - 1, cropPos.z, ForgeDirection.UP, cropBlock);
    }
}
