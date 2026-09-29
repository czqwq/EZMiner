package com.czqwq.EZMiner.core;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.LinkedBlockingQueue;

import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ChatComponentTranslation;

import org.joml.Vector3i;

import com.czqwq.EZMiner.Config;
import com.czqwq.EZMiner.EZMiner;
import com.czqwq.EZMiner.chain.execution.BlockHarvestActionExecutor;
import com.czqwq.EZMiner.chain.execution.ChainActionExecutor;
import com.czqwq.EZMiner.chain.execution.ChainExecutionErrorReporter;
import com.czqwq.EZMiner.chain.execution.ChainExecutor;
import com.czqwq.EZMiner.chain.execution.ChainHarvestExhaustionStrategy;
import com.czqwq.EZMiner.chain.execution.ChunkBlockWriteHelper;
import com.czqwq.EZMiner.chain.execution.ChunkCachedHarvester;
import com.czqwq.EZMiner.chain.execution.ChunkPreloader;
import com.czqwq.EZMiner.chain.execution.CooldownTracker;
import com.czqwq.EZMiner.chain.execution.CropHarvestActionExecutor;
import com.czqwq.EZMiner.chain.execution.VisualProspectingBridge;
import com.czqwq.EZMiner.chain.network.PacketChainStateSync;
import com.czqwq.EZMiner.chain.planning.CachedPositionsPlanningTask;
import com.czqwq.EZMiner.chain.planning.ChainPlanningTask;
import com.czqwq.EZMiner.chain.watchdog.ChainWatchdog;
import com.czqwq.EZMiner.compat.GT5ToolCompat;
import com.czqwq.EZMiner.compat.GT5ToolDurabilityBridge;
import com.czqwq.EZMiner.compat.NaturaSaguaroCompat;
import com.czqwq.EZMiner.compat.TinkersConstructCompat;
import com.czqwq.EZMiner.compat.WitcheryVampireBridge;
import com.czqwq.EZMiner.core.crop.CropAdapterRegistry;
import com.czqwq.EZMiner.network.PacketToolBreakHandoff;
import com.czqwq.EZMiner.toolswap.server.ToolSwapServerService;
import com.czqwq.EZMiner.utils.MessageUtils;
import com.czqwq.EZMiner.utils.TimeFormatUtils;
import com.czqwq.EZMiner.utils.ToolHarvestEligibility;

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
    public ChainPlanningTask planningTask;
    public final LinkedBlockingQueue<Vector3i> canBreakPositions = new LinkedBlockingQueue<>();

    private long startTime;
    private int operatorCount = 0;
    private boolean stopRequested = false;
    /**
     * Server tick when the last tool-break handoff request was sent to the client.
     * 0 = no handoff in progress. The operator waits up to
     * {@link Config#toolBreakHandoffTimeoutTicks} ticks for the client to switch.
     */
    private long toolBreakHandoffTick = 0;
    /** Timestamp of the most recent successful block harvest. Initialized to startTime in registry(). */
    private long lastHarvestedTime = 0;
    /**
     * When non-zero, a 5-second auto-cancel countdown is active and this field holds the
     * millisecond timestamp when the countdown began. Reset to 0 when a block is harvested.
     */
    private long countdownStartTime = 0;
    /** Last countdown second that was announced to the player (1-5). */
    private int lastCountdownSecond = -1;
    /**
     * Tracks chunk coords (encoded as a single long) for which VP ore-vein
     * discovery has already been triggered this operation. Multiple ore blocks
     * in the same 16×16 chunk belong to the same vein; querying VP once per
     * chunk is sufficient and avoids redundant network packets in multiplayer.
     */
    private final Set<Long> vpNotifiedChunks = new HashSet<>();
    private final BlockHarvestActionExecutor harvestActionExecutor = new BlockHarvestActionExecutor();
    private final ChainActionExecutor cropHarvestActionExecutor = new CropHarvestActionExecutor();
    private final ChainExecutor chainExecutor = new ChainExecutor(harvestActionExecutor);
    private final ChainHarvestExhaustionStrategy exhaustionStrategy = new ChainHarvestExhaustionStrategy();
    private final VisualProspectingBridge vpBridge = new VisualProspectingBridge();
    /** Incremental chunk pre-loader for chain/blast modes when chunk loading is enabled. */
    private final ChunkPreloader chunkPreloader = new ChunkPreloader();

    public BaseOperator(Vector3i pos, Manager manager) {
        this.playerMP = manager.player;
        this.manager = manager;
        this.planningTask = EZMiner.chainPlanningRuntimeFactory
            .createTaskForMode(manager.minerModeState, pos, canBreakPositions, playerMP, manager.pConfig);
    }

    /** Factory using pre-calculated positions instead of a background founder. */
    public static BaseOperator createCached(Vector3i origin, Manager manager, List<Vector3i> positions) {
        BaseOperator op = new BaseOperator(origin, manager);
        // Replace the founder-based planning task with a lazy cache-feeding task.
        op.planningTask.interrupt(); // stop the default founder that was created in constructor
        op.canBreakPositions.clear();
        op.planningTask = new CachedPositionsPlanningTask(positions);
        return op;
    }

    @SubscribeEvent
    public void operatorTask(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;
        // Guard: stop if chain key released OR player is no longer online/alive
        if (!manager.isKeyPressed() || !isPlayerOnline()) {
            unRegistry();
            return;
        }

        // For cached tasks, feed positions on-demand instead of preloading the
        // entire list into the queue. This keeps memory pressure low and allows
        // cancellation to take effect within one tick.
        // Crazy mode: ignore per-tick limit with a 4096 safety cap to prevent freezes
        final int perTick;
        if (Config.crazyMode) {
            perTick = 4096;
        } else {
            perTick = planningTask instanceof CachedPositionsPlanningTask ? Config.cachedBreakPerTick
                : Config.breakPerTick;
        }

        if (planningTask instanceof CachedPositionsPlanningTask) {
            CachedPositionsPlanningTask cached = (CachedPositionsPlanningTask) planningTask;
            cached.feedTo(canBreakPositions, Config.crazyMode ? 4096 : perTick);
        }

        // ── Chunk preloading (server thread) ──
        // Loads 1 chunk/tick from disk only — never triggers terrain generation.
        // The founder runs before operatorTask (pre-tick), so chunks loaded here
        // become available on the NEXT tick for the founder to scan.
        if (Config.enableChainChunkLoading && !(planningTask instanceof CachedPositionsPlanningTask)) {
            chunkPreloader.tick();
        }

        // ── Always sync runtime to client, even when idle (so the timer keeps ticking) ──
        long now = System.currentTimeMillis();
        long elapsedMs = now - startTime;
        manager.updateRuntimeProjection(operatorCount, elapsedMs, canBreakPositions.size());
        EZMiner.network.network
            .sendTo(new PacketChainStateSync(manager.activeSession, operatorCount, elapsedMs, true), playerMP);

        // ── Idle timeout: auto-cancel when stuck at chunk boundary with no new blocks ──
        if (operatorCount > 0 && Config.chainIdleTimeoutSeconds >= 0 && Config.chainIdleCountdownSeconds >= 0) {
            long idleMs = now - lastHarvestedTime;
            if (idleMs > Config.chainIdleTimeoutSeconds * 1000L) {
                if (countdownStartTime == 0) {
                    countdownStartTime = now;
                    lastCountdownSecond = -1;
                }
                int countdownSec = Config.chainIdleCountdownSeconds - (int) ((now - countdownStartTime) / 1000);
                if (countdownSec <= 0) {
                    int totalTimeout = Config.chainIdleTimeoutSeconds + Config.chainIdleCountdownSeconds;
                    MessageUtils.serverSendPlayerMessage(
                        new ChatComponentTranslation("ezminer.message.chain.idle_cancel", totalTimeout),
                        manager.playerUUID);
                    unRegistry();
                    return;
                }
                if (countdownSec != lastCountdownSecond && countdownSec <= Config.chainIdleCountdownSeconds) {
                    lastCountdownSecond = countdownSec;
                    MessageUtils.serverSendPlayerMessage(
                        new ChatComponentTranslation("ezminer.message.chain.idle_countdown", countdownSec),
                        manager.playerUUID);
                }
            }
        }

        // ── Tick-based watchdog: force-cancel stuck chains regardless of wall-clock time ──
        if (Config.enableChainWatchdog && ChainWatchdog.hasTimedOut(manager.playerUUID)) {
            MessageUtils.serverSendPlayerMessage(
                new ChatComponentTranslation("ezminer.message.chain.watchdog_timeout"),
                manager.playerUUID);
            unRegistry();
            return;
        }

        if (canBreakPositions.isEmpty()) {
            if (planningTask.isStopped()) unRegistry();
            return;
        }

        // The planner delivered work, so the chain is making progress. Refreshing here (not only
        // in markHarvested) is what stops the watchdog from cancelling a chain that is still
        // searching or waiting in the tool-handoff state — see ChainWatchdog.markChainStarted.
        ChainWatchdog.recordProgress(manager.playerUUID);

        // Tool break handoff: skip harvesting while waiting for the client to switch tools.
        // canOperate() returns true during the wait, but we shouldn't use the dying tool.
        if (toolBreakHandoffTick != 0 && getServerTick() - toolBreakHandoffTick >= 1) {
            // After the first tick (the detection tick), pause harvesting.
            // The client needs server ticks to sync the inventory change.
            if (!canOperate() || !isPlayerOnline()) {
                unRegistry();
                return;
            }
            // Re-check: has the tool changed to a usable replacement? If so, resume.
            // A null item or a non-tool item (empty slot, block, food…) must NOT
            // resume the chain — otherwise the client auto-switch to an empty hotbar
            // slot would let mining continue without a real tool (and often without
            // drops).
            if (!isHandoffToolUsable()) return; // Still waiting, skip this tick
            toolBreakHandoffTick = 0;
        }

        if (!canOperate() || !isPlayerOnline()) {
            unRegistry();
            return;
        }

        stopRequested = false;
        // Use batched exhaustion for non-crop block mining: save once before batch,
        // harvest N blocks, apply total exhaustion once after. Crop mode uses the
        // legacy per-block exhaustion path since each crop may or may not be harvestable.
        if (!manager.isSpecialCropMode()) {
            if (Config.useChunkCachedHarvest) {
                processBatchChunkCached(perTick);
            } else {
                processBatchWithBatchedExhaustion(perTick);
            }
        } else {
            chainExecutor.executeBatch(canBreakPositions, perTick, this::processCandidate);
        }
        if (stopRequested) {
            unRegistry();
            return;
        }

        if (planningTask.isStopped() && canBreakPositions.isEmpty()) unRegistry();
    }

    private boolean isPlayerOnline() {
        return playerMP != null && !playerMP.isDead
            && playerMP.worldObj != null
            && (manager.activeSession == null || playerMP.dimension == manager.activeSession.dimensionId);
    }

    private boolean canOperate() {
        if (!manager.isKeyPressed()) return false;
        if (playerMP.getFoodStats()
            .getFoodLevel() <= 0) return false;
        ItemStack item = playerMP.getCurrentEquippedItem();
        if (item != null && item.isItemStackDamageable()) {
            boolean toolGood;
            if (TinkersConstructCompat.isTiCTool(item)) {
                toolGood = TinkersConstructCompat.canContinueMining(item);
            } else if (GT5ToolCompat.isGTTool(item)) {
                // GT tools extend MetaBaseItem, whose constructor calls setMaxDamage(0), so the
                // vanilla formula below evaluates (0 - 0) > 1 — always false. Every GT chain
                // therefore requested a tool-break handoff on every step and EZMiner never got a
                // real durability warning, which is what made the missing pre-check reachable.
                // GT durability lives in NBT, so ask the bridge that already knows how to read it.
                toolGood = GT5ToolDurabilityBridge.hasDurabilityReserveForNextBlock(playerMP);
            } else {
                toolGood = (item.getMaxDamage() - item.getItemDamage()) > 1;
            }
            if (!toolGood) {
                // Tool is about to break — try handoff before cancelling
                return tryToolBreakHandoff();
            }
        }
        // Tool is still good — reset handoff state
        toolBreakHandoffTick = 0;
        return true;
    }

    /**
     * Attempts a tool-break handoff: sends a signal to the client to switch tools,
     * then waits up to {@link Config#toolBreakHandoffTimeoutTicks} ticks.
     *
     * @return true if the handoff may have succeeded (tool switched), false if timeout
     */
    private boolean tryToolBreakHandoff() {
        if (!Config.enableToolBreakHandoff) return false;
        long serverTick = getServerTick();
        if (toolBreakHandoffTick == 0) {
            // First detection — send handoff request to client
            EZMiner.network.network.sendTo(new PacketToolBreakHandoff(), playerMP);
            toolBreakHandoffTick = serverTick;
            return true; // Keep going this tick, let the client react
        }
        // Check if the client had enough time to switch
        long elapsed = serverTick - toolBreakHandoffTick;
        if (elapsed < Config.toolBreakHandoffTimeoutTicks) {
            return true; // Still waiting — keep the chain alive
        }
        // Timeout — handoff failed, cancel the chain
        return false;
    }

    /**
     * True when the currently equipped item is a usable replacement mining tool
     * for the tool-break handoff.
     *
     * <p>
     * This deliberately rejects {@code null} and non-tool items: the client's
     * auto-switch can move to an empty hotbar slot or a block/food when no better
     * tool exists, and resuming the chain there would keep mining without a real
     * tool (and often without drops).
     */
    private boolean isHandoffToolUsable() {
        ItemStack item = playerMP.getCurrentEquippedItem();
        if (item == null) return false;

        // When the next chain target is known, require the held item to actually
        // harvest it — this prevents resuming with a wrong-type tool (sword, bow,
        // non-tool) that would break blocks without drops.
        Vector3i next = canBreakPositions.peek();
        if (next != null && playerMP.worldObj != null && playerMP.worldObj.blockExists(next.x, next.y, next.z)) {
            Block block = playerMP.worldObj.getBlock(next.x, next.y, next.z);
            int meta = playerMP.worldObj.getBlockMetadata(next.x, next.y, next.z);
            if (block != null && block != net.minecraft.init.Blocks.air) {
                // GT Toolbox is a valid replacement only when it has an internal
                // tool that can harvest the next target.
                if (GT5ToolCompat.isGTToolbox(item)) {
                    return GT5ToolCompat.findBestToolboxSlotForBlock(item, block, meta) >= 0;
                }
                return ToolHarvestEligibility.isEligible(item, block, meta);
            }
        }
        if (GT5ToolCompat.isGTToolbox(item)) return false; // cannot validate without a target
        return ToolHarvestEligibility.isUsableMiningTool(item);
    }

    private static long getServerTick() {
        try {
            return cpw.mods.fml.common.FMLCommonHandler.instance()
                .getMinecraftServerInstance()
                .getTickCounter();
        } catch (Exception e) {
            return System.currentTimeMillis() / 50;
        }
    }

    public void registry() {
        startTime = System.currentTimeMillis();
        lastHarvestedTime = startTime;
        countdownStartTime = 0;
        lastCountdownSecond = -1;
        toolBreakHandoffTick = 0;
        // The watchdog is deliberately NOT armed here. Arming at chain start made the 100-tick
        // (5 s) default fire while a large-radius search was still legitimately running, because
        // the check sits before the empty-queue early return below. It is armed by the first
        // harvest / first enqueued candidates instead (ChainWatchdog.markChainStarted javadoc).
        ChainWatchdog.remove(manager.playerUUID);
        // Init chunk preloader (no chunks loaded yet — loading happens in tick())
        if (Config.enableChainChunkLoading && manager.originPos != null) {
            chunkPreloader.init(playerMP.worldObj, manager.originPos, manager.pConfig.bigRadius);
        }
        planningTask.schedule();
        FMLCommonHandler.instance()
            .bus()
            .register(this);
    }

    public void unRegistry() {
        // Record cooldown timestamp when the chain completes (not on emergency stop).
        // Creative players are handled inside CooldownTracker.recordUse.
        if (operatorCount > 0) {
            CooldownTracker.recordUse(playerMP);
        }
        long ms = System.currentTimeMillis() - startTime;
        if (manager.pConfig.useChainDoneMessage) {
            MessageUtils.serverSendPlayerMessage(
                new ChatComponentTranslation(
                    "ezminer.message.chain.done",
                    operatorCount,
                    TimeFormatUtils.formatElapsedServer(ms)),
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
        cleanupOperatorState();
        // Server-authoritative restore of borrowed hotbar items after the chain ends.
        ToolSwapServerService.finalize(playerMP);
    }

    /** Emergency stop on player logout — no chat messages or drop delivery. */
    public void stopImmediately() {
        planningTask.interrupt();
        canBreakPositions.clear();
        try {
            FMLCommonHandler.instance()
                .bus()
                .unregister(this);
        } catch (Exception ignored) {}
        cleanupOperatorState(); // also removes watchdog tracking
        ToolSwapServerService.finalize(playerMP);
    }

    private void cleanupOperatorState() {
        manager.clearRuntimeProjection();
        chunkPreloader.reset();
        EZMiner.chainStateService.markSessionStop(manager.playerUUID);
        manager.activeSession = null;
        ChainWatchdog.remove(manager.playerUUID);
    }

    private boolean shouldHarvest(Vector3i pos) {
        if (!manager.isSpecialCropMode()) return true;
        if (playerMP.worldObj == null) return false;
        return CropAdapterRegistry.isMatureCrop(playerMP.worldObj, pos.x, pos.y, pos.z);
    }

    /** Record that a block was harvested (resets idle countdown and watchdog). */
    private void markHarvested() {
        lastHarvestedTime = System.currentTimeMillis();
        countdownStartTime = 0;
        lastCountdownSecond = -1;
        ChainWatchdog.recordProgress(manager.playerUUID);
    }

    private boolean processCandidate(Vector3i pos) {
        if (!canOperate() || !isPlayerOnline()) {
            stopRequested = true;
            return false;
        }
        // Tool-break handoff has just been requested: stop processing further
        // candidates this tick (see processBatchWithBatchedExhaustion for rationale).
        if (toolBreakHandoffTick != 0) {
            return false;
        }
        try {
            if (!shouldHarvest(pos)) return true;
            // Stop-on-unbreakable: if the player's tool cannot harvest this block,
            // cancel the entire chain immediately instead of silently skipping it.
            if (Config.stopOnUnbreakable && playerMP.worldObj != null) {
                Block block = playerMP.worldObj.getBlock(pos.x, pos.y, pos.z);
                if (block != null && !block.isAir(playerMP.worldObj, pos.x, pos.y, pos.z)) {
                    int meta = playerMP.worldObj.getBlockMetadata(pos.x, pos.y, pos.z);
                    if (!block.canHarvestBlock(playerMP, meta)
                        && !WitcheryVampireBridge.canHarvestWithBareHands(playerMP)) {
                        stopRequested = true;
                        return false;
                    }
                }
            }
            vpBridge.notifyOreDiscovery(playerMP, pos, vpNotifiedChunks);
            ChainActionExecutor executor = manager.isSpecialCropMode() ? cropHarvestActionExecutor
                : harvestActionExecutor;
            boolean harvested = exhaustionStrategy
                .harvestWithConfiguredExhaustion(playerMP, pos, (float) manager.pConfig.addExhaustion, executor);
            if (!harvested) return true;
            operatorCount++;
            markHarvested();
        } catch (Exception e) {
            manager.reportRuntimeError("harvest_error");
            ChainExecutionErrorReporter.reportHarvestError(manager, pos, e);
        }
        return true;
    }

    /** Saves exhaustion once, harvests up to {@code perTick} blocks, applies total exhaustion once. */
    private void processBatchWithBatchedExhaustion(int perTick) {
        net.minecraft.util.FoodStats food = playerMP.getFoodStats();
        float exhaustionBefore = exhaustionStrategy.getExhaustion(food);
        int harvested = 0;

        // Optional batched neighbour notification (floating water/sand/grass fix).
        // When disabled no sink is created and the hot path is untouched.
        List<ChunkBlockWriteHelper.RemovedBlock> removedSink = Config.notifyNeighborsOnChainBreak
            ? new ArrayList<>(Math.min(perTick, 64) * 2)
            : null;

        // ── Collect batch positions ──
        Vector3i pos;
        while (harvested < perTick && (pos = canBreakPositions.poll()) != null) {
            if (!canOperate() || !isPlayerOnline()) {
                stopRequested = true;
                break;
            }
            // Tool-break handoff has just been requested: stop mining this tick
            // immediately. Otherwise the same-tick loop would continue with a
            // broken/doomed tool and remove blocks without drops (TiC tools in
            // particular lose all drops once Broken because canHarvestBlock fails).
            if (toolBreakHandoffTick != 0) {
                break;
            }
            try {
                if (!shouldHarvest(pos)) continue;
                // Stop-on-unbreakable: if the player's tool cannot harvest this block,
                // cancel the entire chain immediately instead of silently skipping it.
                if (Config.stopOnUnbreakable && playerMP.worldObj != null) {
                    Block block = playerMP.worldObj.getBlock(pos.x, pos.y, pos.z);
                    if (block != null && !block.isAir(playerMP.worldObj, pos.x, pos.y, pos.z)) {
                        int meta = playerMP.worldObj.getBlockMetadata(pos.x, pos.y, pos.z);
                        if (!block.canHarvestBlock(playerMP, meta)
                            && !WitcheryVampireBridge.canHarvestWithBareHands(playerMP)) {
                            stopRequested = true;
                            break;
                        }
                    }
                }
                vpBridge.notifyOreDiscovery(playerMP, pos, vpNotifiedChunks);
                // Record the type before removal; after removal the position is air.
                // Saguaro cascades its unsupported neighbors into the work queue.
                boolean wasSaguaro = NaturaSaguaroCompat.isSaguaroBlock(playerMP.worldObj, pos.x, pos.y, pos.z);
                boolean ok = harvestActionExecutor.execute(pos, playerMP, removedSink);
                if (!ok) continue;
                if (wasSaguaro) {
                    NaturaSaguaroCompat
                        .cascadeUnsupportedNeighbors(playerMP.worldObj, pos.x, pos.y, pos.z, canBreakPositions);
                }
                operatorCount++;
                harvested++;
                markHarvested();
            } catch (Exception e) {
                manager.reportRuntimeError("harvest_error");
                ChainExecutionErrorReporter.reportHarvestError(manager, pos, e);
            }
        }

        if (removedSink != null && !removedSink.isEmpty()) {
            ChunkBlockWriteHelper.notifyBatchNeighborChange(playerMP.worldObj, removedSink);
        }

        exhaustionStrategy
            .setExhaustion(food, Math.min(exhaustionBefore + harvested * (float) manager.pConfig.addExhaustion, 4.0F));
    }

    /**
     * Chunk-cached variant of {@link #processBatchWithBatchedExhaustion} that uses
     * {@link ChunkCachedHarvester} to avoid repeated
     * {@code world.getChunkFromChunkCoords()} LongHashMap lookups.
     *
     * <p>
     * For non-crop block mining, this eliminates 3 chunk lookups per block compared
     * to the standard path ({@code getBlock} + {@code getBlockMetadata} + {@code setBlock}).
     * The inner loop delegates to {@link ChunkCachedHarvester#harvestNext} which caches
     * chunk and ExtendedBlockStorage references internally.
     */
    private void processBatchChunkCached(int perTick) {
        net.minecraft.util.FoodStats food = playerMP.getFoodStats();
        float exhaustionBefore = exhaustionStrategy.getExhaustion(food);
        int harvested = 0;

        // Optional batched neighbour notification (floating water/sand/grass fix).
        List<ChunkBlockWriteHelper.RemovedBlock> removedSink = Config.notifyNeighborsOnChainBreak
            ? new ArrayList<>(Math.min(perTick, 64) * 2)
            : null;

        ChunkCachedHarvester harvester = new ChunkCachedHarvester();

        Vector3i pos;
        while (harvested < perTick && (pos = canBreakPositions.poll()) != null) {
            if (!canOperate() || !isPlayerOnline()) {
                stopRequested = true;
                break;
            }
            // Tool-break handoff has just been requested: stop mining this tick
            // immediately (see processBatchWithBatchedExhaustion for rationale).
            if (toolBreakHandoffTick != 0) {
                break;
            }
            try {
                if (!shouldHarvest(pos)) continue;

                // Stop-on-unbreakable: if the player's tool cannot harvest this block,
                // cancel the entire chain immediately instead of silently skipping it.
                if (Config.stopOnUnbreakable && playerMP.worldObj != null) {
                    Block block = playerMP.worldObj.getBlock(pos.x, pos.y, pos.z);
                    if (block != null && !block.isAir(playerMP.worldObj, pos.x, pos.y, pos.z)) {
                        int meta = playerMP.worldObj.getBlockMetadata(pos.x, pos.y, pos.z);
                        if (!block.canHarvestBlock(playerMP, meta)
                            && !WitcheryVampireBridge.canHarvestWithBareHands(playerMP)) {
                            stopRequested = true;
                            break;
                        }
                    }
                }

                vpBridge.notifyOreDiscovery(playerMP, pos, vpNotifiedChunks);

                // Record the type before removal; after removal the position is air.
                // Saguaro cascades its unsupported neighbors into the work queue.
                boolean wasSaguaro = NaturaSaguaroCompat.isSaguaroBlock(playerMP.worldObj, pos.x, pos.y, pos.z);
                boolean ok = harvester.harvestNext(pos, playerMP, removedSink);
                if (!ok) continue;
                if (wasSaguaro) {
                    NaturaSaguaroCompat
                        .cascadeUnsupportedNeighbors(playerMP.worldObj, pos.x, pos.y, pos.z, canBreakPositions);
                }

                operatorCount++;
                harvested++;
                markHarvested();
            } catch (Exception e) {
                manager.reportRuntimeError("harvest_error");
                ChainExecutionErrorReporter.reportHarvestError(manager, pos, e);
            }
        }

        // Flush height map for the last chunk touched
        harvester.flushRemaining();

        if (removedSink != null && !removedSink.isEmpty()) {
            ChunkBlockWriteHelper.notifyBatchNeighborChange(playerMP.worldObj, removedSink);
        }

        exhaustionStrategy
            .setExhaustion(food, Math.min(exhaustionBefore + harvested * (float) manager.pConfig.addExhaustion, 4.0F));
    }
}
