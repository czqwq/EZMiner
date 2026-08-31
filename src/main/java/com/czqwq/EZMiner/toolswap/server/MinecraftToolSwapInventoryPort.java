package com.czqwq.EZMiner.toolswap.server;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;

/**
 * Real player-inventory adapter for the server main thread.
 */
public final class MinecraftToolSwapInventoryPort implements ToolSwapInventoryPort {

    private final EntityPlayerMP player;

    public MinecraftToolSwapInventoryPort(EntityPlayerMP player) {
        if (player == null) throw new IllegalArgumentException("player must not be null");
        this.player = player;
    }

    @Override
    public boolean isPlayerAlive() {
        return player.worldObj != null && !player.isDead && player.isEntityAlive();
    }

    @Override
    public boolean isCreativeMode() {
        return player.capabilities.isCreativeMode;
    }

    @Override
    public boolean hasPersonalInventoryWindow0() {
        return player.openContainer == player.inventoryContainer && player.inventoryContainer.windowId == 0;
    }

    @Override
    public boolean isCursorEmpty() {
        return player.inventory.getItemStack() == null;
    }

    @Override
    public int selectedHotbarSlot() {
        return player.inventory.currentItem;
    }

    @Override
    public ItemStack readInventorySlot(int slot) {
        requireSlot(slot);
        return player.inventory.mainInventory[slot];
    }

    @Override
    public void swapInventorySlotsAtomically(int slotA, int slotB) {
        requireSlot(slotA);
        requireSlot(slotB);
        if (slotA == slotB) throw new IllegalArgumentException("slots must differ");
        ItemStack tmp = player.inventory.mainInventory[slotA];
        player.inventory.mainInventory[slotA] = player.inventory.mainInventory[slotB];
        player.inventory.mainInventory[slotB] = tmp;
    }

    @Override
    public void syncInventoryDifference() {
        player.inventory.markDirty();
        player.sendContainerToPlayer(player.inventoryContainer);
    }

    private static void requireSlot(int slot) {
        if (slot < 0 || slot >= 36) throw new IllegalArgumentException("inventory slot must be 0..35");
    }
}
