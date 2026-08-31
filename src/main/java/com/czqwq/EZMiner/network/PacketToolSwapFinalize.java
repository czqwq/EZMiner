package com.czqwq.EZMiner.network;

import net.minecraft.entity.player.EntityPlayerMP;

import com.czqwq.EZMiner.toolswap.server.ToolSwapServerService;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

/**
 * Client → Server: smart-tool-switch mode was deactivated. The server restores
 * any outstanding tool borrows from its physical ledger, so the original hotbar
 * layout is returned even when no chain is running.
 */
public class PacketToolSwapFinalize implements IMessage {

    public PacketToolSwapFinalize() {}

    @Override
    public void fromBytes(ByteBuf buf) {}

    @Override
    public void toBytes(ByteBuf buf) {}

    public static class Handler implements IMessageHandler<PacketToolSwapFinalize, IMessage> {

        @Override
        public IMessage onMessage(PacketToolSwapFinalize msg, MessageContext ctx) {
            if (!ctx.side.isServer()) return null;
            EntityPlayerMP player = ctx.getServerHandler().playerEntity;
            if (player == null) return null;
            ToolSwapServerService.finalize(player);
            return null;
        }
    }
}
