package com.czqwq.EZMiner.network;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;

import com.czqwq.EZMiner.Config;
import com.czqwq.EZMiner.client.SmartToolSwitchHandler;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
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

    public static class Handler implements IMessageHandler<PacketToolBreakHandoff, IMessage> {

        @Override
        public IMessage onMessage(PacketToolBreakHandoff msg, MessageContext ctx) {
            // This handler is only ever invoked on the client because the packet is
            // registered with Side.CLIENT. The side check here is defensive — the
            // handler class must be loadable on both sides because NetworkMain.registry()
            // references Handler.class during preInit (which runs on both client and server).
            if (!ctx.side.isClient()) return null;
            if (!Config.smartToolSwitchEnabled || !Config.enableToolBreakHandoff) return null;

            Minecraft mc = Minecraft.getMinecraft();
            if (mc.thePlayer == null) return null;
            EntityPlayer player = mc.thePlayer;

            SmartToolSwitchHandler handler = SmartToolSwitchHandler.getActiveInstance();
            if (handler == null) return null;

            handler.performToolBreakHandoff(player);
            return null;
        }
    }
}
