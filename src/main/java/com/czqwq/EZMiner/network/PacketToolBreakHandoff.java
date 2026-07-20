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
            ItemStack held = player.inventory.mainInventory[current];

            // Find the next hotbar slot (different from current) that has a non-empty,
            // damageable (i.e. tool-like) item. Simple but effective — the server only
            // sends this packet when the current tool is truly about to break.
            int hotbarSize = InventoryPlayer.getHotbarSize();
            for (int i = 0; i < hotbarSize; i++) {
                int slot = (current + i + 1) % hotbarSize;
                ItemStack stack = player.inventory.mainInventory[slot];
                if (stack != null && stack.isItemStackDamageable()) {
                    int remaining = stack.getMaxDamage() - stack.getItemDamage();
                    if (remaining > 1) {
                        player.inventory.currentItem = slot;
                        return null;
                    }
                }
            }
            return null;
        }
    }
}
