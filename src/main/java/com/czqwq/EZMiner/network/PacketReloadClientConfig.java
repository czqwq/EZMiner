package com.czqwq.EZMiner.network;

import com.czqwq.EZMiner.Config;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

/** Triggers a client-only config reload from local disk. */
public class PacketReloadClientConfig implements IMessage {

    @Override
    public void fromBytes(ByteBuf buf) {}

    @Override
    public void toBytes(ByteBuf buf) {}

    public static class Handler implements IMessageHandler<PacketReloadClientConfig, IMessage> {

        @Override
        public IMessage onMessage(PacketReloadClientConfig msg, MessageContext ctx) {
            if (ctx.side.isClient()) {
                Config.reloadClientOnly();
            }
            return null;
        }
    }
}
