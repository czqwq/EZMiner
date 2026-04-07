package com.czqwq.EZMiner.network;

import com.czqwq.EZMiner.ClientProxy;
import com.czqwq.EZMiner.EZMiner;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

/**
 * Server → Client: real-time update of how many blocks have been mined in the current
 * chain operation, plus elapsed time in milliseconds.
 * Sent once per operator tick and a final reset (count=0, elapsedMs=0) when the operation ends.
 */
public class PacketChainCount implements IMessage {

    public int count;
    public long elapsedMs;

    public PacketChainCount() {}

    public PacketChainCount(int count, long elapsedMs) {
        this.count = count;
        this.elapsedMs = elapsedMs;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        count = buf.readInt();
        elapsedMs = buf.readLong();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(count);
        buf.writeLong(elapsedMs);
    }

    public static class Handler implements IMessageHandler<PacketChainCount, IMessage> {

        @Override
        public IMessage onMessage(PacketChainCount msg, MessageContext ctx) {
            if (EZMiner.proxy instanceof ClientProxy) {
                ClientProxy proxy = (ClientProxy) EZMiner.proxy;
                proxy.clientState.chainedBlockCount = msg.count;
                proxy.clientState.chainElapsedMs = msg.elapsedMs;
            }
            return null;
        }
    }
}
