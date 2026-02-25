package com.czqwq.EZMiner.network;

import com.czqwq.EZMiner.ClientProxy;
import com.czqwq.EZMiner.EZMiner;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import io.netty.buffer.ByteBuf;

/**
 * Server → Client: real-time update of how many blocks have been mined in the current
 * chain operation. Sent once per operator tick and once when the operation ends.
 */
public class PacketChainCount implements IMessage {

    public int count;

    public PacketChainCount() {}

    public PacketChainCount(int count) {
        this.count = count;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        count = buf.readInt();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(count);
    }

    @SideOnly(Side.CLIENT)
    public static class Handler implements IMessageHandler<PacketChainCount, IMessage> {

        @Override
        public IMessage onMessage(PacketChainCount msg, MessageContext ctx) {
            // chainedBlockCount is volatile – safe to write from the Netty IO thread.
            if (EZMiner.proxy instanceof ClientProxy) {
                ((ClientProxy) EZMiner.proxy).clientState.chainedBlockCount = msg.count;
            }
            return null;
        }
    }
}
