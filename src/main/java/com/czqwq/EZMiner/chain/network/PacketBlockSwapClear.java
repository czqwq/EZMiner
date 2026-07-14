package com.czqwq.EZMiner.chain.network;

import com.czqwq.EZMiner.ClientProxy;
import com.czqwq.EZMiner.EZMiner;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

/**
 * Server → Client: clears all client-side block-swap state (result counter).
 * Sent when the chain key is released.
 */
public class PacketBlockSwapClear implements IMessage {

    public PacketBlockSwapClear() {}

    @Override
    public void fromBytes(ByteBuf buf) {}

    @Override
    public void toBytes(ByteBuf buf) {}

    public static class Handler implements IMessageHandler<PacketBlockSwapClear, IMessage> {

        @Override
        public IMessage onMessage(PacketBlockSwapClear msg, MessageContext ctx) {
            if (EZMiner.proxy instanceof ClientProxy) {
                ClientProxy proxy = (ClientProxy) EZMiner.proxy;
                proxy.clientState.blockSwapResultCount = 0;
                proxy.clientState.blockSwapResultTimestamp = 0L;
            }
            return null;
        }
    }
}
