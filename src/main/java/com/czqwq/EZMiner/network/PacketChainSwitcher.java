package com.czqwq.EZMiner.network;

import net.minecraft.entity.player.EntityPlayerMP;

import com.czqwq.EZMiner.core.Manager;
import com.czqwq.EZMiner.core.PlayerManager;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

public class PacketChainSwitcher implements IMessage {

    public boolean inChain;

    public PacketChainSwitcher() {}

    public PacketChainSwitcher(boolean inChain) {
        this.inChain = inChain;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        inChain = buf.readBoolean();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeBoolean(inChain);
    }

    public static class Handler implements IMessageHandler<PacketChainSwitcher, IMessage> {

        @Override
        public IMessage onMessage(PacketChainSwitcher msg, MessageContext ctx) {
            if (ctx.side.isServer()) {
                EntityPlayerMP player = ctx.getServerHandler().playerEntity;
                Manager mgr = PlayerManager.instance.managers.get(player.getUniqueID());
                if (mgr != null) mgr.inPressChainKey = msg.inChain;
            }
            return null;
        }
    }
}
