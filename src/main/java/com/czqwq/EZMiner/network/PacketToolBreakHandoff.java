package com.czqwq.EZMiner.network;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;

import com.czqwq.EZMiner.Config;
import com.czqwq.EZMiner.client.SmartToolSwitchHandler;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import io.netty.buffer.ByteBuf;

/**
 * Server → Client: the current tool is about to break during chain mining.
 * The client should switch to the next best available tool in the hotbar.
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

    @SideOnly(Side.CLIENT)
    public static class Handler implements IMessageHandler<PacketToolBreakHandoff, IMessage> {

        @Override
        public IMessage onMessage(PacketToolBreakHandoff msg, MessageContext ctx) {
            if (!ctx.side.isClient()) return null;
            if (!Config.smartToolSwitchEnabled || !Config.enableToolBreakHandoff) return null;

            Minecraft mc = Minecraft.getMinecraft();
            if (mc.thePlayer == null) return null;
            EntityPlayer player = mc.thePlayer;

            // Find the next best tool different from the current one
            SmartToolSwitchHandler handler = SmartToolSwitchHandler.getActiveInstance();
            if (handler == null) return null;

            handler.performToolBreakHandoff(player);
            return null;
        }
    }
}
