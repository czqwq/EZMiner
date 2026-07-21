package com.czqwq.EZMiner.network;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

/**
 * Client → Server: swap two slots in the player's personal inventory.
 * <p>
 * Sent by {@code SmartToolSwitchHandler} after a client-side tool swap so the
 * server stays in sync. Without this, client-only swaps produce "ghost items" —
 * the client thinks the tool moved but the server doesn't, so the next block
 * break or inventory open reverts the slot.
 */
public class PacketInventorySwap implements IMessage {

    private int slotA;
    private int slotB;

    public PacketInventorySwap() {}

    /**
     * @param slotA first inventory slot (0–35)
     * @param slotB second inventory slot (0–35)
     */
    public PacketInventorySwap(int slotA, int slotB) {
        this.slotA = slotA;
        this.slotB = slotB;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        slotA = buf.readByte();
        slotB = buf.readByte();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeByte(slotA);
        buf.writeByte(slotB);
    }

    public static class Handler implements IMessageHandler<PacketInventorySwap, IMessage> {

        @Override
        public IMessage onMessage(PacketInventorySwap msg, MessageContext ctx) {
            if (!ctx.side.isServer()) return null;
            EntityPlayerMP player = ctx.getServerHandler().playerEntity;
            if (player == null) return null;

            int a = msg.slotA;
            int b = msg.slotB;
            if (a < 0 || a >= player.inventory.mainInventory.length
                || b < 0
                || b >= player.inventory.mainInventory.length
                || a == b) return null;

            // Atomically swap the two slots on the server side
            ItemStack tmp = player.inventory.mainInventory[a];
            player.inventory.mainInventory[a] = player.inventory.mainInventory[b];
            player.inventory.mainInventory[b] = tmp;

            // Mark dirty and sync back to the client
            player.inventory.markDirty();
            player.inventoryContainer.detectAndSendChanges();
            return null;
        }
    }
}
