package com.czqwq.EZMiner.network;

import net.minecraft.entity.player.EntityPlayerMP;

import com.czqwq.EZMiner.EZMiner;
import com.czqwq.EZMiner.permission.OpPermissionChecker;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

/**
 * Client → server: request a fresh OP status check.
 *
 * <p>
 * Sent by the EZMiner config GUI every time it opens so that the client's
 * {@link EZMiner#clientIsOp} reflects the current server state (e.g. after the host has opened
 * the world to LAN with cheats).
 */
public class PacketOpStatusRequest implements IMessage {

    public PacketOpStatusRequest() {}

    @Override
    public void fromBytes(ByteBuf buf) {
        // no data
    }

    @Override
    public void toBytes(ByteBuf buf) {
        // no data
    }

    public static class Handler implements IMessageHandler<PacketOpStatusRequest, IMessage> {

        @Override
        public IMessage onMessage(PacketOpStatusRequest msg, MessageContext ctx) {
            if (!ctx.side.isServer()) return null;
            EntityPlayerMP player = ctx.getServerHandler().playerEntity;
            boolean isOp = OpPermissionChecker.isOp(player);
            EZMiner.network.network.sendTo(new PacketOpStatusResponse(isOp), player);
            return null;
        }
    }
}
