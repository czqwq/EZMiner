package com.czqwq.EZMiner.network;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.ChatComponentTranslation;

import com.czqwq.EZMiner.Config;
import com.czqwq.EZMiner.EZMiner;
import com.czqwq.EZMiner.core.Manager;
import com.czqwq.EZMiner.core.MinerConfig;
import com.czqwq.EZMiner.core.PlayerManager;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

/**
 * Client → server: request a full server config reload.
 *
 * <p>
 * Equivalent to {@code /EZMiner reloadConfig}. The server validates that the
 * requesting player has OP permission (level 2) before reloading.
 */
public class PacketReloadServerConfig implements IMessage {

    @Override
    public void fromBytes(ByteBuf buf) {}

    @Override
    public void toBytes(ByteBuf buf) {}

    public static class Handler implements IMessageHandler<PacketReloadServerConfig, IMessage> {

        @Override
        public IMessage onMessage(PacketReloadServerConfig msg, MessageContext ctx) {
            if (!ctx.side.isServer()) return null;
            EntityPlayerMP player = ctx.getServerHandler().playerEntity;
            if (!player.canCommandSenderUseCommand(2, "EZMiner")) return null;
            Config.load();
            if (PlayerManager.instance != null) {
                for (Manager mgr : PlayerManager.instance.managers.values()) {
                    mgr.pConfig.updateFrom(new MinerConfig());
                    EZMiner.network.network.sendTo(new PacketMinerConfig(mgr.pConfig), mgr.player);
                    EZMiner.network.network.sendTo(PacketServerConfig.buildForPlayer(mgr.player), mgr.player);
                    EZMiner.network.network.sendTo(new PacketReloadClientConfig(), mgr.player);
                }
            }
            EZMiner.LOG.info("EZMiner config reloaded (via GUI) by {}.", player.getDisplayName());
            player.addChatMessage(new ChatComponentTranslation("ezminer.command.reloadconfig.success"));
            return null;
        }
    }
}
