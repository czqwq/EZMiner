package com.czqwq.EZMiner.network;

import net.minecraft.entity.player.EntityPlayerMP;

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
 * Client → server: save modified server config values.
 *
 * <p>
 * Sent from the OP-only "Server Settings" tab of the EZMiner config GUI.
 * The server validates OP permission before applying any changes.
 */
public class PacketSaveServerConfig implements IMessage {

    public int bigRadius;
    public int blockLimit;
    public int smallRadius;
    public int tunnelWidth;
    public int breakPerTick;
    public double addExhaustion;
    public boolean dropToPlayer;
    public boolean serverUsePreview;
    public int serverMaxPreviewBigRadius;
    public int serverMaxPreviewBlockLimit;
    public int minesweeperProbeCooldownSeconds;

    public PacketSaveServerConfig() {}

    public PacketSaveServerConfig(int bigRadius, int blockLimit, int smallRadius, int tunnelWidth, int breakPerTick,
        double addExhaustion, boolean dropToPlayer, boolean serverUsePreview, int serverMaxPreviewBigRadius,
        int serverMaxPreviewBlockLimit, int minesweeperProbeCooldownSeconds) {
        this.bigRadius = bigRadius;
        this.blockLimit = blockLimit;
        this.smallRadius = smallRadius;
        this.tunnelWidth = tunnelWidth;
        this.breakPerTick = breakPerTick;
        this.addExhaustion = addExhaustion;
        this.dropToPlayer = dropToPlayer;
        this.serverUsePreview = serverUsePreview;
        this.serverMaxPreviewBigRadius = serverMaxPreviewBigRadius;
        this.serverMaxPreviewBlockLimit = serverMaxPreviewBlockLimit;
        this.minesweeperProbeCooldownSeconds = minesweeperProbeCooldownSeconds;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        bigRadius = buf.readInt();
        blockLimit = buf.readInt();
        smallRadius = buf.readInt();
        tunnelWidth = buf.readInt();
        breakPerTick = buf.readInt();
        addExhaustion = buf.readDouble();
        dropToPlayer = buf.readBoolean();
        serverUsePreview = buf.readBoolean();
        serverMaxPreviewBigRadius = buf.readInt();
        serverMaxPreviewBlockLimit = buf.readInt();
        minesweeperProbeCooldownSeconds = buf.readInt();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(bigRadius);
        buf.writeInt(blockLimit);
        buf.writeInt(smallRadius);
        buf.writeInt(tunnelWidth);
        buf.writeInt(breakPerTick);
        buf.writeDouble(addExhaustion);
        buf.writeBoolean(dropToPlayer);
        buf.writeBoolean(serverUsePreview);
        buf.writeInt(serverMaxPreviewBigRadius);
        buf.writeInt(serverMaxPreviewBlockLimit);
        buf.writeInt(minesweeperProbeCooldownSeconds);
    }

    public static class Handler implements IMessageHandler<PacketSaveServerConfig, IMessage> {

        @Override
        public IMessage onMessage(PacketSaveServerConfig msg, MessageContext ctx) {
            if (!ctx.side.isServer()) return null;
            EntityPlayerMP player = ctx.getServerHandler().playerEntity;
            if (!player.canCommandSenderUseCommand(2, "EZMiner")) return null;

            // Clamp values to safe ranges before applying
            Config.bigRadius = Math.max(0, msg.bigRadius);
            Config.blockLimit = Math.max(0, msg.blockLimit);
            Config.smallRadius = Math.max(0, msg.smallRadius);
            Config.tunnelWidth = Math.max(0, msg.tunnelWidth);
            Config.breakPerTick = Math.max(1, Math.min(64, msg.breakPerTick));
            Config.addExhaustion = msg.addExhaustion;
            Config.dropToPlayer = msg.dropToPlayer;
            Config.serverUsePreview = msg.serverUsePreview;
            Config.serverMaxPreviewBigRadius = Math.max(0, msg.serverMaxPreviewBigRadius);
            Config.serverMaxPreviewBlockLimit = Math.max(0, msg.serverMaxPreviewBlockLimit);
            Config.minesweeperProbeCooldownSeconds = Math.max(1, msg.minesweeperProbeCooldownSeconds);

            // Persist to disk
            Config.saveServerConfig();

            // Push updated limits to all online players
            if (PlayerManager.instance != null) {
                for (Manager mgr : PlayerManager.instance.managers.values()) {
                    mgr.pConfig.updateFrom(new MinerConfig());
                    EZMiner.network.network.sendTo(new PacketMinerConfig(mgr.pConfig), mgr.player);
                    EZMiner.network.network.sendTo(PacketServerConfig.buildForPlayer(mgr.player), mgr.player);
                    EZMiner.network.network.sendTo(new PacketReloadClientConfig(), mgr.player);
                }
            }
            EZMiner.LOG.info("EZMiner server config saved via GUI by {}.", player.getDisplayName());
            return null;
        }
    }
}
