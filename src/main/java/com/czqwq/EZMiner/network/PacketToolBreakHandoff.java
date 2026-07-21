package com.czqwq.EZMiner.network;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.ItemStack;

import com.czqwq.EZMiner.Config;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import io.netty.buffer.ByteBuf;

/**
 * Server → Client: the current tool is about to break during chain mining.
 * The client should switch to the next best available tool in the hotbar.
 *
 * <p>
 * The handler is self-contained (no dependency on {@code SmartToolSwitchHandler})
 * so that it can be loaded on both sides during {@code NetworkMain.registry()}
 * without triggering client-only class loading on the dedicated server.
 * </p>
 */
public class PacketToolBreakHandoff implements IMessage {

    public PacketToolBreakHandoff() {}

    @Override
    public void fromBytes(ByteBuf buf) {
        // No payload needed — the signal itself is the message.
    }

    @Override
    public void toBytes(ByteBuf buf) {
        // No payload.
    }

    /**
     * Handler registered with {@link Side#CLIENT} — invoked only on the client.
     * Must be loadable on the server because {@code SimpleNetworkWrapper#registerMessage}
     * instantiates it immediately (lazy instantiation is not guaranteed in all
     * Forge 1.7.10 builds). This class therefore avoids any reference to
     * {@code @SideOnly(Side.CLIENT)} types.
     */
    public static class Handler implements IMessageHandler<PacketToolBreakHandoff, IMessage> {

        @Override
        @SideOnly(Side.CLIENT)
        public IMessage onMessage(PacketToolBreakHandoff msg, MessageContext ctx) {
            if (!Config.smartToolSwitchEnabled || !Config.enableToolBreakHandoff) return null;

            Minecraft mc = Minecraft.getMinecraft();
            if (mc.thePlayer == null) return null;
            EntityPlayer player = mc.thePlayer;

            int current = player.inventory.currentItem;
            int bestSlot = -1;
            int bestRemaining = 0;

            // Phase 1: scan hotbar for the tool with the most remaining durability
            for (int i = 0; i < InventoryPlayer.getHotbarSize(); i++) {
                if (i == current) continue;
                ItemStack stack = player.inventory.mainInventory[i];
                if (stack == null) continue;
                int remaining = stack.isItemStackDamageable()
                    ? Math.max(0, stack.getMaxDamage() - stack.getItemDamage())
                    : Integer.MAX_VALUE;
                if (remaining <= 1) continue;
                if (remaining > bestRemaining) {
                    bestRemaining = remaining;
                    bestSlot = i;
                }
            }

            // Phase 2: if no hotbar tool and full-inventory mode, scan main inventory
            if (bestSlot < 0 && Config.smartToolSwitchFullInventory) {
                for (int i = InventoryPlayer.getHotbarSize(); i < player.inventory.mainInventory.length; i++) {
                    ItemStack stack = player.inventory.mainInventory[i];
                    if (stack == null) continue;
                    int remaining = stack.isItemStackDamageable()
                        ? Math.max(0, stack.getMaxDamage() - stack.getItemDamage())
                        : Integer.MAX_VALUE;
                    if (remaining <= 1) continue;
                    if (remaining > bestRemaining) {
                        bestRemaining = remaining;
                        bestSlot = i;
                    }
                }
                // If found in main inventory, swap it into the hotbar
                if (bestSlot >= 0) {
                    int target = findEmptyOrWorstHotbarSlot(player, current);
                    if (target >= 0) {
                        ItemStack src = player.inventory.mainInventory[bestSlot];
                        ItemStack tmp = player.inventory.mainInventory[target];
                        player.inventory.mainInventory[target] = src;
                        player.inventory.mainInventory[bestSlot] = tmp;
                        player.inventory.currentItem = target;
                        // Sync the swap to the server so the item isn't a ghost
                        com.czqwq.EZMiner.EZMiner.network.network
                            .sendToServer(new com.czqwq.EZMiner.network.PacketInventorySwap(target, bestSlot));
                    }
                    return null;
                }
            }

            if (bestSlot >= 0) {
                player.inventory.currentItem = bestSlot;
            }
            return null;
        }

        /** Finds an empty or least-important hotbar slot, excluding the given current slot. */
        @SideOnly(Side.CLIENT)
        private static int findEmptyOrWorstHotbarSlot(EntityPlayer player, int excludeSlot) {
            int hotbarSize = InventoryPlayer.getHotbarSize();
            for (int i = 0; i < hotbarSize; i++) {
                if (i != excludeSlot && player.inventory.mainInventory[i] == null) return i;
            }
            // No empty slot — return any slot that's not the current one
            for (int i = 0; i < hotbarSize; i++) {
                if (i != excludeSlot) return i;
            }
            return -1;
        }
    }
}
