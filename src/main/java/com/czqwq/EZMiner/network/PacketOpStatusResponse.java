package com.czqwq.EZMiner.network;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

/**
 * Server → client: response carrying the server's OP determination for the requesting player.
 */
public class PacketOpStatusResponse implements IMessage {

    public boolean isOp;

    public PacketOpStatusResponse() {}

    public PacketOpStatusResponse(boolean isOp) {
        this.isOp = isOp;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        isOp = buf.readBoolean();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeBoolean(isOp);
    }

    public static class Handler implements IMessageHandler<PacketOpStatusResponse, IMessage> {

        @Override
        public IMessage onMessage(PacketOpStatusResponse msg, MessageContext ctx) {
            if (ctx.side.isClient()) {
                com.czqwq.EZMiner.EZMiner.clientIsOp = msg.isOp;
            }
            return null;
        }
    }
}
