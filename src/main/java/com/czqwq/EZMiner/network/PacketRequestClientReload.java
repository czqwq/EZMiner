package com.czqwq.EZMiner.network;

import net.minecraft.entity.player.EntityPlayerMP;

import com.czqwq.EZMiner.EZMiner;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

/**
 * Client → server: request a client-config reload.
 *
 * <p>
 * Equivalent to {@code /EZMiner reloadClientConfig}. The server responds by
 * sending the requesting player fresh {@link PacketServerConfig} limits
 * followed by a {@link PacketReloadClientConfig} trigger.
 */
public class PacketRequestClientReload implements IMessage {

    @Override
    public void fromBytes(ByteBuf buf) {}

    @Override
    public void toBytes(ByteBuf buf) {}

    public static class Handler implements IMessageHandler<PacketRequestClientReload, IMessage> {

        @Override
        public IMessage onMessage(PacketRequestClientReload msg, MessageContext ctx) {
            if (!ctx.side.isServer()) return null;
            EntityPlayerMP player = ctx.getServerHandler().playerEntity;
            EZMiner.network.network.sendTo(PacketServerConfig.buildForPlayer(player), player);
            EZMiner.network.network.sendTo(new PacketReloadClientConfig(), player);
            return null;
        }
    }
}
