package com.czqwq.EZMiner.network;

import net.minecraft.entity.player.EntityPlayerMP;

import com.czqwq.EZMiner.Config;
import com.czqwq.EZMiner.core.Manager;
import com.czqwq.EZMiner.core.MinerConfig;
import com.czqwq.EZMiner.core.PlayerManager;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

public class PacketMinerConfig implements IMessage {

    public MinerConfig minerConfig = new MinerConfig();

    public PacketMinerConfig() {}

    public PacketMinerConfig(MinerConfig cfg) {
        this.minerConfig = cfg;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        minerConfig.bigRadius = buf.readInt();
        minerConfig.blockLimit = buf.readInt();
        minerConfig.smallRadius = buf.readInt();
        minerConfig.tunnelWidth = buf.readInt();
        minerConfig.useChainDoneMessage = buf.readBoolean();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(minerConfig.bigRadius);
        buf.writeInt(minerConfig.blockLimit);
        buf.writeInt(minerConfig.smallRadius);
        buf.writeInt(minerConfig.tunnelWidth);
        buf.writeBoolean(minerConfig.useChainDoneMessage);
    }

    public static class Handler implements IMessageHandler<PacketMinerConfig, IMessage> {

        @Override
        public IMessage onMessage(PacketMinerConfig msg, MessageContext ctx) {
            if (ctx.side.isServer()) {
                EntityPlayerMP player = ctx.getServerHandler().playerEntity;
                Manager mgr = PlayerManager.instance.managers.get(player.getUniqueID());
                if (mgr != null) {
                    mgr.receiveClientConfig(msg.minerConfig);
                    // Echo validated config back
                    return new PacketMinerConfig(mgr.pConfig);
                }
            } else if (ctx.side.isClient()) {
                // Update local config with server-validated values
                Config.bigRadius = msg.minerConfig.bigRadius;
                Config.blockLimit = msg.minerConfig.blockLimit;
                Config.smallRadius = msg.minerConfig.smallRadius;
                Config.tunnelWidth = msg.minerConfig.tunnelWidth;
                Config.useChainDoneMessage = msg.minerConfig.useChainDoneMessage;
            }
            return null;
        }
    }
}
