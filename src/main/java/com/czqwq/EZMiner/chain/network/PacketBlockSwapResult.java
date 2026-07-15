package com.czqwq.EZMiner.chain.network;

import com.czqwq.EZMiner.ClientProxy;
import com.czqwq.EZMiner.EZMiner;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

/**
 * Server → Client: notifies the client of the number of blocks swapped in the
 * last block-swap operation. The client uses this to show a brief result in the HUD.
 */
public class PacketBlockSwapResult implements IMessage {

    public int swappedCount;

    public PacketBlockSwapResult() {}

    public PacketBlockSwapResult(int swappedCount) {
        this.swappedCount = swappedCount;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        swappedCount = buf.readInt();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(swappedCount);
    }

    public static class Handler implements IMessageHandler<PacketBlockSwapResult, IMessage> {

        @Override
        public IMessage onMessage(PacketBlockSwapResult msg, MessageContext ctx) {
            if (EZMiner.proxy instanceof ClientProxy) {
                ClientProxy proxy = (ClientProxy) EZMiner.proxy;
                proxy.clientState.blockSwapResultCount = msg.swappedCount;
                proxy.clientState.blockSwapResultTimestamp = System.currentTimeMillis();
            }
            return null;
        }
    }
}
