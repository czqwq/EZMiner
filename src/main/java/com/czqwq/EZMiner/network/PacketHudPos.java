package com.czqwq.EZMiner.network;

import com.czqwq.EZMiner.Config;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

public class PacketHudPos implements IMessage {

    public int x;
    public int z;

    public PacketHudPos() {}

    public PacketHudPos(int x, int z) {
        this.x = x;
        this.z = z;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        x = buf.readInt();
        z = buf.readInt();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(x);
        buf.writeInt(z);
    }

    public static class Handler implements IMessageHandler<PacketHudPos, IMessage> {

        @Override
        public IMessage onMessage(PacketHudPos msg, MessageContext ctx) {
            if (ctx.side.isClient()) {
                Config.saveHudPos(msg.x, msg.z);
            }
            return null;
        }
    }
}
