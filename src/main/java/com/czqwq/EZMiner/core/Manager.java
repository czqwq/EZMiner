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
import com.czqwq.EZMiner.core.founder.DeterminingIdentical;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

/**
 * Per-player chain mining manager.
 * Listens for block break events and manages the operator lifecycle.
 * All mutation happens on the server thread.
 */
public class Manager {

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
     * World position of the first block that triggered this chain operation.
     * Used as the drop spawn point when {@link Config#dropToInventory} is false.
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
        operator = new BaseOperator(pos, this);
        operator.registry();
    }

    // ===== Drop Collection =====

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onHarvestDrops(BlockEvent.HarvestDropsEvent event) {
        if (event.harvester == null || !isSamePlayer(event.harvester)) return;
        if (!inOperate) return;

        for (ItemStack drop : event.drops) {
            if (drop == null || drop.stackSize <= 0) continue;
            int remaining = drop.stackSize;
            // Try to fill into existing matching stacks first (respecting maxStackSize).
            for (ItemStack existing : drops) {
                if (!DeterminingIdentical.isSame(existing, drop)) continue;
                int space = existing.getMaxStackSize() - existing.stackSize;
                if (space <= 0) continue;
                int toAdd = Math.min(space, remaining);
                existing.stackSize += toAdd;
                remaining -= toAdd;
                if (remaining <= 0) break;
            }
            // Whatever didn't fit: create new stack(s), each capped at maxStackSize.
            while (remaining > 0) {
                int take = Math.min(remaining, drop.getMaxStackSize());
                ItemStack newStack = drop.copy();
                newStack.stackSize = take;
                drops.add(newStack);
                remaining -= take;
            }
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
        // Determine drop position:
        // dropToInventory=true → at the player's current feet position
        // dropToInventory=false → at the center of the originally mined block
        double spawnX, spawnY, spawnZ;
        if (Config.dropToInventory || originPos == null) {
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
            // Ensure each spawned entity has a valid stack size.
            int remaining = stack.stackSize;
            while (remaining > 0) {
                int take = Math.min(remaining, stack.getMaxStackSize());
                ItemStack toSpawn = stack.copy();
                toSpawn.stackSize = take;
                player.worldObj.spawnEntityInWorld(new EntityItem(player.worldObj, spawnX, spawnY, spawnZ, toSpawn));
                remaining -= take;
            }
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
    }

    @SubscribeEvent
    public void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        // Each Manager is bound to a specific player – ignore events for other players.
        if (!event.player.getUniqueID()
            .equals(playerUUID)) return;
        // Stop the operator immediately so no further server-tick callbacks fire
        // against the now-invalid player entity.
        if (operator != null) {
            operator.stopImmediately();
            operator = null;
        }
        // Discard pending drops – the player is gone and items cannot be delivered.
        drops.clear();
        inPressChainKey = false;
        inOperate = false;
    }

    // ===== Config sync =====

    public void receiveClientConfig(MinerConfig cfg) {
        pConfig.bigRadius = Math.max(0, Math.min(cfg.bigRadius, Config.bigRadius));
        pConfig.blockLimit = Math.max(0, Math.min(cfg.blockLimit, Config.blockLimit));
        pConfig.smallRadius = Math.max(0, Math.min(cfg.smallRadius, Config.smallRadius));
        pConfig.tunnelWidth = Math.max(0, Math.min(cfg.tunnelWidth, Config.tunnelWidth));
        pConfig.useChainDoneMessage = cfg.useChainDoneMessage;
    }

    // ===== Guard =====

    public boolean isSamePlayer(EntityPlayer p) {
        return p.getUniqueID()
            .equals(playerUUID) && p instanceof EntityPlayerMP
            && !p.worldObj.isRemote
            && !(p instanceof FakePlayer);
    }
}
