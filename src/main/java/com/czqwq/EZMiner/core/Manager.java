package com.czqwq.EZMiner.core;

import java.util.ArrayList;
import java.util.UUID;

import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.world.BlockEvent;

import org.joml.Vector3i;

import com.czqwq.EZMiner.Config;
import com.czqwq.EZMiner.EZMiner;
import com.czqwq.EZMiner.core.founder.DeterminingIdentical;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.Loader;
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
    public MinerConfig pConfig = new MinerConfig();
    public MinerModeState minerModeState = new MinerModeState();

    /** True while the player holds the chain key. */
    public volatile boolean inPressChainKey = false;
    /** True while a chain operation is executing. */
    public volatile boolean inOperate = false;

    public BaseOperator operator = null;

    /**
     * Position of the first block broken in this chain; used as the drop-spawn point when {@code dropToPlayer} is
     * false.
     */
    public Vector3i originPos = null;

    /** Collected drops during the current chain operation. */
    public final ArrayList<ItemStack> drops = new ArrayList<>();

    public Manager(EntityPlayerMP player) {
        this.player = player;
        this.playerUUID = player.getUniqueID();
    }

    // ===== Block Break Trigger =====

    @SubscribeEvent
    public void onBlockBreak(BlockEvent.BreakEvent event) {
        if (!isSamePlayer(event.getPlayer())) return;
        if (inOperate || !inPressChainKey) return;
        inOperate = true;
        player = (EntityPlayerMP) event.getPlayer();
        Vector3i pos = new Vector3i(event.x, event.y, event.z);
        originPos = pos;
        EZMiner.chainStateService.markSessionStart(playerUUID, pos, player.dimension);
        operator = new BaseOperator(pos, this);
        operator.registry();
    }

    // ===== Drop Collection =====

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onHarvestDrops(BlockEvent.HarvestDropsEvent event) {
        if (event.harvester == null || !isSamePlayer(event.harvester)) return;
        if (!inOperate) return;
        // When Bandit is loaded it collects drops via EntityJoinWorldEvent inside its own
        // HarvestCollector scope. Clearing event.drops here would prevent those EntityItems
        // from ever being spawned, leaving Bandit with zero drops. Yield to Bandit so that
        // items drop normally and Bandit can intercept them as designed.
        if (BANDIT_LOADED) return;

        for (ItemStack drop : event.drops) {
            if (drop == null || drop.stackSize <= 0) continue;
            boolean merged = false;
            for (ItemStack existing : drops) {
                if (!DeterminingIdentical.isSame(existing, drop)) continue;
                // Accumulate into one super-stack regardless of maxStackSize.
                // Fewer EntityItems = less server lag when mining large veins.
                existing.stackSize += drop.stackSize;
                merged = true;
                break;
            }
            if (!merged) drops.add(drop.copy());
        }
        event.drops.clear();
    }

    // ===== Tick: flush drops after chain ends =====

    @SubscribeEvent
    public void onWorldTick(TickEvent.WorldTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;
        if (!inOperate && !inPressChainKey) flushDrops();
    }

    public void flushDrops() {
        if (drops.isEmpty()) return;
        if (player == null || player.worldObj == null) {
            drops.clear();
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
        for (ItemStack stack : drops) {
            if (stack == null || stack.stackSize <= 0) continue;
            player.worldObj.spawnEntityInWorld(new EntityItem(player.worldObj, spawnX, spawnY, spawnZ, stack));
        }
        drops.clear();
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
        inPressChainKey = false;
        inOperate = false;
        EZMiner.chainStateService.markSessionStop(playerUUID);
    }

    // ===== Config sync =====

    public void receiveClientConfig(MinerConfig cfg) {
        pConfig.bigRadius = Math.max(0, Math.min(cfg.bigRadius, Config.bigRadius));
        pConfig.blockLimit = Math.max(0, Math.min(cfg.blockLimit, Config.blockLimit));
        pConfig.smallRadius = Math.max(0, Math.min(cfg.smallRadius, Config.smallRadius));
        pConfig.tunnelWidth = Math.max(0, Math.min(cfg.tunnelWidth, Config.tunnelWidth));
        pConfig.useChainDoneMessage = cfg.useChainDoneMessage;
        // addExhaustion is a client-side preference (replaces vanilla mining exhaustion).
        // Clamp to [-1.0, 1.0] to prevent degenerate values; no further server cap needed.
        pConfig.addExhaustion = Math.max(-1.0, Math.min(cfg.addExhaustion, 1.0));
    }

    // ===== Guard =====

    public boolean isSamePlayer(EntityPlayer p) {
        return p.getUniqueID()
            .equals(playerUUID) && p instanceof EntityPlayerMP
            && !p.worldObj.isRemote
            && !(p instanceof FakePlayer);
    }
}
