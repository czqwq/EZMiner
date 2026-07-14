package com.czqwq.EZMiner.core;

import java.util.UUID;

import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.world.World;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.world.BlockEvent;

import org.joml.Vector3i;

import com.czqwq.EZMiner.Config;
import com.czqwq.EZMiner.EZMiner;
import com.czqwq.EZMiner.chain.execution.BlockSwapModeHandler;
import com.czqwq.EZMiner.chain.execution.ChainDropCollector;
import com.czqwq.EZMiner.chain.execution.CooldownTracker;
import com.czqwq.EZMiner.chain.execution.MinesweeperModeHandler;
import com.czqwq.EZMiner.chain.execution.SudokuModeHandler;
import com.czqwq.EZMiner.chain.execution.XPDropHandler;
import com.czqwq.EZMiner.chain.planning.ChainPreCalcCache;
import com.czqwq.EZMiner.chain.planning.ChainPreCalcCache.CachedEntry;
import com.czqwq.EZMiner.chain.planning.ChainPreCalcEngine;
import com.czqwq.EZMiner.chain.state.ChainPlayerState;
import com.czqwq.EZMiner.chain.state.ChainSession;
import com.czqwq.EZMiner.core.founder.CropFounder;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.eventhandler.Event.Result;
import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

/**
 * Per-player chain mining manager.
 * Listens for block break events and manages the operator lifecycle.
 * All mutation happens on the server thread.
 */
public class Manager {

    /**
     * True when the Bandit mod (vein-mining mod) is present on this installation.
     *
     * <p>
     * Bandit collects drops by intercepting {@code EntityJoinWorldEvent} inside a
     * {@code HarvestCollector.withHarvestCollectorScope} wrapper. EZMiner's
     * {@link #onHarvestDrops} runs at {@code LOWEST} priority and clears
     * {@code event.drops} before Minecraft can spawn the {@code EntityItem}s that
     * Bandit expects to intercept. This causes Bandit to receive zero drops and
     * items to disappear. When Bandit is present, EZMiner therefore skips its own
     * drop-collection logic so that Bandit can handle drops normally.
     */
    private static final boolean BANDIT_LOADED = Loader.isModLoaded("bandit");

    public final UUID playerUUID;
    public EntityPlayerMP player;
    public final MinerConfig pConfig;
    public final MinerModeState minerModeState;

    public BaseOperator operator = null;
    public volatile ChainSession activeSession = null;

    /**
     * Position of the first block broken in this chain; used as the drop-spawn point when {@code dropToPlayer} is
     * false.
     */
    public Vector3i originPos = null;

    /**
     * Collected drops during the current chain operation.
     *
     * <p>
     * Uses a fast O(1) map for items without NBT and a short fallback list for
     * NBT-bearing items. See {@link com.czqwq.EZMiner.chain.execution.ChainDropCollector}
     * for details.
     */
    private final ChainDropCollector dropCollector = new ChainDropCollector();
    private final MinesweeperModeHandler minesweeperHandler = new MinesweeperModeHandler();
    private final SudokuModeHandler sudokuHandler = new SudokuModeHandler();
    private final BlockSwapModeHandler blockSwapHandler = new BlockSwapModeHandler();

    // ── Pre-calculation engine for cached chain sub-modes ──
    //
    // The BFS runs directly on the server thread (inside onWorldTick), processing a
    // limited number of candidates per tick. This avoids the Hodgepodge
    // ServerThreadLongHashMap off-thread warning that background founder threads
    // trigger on every blockExists() / chunkExists() call.
    //
    // Encapsulated in ChainPreCalcEngine (chain/planning/) — decoupled from Manager
    // so the BFS logic is independently testable and the chain subsystem owns all
    // planning concerns.

    /** Per-player server-thread BFS engine for cached chain pre-calculation. */
    private final ChainPreCalcEngine preCalcEngine = new ChainPreCalcEngine();

    public Manager(EntityPlayerMP player) {
        this.player = player;
        this.playerUUID = player.getUniqueID();
        ChainPlayerState state = state();
        this.pConfig = state.minerConfig;
        this.minerModeState = state.minerModeState;
    }

    // ===== Block Break Trigger =====

    @SubscribeEvent
    public void onBlockBreak(BlockEvent.BreakEvent event) {
        if (!isSamePlayer(event.getPlayer())) return;
        if (isInOperate() || !isKeyPressed()) return;
        if (isSpecialMinesweeperMode()) return;
        if (isSpecialCropMode()) return;
        if (isBlockSwapMode()) return;
        // Cooldown check: prevent starting a new chain while cooldown is active
        if (CooldownTracker.isOnCooldown((EntityPlayerMP) event.getPlayer())) return;
        // For cached chain modes, try to use the pre-calculated cache first.
        if (isCachedChainMode()) {
            Vector3i pos = new Vector3i(event.x, event.y, event.z);
            if (tryStartCachedChain(pos, (EntityPlayerMP) event.getPlayer())) return;
            // Cache miss — the pre-calculation is preserved for the next attempt
            // (the player may have aimed at a slightly different block; the next
            // break of a matching-type block within range will still hit the cache).
            // Fall through to normal chain start with a fresh founder.
        }
        startChain(new Vector3i(event.x, event.y, event.z), (EntityPlayerMP) event.getPlayer());
    }

    // Must receive canceled events because some crop/interaction mods cancel
    // RIGHT_CLICK_BLOCK before EZMiner runs; we still need to start chain harvest.
    @SubscribeEvent(priority = EventPriority.HIGHEST, receiveCanceled = true)
    public void onCropRightClick(PlayerInteractEvent event) {
        if (event.action != PlayerInteractEvent.Action.RIGHT_CLICK_BLOCK) return;
        if (!isSamePlayer(event.entityPlayer)) return;
        if (isInOperate() || !isKeyPressed()) return;
        if (!isSpecialCropMode()) return;
        // Cooldown check: prevent starting a new crop chain while cooldown is active
        if (CooldownTracker.isOnCooldown((EntityPlayerMP) event.entityPlayer)) return;
        if (!CropFounder.isMatureCrop(event.entityPlayer.worldObj, event.x, event.y, event.z)) return;
        startChain(new Vector3i(event.x, event.y, event.z), (EntityPlayerMP) event.entityPlayer);
        // Explicitly consume the interaction so vanilla/sibling handlers do not
        // perform a second single-crop right-click harvest.
        event.useBlock = Result.DENY;
        event.useItem = Result.DENY;
        event.setCanceled(true);
    }

    // ── Block swap debounce ──
    private long lastBlockSwapEncodedPos = Long.MIN_VALUE;
    private long lastBlockSwapTimeMs = 0L;

    private static long encodeSwapPos(int x, int y, int z) {
        return ((long) x << 40) | ((long) z << 8) | (long) (y & 0xFF);
    }

    // Block swap mode: right-click triggers block replacement.
    // Does NOT require the chain key to be held — the mode itself is the activation
    // signal. The player right-clicks while in this sub-mode to swap blocks.
    @SubscribeEvent(priority = EventPriority.HIGHEST, receiveCanceled = true)
    public void onBlockSwapRightClick(PlayerInteractEvent event) {
        if (isInOperate() || !isKeyPressed()) return;
        if (!isBlockSwapMode()) return;

        final Vector3i targetPos;
        if (event.action == PlayerInteractEvent.Action.RIGHT_CLICK_BLOCK) {
            if (!isSamePlayer(event.entityPlayer)) return;
            targetPos = new Vector3i(event.x, event.y, event.z);
        } else if (event.action == PlayerInteractEvent.Action.RIGHT_CLICK_AIR) {
            if (!isSamePlayer(event.entityPlayer)) return;
            if (event.entityPlayer.worldObj == null) return;
            EntityPlayerMP ep = (EntityPlayerMP) event.entityPlayer;
            MovingObjectPosition mop = ep.rayTrace(5.0D, 1.0F);
            if (mop == null || mop.typeOfHit != MovingObjectPosition.MovingObjectType.BLOCK) return;
            targetPos = new Vector3i(mop.blockX, mop.blockY, mop.blockZ);
        } else {
            return;
        }

        // Debounce: skip if same position swapped within 150ms
        long now = System.currentTimeMillis();
        long encoded = encodeSwapPos(targetPos.x, targetPos.y, targetPos.z);
        if (encoded == lastBlockSwapEncodedPos && now - lastBlockSwapTimeMs < 150) return;
        lastBlockSwapEncodedPos = encoded;
        lastBlockSwapTimeMs = now;

        setInOperate(true);
        try {
            blockSwapHandler.handleSwap((EntityPlayerMP) event.entityPlayer, targetPos);
        } finally {
            flushDrops();
            setInOperate(false);
        }
        event.useBlock = Result.DENY;
        event.useItem = Result.DENY;
        event.setCanceled(true);
    }

    // ===== Drop Collection =====

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onHarvestDrops(BlockEvent.HarvestDropsEvent event) {
        if (event.harvester == null || !isSamePlayer(event.harvester)) return;
        if (!isInOperate()) return;
        // When Bandit is loaded it collects drops via EntityJoinWorldEvent inside its own
        // HarvestCollector scope. Clearing event.drops here would prevent those EntityItems
        // from ever being spawned, leaving Bandit with zero drops. Yield to Bandit so that
        // items drop normally and Bandit can intercept them as designed.
        if (BANDIT_LOADED) return;
        dropCollector.collect(event.drops);
        if (Config.dropImmediately) {
            // Flush drops after every harvest instead of batching at chain end.
            // This eliminates the lag spike caused by spawning hundreds of EntityItems
            // simultaneously when a large vein finishes.
            flushCollectedDrops();
        }
    }

    /** Spawns accumulated drops immediately without the usual end-of-chain guard. */
    private void flushCollectedDrops() {
        if (dropCollector.isEmpty() || player == null || player.worldObj == null) {
            dropCollector.clear();
            return;
        }
        final double spawnX, spawnY, spawnZ;
        if (Config.dropToPlayer || originPos == null) {
            spawnX = player.posX;
            spawnY = player.posY;
            spawnZ = player.posZ;
        } else {
            spawnX = originPos.x + 0.5;
            spawnY = originPos.y + 0.5;
            spawnZ = originPos.z + 0.5;
        }
        dropCollector.flush(player.worldObj, spawnX, spawnY, spawnZ);
    }

    // ===== Tick: flush drops after chain ends =====

    @SubscribeEvent
    public void onWorldTick(TickEvent.WorldTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;
        if (player == null || event.world != player.worldObj || isInOperate()) return;
        if (isKeyPressed()) {
            tickSpecialMode();
            preCalcEngine.tick(player, pConfig, minerModeState);
            return;
        }
        // Key released: stop any in-progress pre-calculation and flush drops.
        preCalcEngine.stop(player);
        flushDrops();
    }

    public void flushDrops() {
        boolean hasItems = !dropCollector.isEmpty();
        boolean hasXP = XPDropHandler.hasAccumulatedXP(player);
        if (!hasItems && !hasXP) return;
        if (player == null || player.worldObj == null) {
            dropCollector.clear();
            XPDropHandler.clear(player);
            return;
        }
        // dropToPlayer=true → spawn at the player's current feet position (default)
        // dropToPlayer=false → spawn at the center of the first mined block
        final double spawnX, spawnY, spawnZ;
        if (Config.dropToPlayer || originPos == null) {
            spawnX = player.posX;
            spawnY = player.posY;
            spawnZ = player.posZ;
        } else {
            spawnX = originPos.x + 0.5;
            spawnY = originPos.y + 0.5;
            spawnZ = originPos.z + 0.5;
        }
        if (hasItems) {
            dropCollector.flush(player.worldObj, spawnX, spawnY, spawnZ);
        }
        if (hasXP) {
            XPDropHandler.flush(player.worldObj, player, spawnX, spawnY, spawnZ, Config.mergeXPOrbs);
        }
    }

    /** Clears all accumulated drops from the drop collector. */
    public void clearDrops() {
        dropCollector.clear();
        XPDropHandler.clear(player);
    }

    // ===== Lifecycle =====

    public void registry() {
        FMLCommonHandler.instance()
            .bus()
            .register(this);
        MinecraftForge.EVENT_BUS.register(this);
    }

    public void unRegistry() {
        cleanupState();
        FMLCommonHandler.instance()
            .bus()
            .unregister(this);
        MinecraftForge.EVENT_BUS.unregister(this);
    }

    public void cleanupState() {
        preCalcEngine.cleanup();
        ChainPreCalcCache.remove(playerUUID);
        ChainPlayerState state = state();
        state.keyPressed = false;
        state.runtimeState.inOperate = false;
        state.runtimeState.chainedCount = 0;
        state.runtimeState.elapsedMs = 0L;
        state.runtimeState.queuedCandidates = 0;
        state.runtimeState.lastErrorCode = "";
        minesweeperHandler.reset();
        sudokuHandler.reset();
        blockSwapHandler.reset();
        XPDropHandler.clear(player);
        EZMiner.chainStateService.markSessionStop(playerUUID);
        activeSession = null;
    }

    // ===== Config sync =====

    public void receiveClientConfig(MinerConfig cfg) {
        pConfig.bigRadius = Math.max(0, Math.min(cfg.bigRadius, Config.bigRadius));
        pConfig.blockLimit = Math.max(0, Math.min(cfg.blockLimit, Config.blockLimit));
        pConfig.smallRadius = Math.max(0, Math.min(cfg.smallRadius, Config.smallRadius));
        pConfig.tunnelWidth = Math.max(0, Math.min(cfg.tunnelWidth, Config.tunnelWidth));
        pConfig.useChainDoneMessage = cfg.useChainDoneMessage;
        pConfig.addExhaustion = Config.addExhaustion;
    }

    // ===== Guard =====

    public boolean isSamePlayer(EntityPlayer p) {
        return p.getUniqueID()
            .equals(playerUUID) && p instanceof EntityPlayerMP
            && !p.worldObj.isRemote
            && !(p instanceof FakePlayer);
    }

    private void startChain(Vector3i pos, EntityPlayerMP player) {
        if (operator != null) {
            operator.stopImmediately();
            operator = null;
        }
        this.player = player;
        originPos = pos;
        activeSession = EZMiner.chainStateService.markSessionStart(playerUUID, pos, player.dimension);
        setInOperate(true);
        operator = new BaseOperator(pos, this);
        operator.registry();
    }

    public boolean isSpecialCropMode() {
        return minerModeState.mainMode == 2 && minerModeState.specialMode == 1;
    }

    public boolean isSpecialMinesweeperMode() {
        return minerModeState.mainMode == 2 && minerModeState.specialMode == 0;
    }

    public boolean isSpecialSudokuMode() {
        return minerModeState.mainMode == 2 && minerModeState.specialMode == 2;
    }

    public boolean isBlockSwapMode() {
        return Config.enableBlockSwapMode && minerModeState.mainMode == 2 && minerModeState.specialMode == 3;
    }

    // ── Cached chain sub-mode helpers ──

    /** Delegates to {@link MinerModeState#isCachedChainMode()}. */
    public boolean isCachedChainMode() {
        return minerModeState.isCachedChainMode();
    }

    /** Returns true for the fuzzy variant of cached chain. */
    public boolean isCachedChainFuzzyMode() {
        return minerModeState.isCachedChainMode() && minerModeState.chainMode == 3;
    }

    /**
     * Attempts to start a cached chain operation using the pre-calculated block list.
     *
     * @return true if the cache was valid and the cached chain was started
     */
    private boolean tryStartCachedChain(Vector3i pos, EntityPlayerMP playerMP) {
        CachedEntry entry = ChainPreCalcCache.get(playerUUID);
        if (entry == null || entry.isEmpty()) return false;

        // Validate: player must still be in the same dimension.
        if (playerMP.dimension != entry.dimension) return false;

        // Validate: the broken block must be of the same type as what was pre-calculated,
        // and within reasonable distance of the pre-calc center.
        World world = playerMP.worldObj;
        if (world == null || !world.blockExists(pos.x, pos.y, pos.z)) return false;
        Block block = world.getBlock(pos.x, pos.y, pos.z);

        if (entry.fuzzyTypeClassName != null) {
            // Fuzzy mode: class-based matching (isAssignableFrom).
            boolean fuzzyMatch = false;
            try {
                Class<?> entryClass = Class.forName(entry.fuzzyTypeClassName);
                Class<?> brokenClass = block.getClass();
                fuzzyMatch = entryClass.isAssignableFrom(brokenClass) || brokenClass.isAssignableFrom(entryClass);
            } catch (ClassNotFoundException ignored) {}
            if (!fuzzyMatch) return false;
        } else {
            // Exact mode (Bandit-style): compare by block ID, ignore meta.
            int blockIdHash = ChainPreCalcCache.computeBlockIdHash(Block.getIdFromBlock(block), playerMP.dimension);
            if (blockIdHash != entry.typeHash) return false;
        }

        int dx = pos.x - entry.centerX;
        int dy = pos.y - entry.centerY;
        int dz = pos.z - entry.centerZ;
        int distSq = dx * dx + dy * dy + dz * dz;
        int bigR = pConfig.bigRadius;
        if (distSq > bigR * bigR) return false;

        // Cache is valid — start the cached chain operation.
        if (operator != null) {
            operator.stopImmediately();
            operator = null;
        }
        this.player = playerMP;
        originPos = pos;
        activeSession = EZMiner.chainStateService.markSessionStart(playerUUID, pos, playerMP.dimension);
        setInOperate(true);
        operator = BaseOperator.createCached(pos, this, entry.positions);
        operator.registry();

        // Clear the cache so it's not accidentally reused for another block type.
        ChainPreCalcCache.remove(playerUUID);
        preCalcEngine.stop(playerMP);
        return true;
    }

    public void tickSpecialMode() {
        // ── Minesweeper mode ──
        if (isSpecialMinesweeperMode()) {
            if (!isKeyPressed()) return;
            if (isInOperate() || player == null || player.worldObj == null || player.isDead) return;
            minesweeperHandler.tick(player, playerUUID);
            return;
        }
        // ── Sudoku mode ──
        if (isSpecialSudokuMode()) {
            if (!isKeyPressed()) return;
            if (isInOperate() || player == null || player.worldObj == null || player.isDead) return;
            sudokuHandler.tick(player, playerUUID);
            return;
        }
        // Not in a tickable special mode — do nothing.
        // Cooldown and state are intentionally preserved so that quickly switching
        // to another mode and back cannot bypass the probe interval.
        // They are only cleared in cleanupState() (player disconnect / logout).
    }

    /**
     * Re-sends all previously-flagged mine positions to {@code target}.
     *
     * <p>
     * Called when the player re-presses the chain key in minesweeper mode so that the client's
     * flagged-mine list is repopulated without requiring the server to re-flag the same mines.
     */
    public void resendMinesweeperMarks(EntityPlayerMP target) {
        minesweeperHandler.resendMarks(target);
    }

    public void resendSudokuFills(EntityPlayerMP target) {
        sudokuHandler.resendFills(target);
    }

    public boolean isKeyPressed() {
        return state().keyPressed;
    }

    public boolean isInOperate() {
        return state().runtimeState.inOperate;
    }

    public void setInOperate(boolean inOperate) {
        state().runtimeState.inOperate = inOperate;
    }

    public void updateRuntimeProjection(int chainedCount, long elapsedMs, int queuedCandidates) {
        ChainPlayerState state = state();
        state.runtimeState.inOperate = true;
        state.runtimeState.chainedCount = chainedCount;
        state.runtimeState.elapsedMs = elapsedMs;
        state.runtimeState.queuedCandidates = queuedCandidates;
    }

    public void clearRuntimeProjection() {
        ChainPlayerState state = state();
        state.runtimeState.inOperate = false;
        state.runtimeState.chainedCount = 0;
        state.runtimeState.elapsedMs = 0L;
        state.runtimeState.queuedCandidates = 0;
    }

    public void reportRuntimeError(String errorCode) {
        state().runtimeState.lastErrorCode = errorCode == null ? "" : errorCode;
    }

    private ChainPlayerState state() {
        return EZMiner.chainStateService.getOrCreate(playerUUID);
    }
}
