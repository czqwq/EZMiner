package com.czqwq.EZMiner.core;

import java.util.ArrayList;
import java.util.UUID;

import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.IInventory;
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
        operator = new BaseOperator(pos, this);
        operator.registry();
    }

    // ===== Drop Collection =====

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onHarvestDrops(BlockEvent.HarvestDropsEvent event) {
        if (event.harvester == null || !isSamePlayer(event.harvester)) return;
        if (!inOperate) return;

        for (ItemStack drop : event.drops) {
            boolean merged = false;
            for (ItemStack existing : new ArrayList<>(drops)) {
                if (!DeterminingIdentical.isSame(existing, drop)) continue;
                existing.stackSize += drop.stackSize;
                drop.stackSize = 0;
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
        for (ItemStack stack : drops) {
            if (stack == null || stack.stackSize <= 0) continue;
            if (Config.dropToInventory) {
                giveItemToPlayer(stack);
            } else {
                spawnItemAt(stack);
            }
        }
        drops.clear();
    }

    private void giveItemToPlayer(ItemStack stack) {
        // Try each inventory slot; overflow falls at player feet
        IInventory inv = player.inventory;
        ItemStack remainder = stack;
        for (int i = 0; i < inv.getSizeInventory() && remainder != null && remainder.stackSize > 0; i++) {
            ItemStack slot = inv.getStackInSlot(i);
            if (slot == null) {
                inv.setInventorySlotContents(i, remainder.copy());
                remainder = null;
            } else if (DeterminingIdentical.isSame(slot, remainder)) {
                int space = slot.getMaxStackSize() - slot.stackSize;
                if (space > 0) {
                    int toAdd = Math.min(space, remainder.stackSize);
                    slot.stackSize += toAdd;
                    remainder.stackSize -= toAdd;
                }
            }
        }
        player.inventory.markDirty();
        if (remainder != null && remainder.stackSize > 0) {
            spawnItemAt(remainder);
        }
    }

    private void spawnItemAt(ItemStack stack) {
        player.worldObj
            .spawnEntityInWorld(new EntityItem(player.worldObj, player.posX, player.posY, player.posZ, stack));
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
        cleanupState();
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
