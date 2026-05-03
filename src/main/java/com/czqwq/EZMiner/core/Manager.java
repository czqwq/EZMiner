package com.czqwq.EZMiner.core;

import java.util.UUID;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.world.BlockEvent;

import org.joml.Vector3i;

import com.czqwq.EZMiner.Config;
import com.czqwq.EZMiner.EZMiner;
import com.czqwq.EZMiner.chain.execution.ChainDropCollector;
import com.czqwq.EZMiner.chain.execution.MinesweeperModeHandler;
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
        if (isBlastCropMode()) return;
        startChain(new Vector3i(event.x, event.y, event.z), (EntityPlayerMP) event.getPlayer());
    }

    // Must receive canceled events because some crop/interaction mods cancel
    // RIGHT_CLICK_BLOCK before EZMiner runs; we still need to start chain harvest.
    @SubscribeEvent(priority = EventPriority.HIGHEST, receiveCanceled = true)
    public void onCropRightClick(PlayerInteractEvent event) {
        if (event.action != PlayerInteractEvent.Action.RIGHT_CLICK_BLOCK) return;
        if (!isSamePlayer(event.entityPlayer)) return;
        if (isInOperate() || !isKeyPressed()) return;
        if (!isBlastCropMode()) return;
        if (!CropFounder.isMatureCrop(event.entityPlayer.worldObj, event.x, event.y, event.z)) return;
        startChain(new Vector3i(event.x, event.y, event.z), (EntityPlayerMP) event.entityPlayer);
        // Explicitly consume the interaction so vanilla/sibling handlers do not
        // perform a second single-crop right-click harvest.
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
    }

    // ===== Tick: flush drops after chain ends =====

    @SubscribeEvent
    public void onWorldTick(TickEvent.WorldTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;
        if (!isInOperate() && !isKeyPressed()) flushDrops();
    }

    public void flushDrops() {
        if (dropCollector.isEmpty()) return;
        if (player == null || player.worldObj == null) {
            dropCollector.clear();
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
        dropCollector.flush(player.worldObj, spawnX, spawnY, spawnZ);
    }

    /** Clears all accumulated drops from the drop collector. */
    public void clearDrops() {
        dropCollector.clear();
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
        ChainPlayerState state = state();
        state.keyPressed = false;
        state.runtimeState.inOperate = false;
        state.runtimeState.chainedCount = 0;
        state.runtimeState.elapsedMs = 0L;
        state.runtimeState.queuedCandidates = 0;
        state.runtimeState.lastErrorCode = "";
        minesweeperHandler.reset();
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

    public boolean isBlastCropMode() {
        return minerModeState.mainMode == 0 && minerModeState.blastMode == 5;
    }

    public boolean isSpecialMinesweeperMode() {
        return minerModeState.mainMode == 2 && minerModeState.specialMode == 0;
    }

    public void tickSpecialMode() {
        if (!isSpecialMinesweeperMode()) {
            // Not in minesweeper mode — do nothing.
            // The cooldown timer and detected-bomb set are intentionally preserved so that
            // quickly switching to another mode and back cannot bypass the probe interval.
            // They are only cleared in cleanupState() (player disconnect / logout).
            return;
        }
        // Key not held: keep the cooldown timer intact so quickly releasing and
        // re-pressing the key cannot bypass the configured probe interval.
        if (!isKeyPressed()) return;
        if (isInOperate() || player == null || player.worldObj == null || player.isDead) return;
        minesweeperHandler.tick(player, playerUUID);
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
