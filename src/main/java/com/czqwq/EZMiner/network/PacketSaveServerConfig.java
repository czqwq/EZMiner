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
    public int cachedBreakPerTick;
    public boolean dropImmediately;
    public double addExhaustion;
    public boolean dropToPlayer;
    public boolean serverUsePreview;
    public int serverMaxPreviewBigRadius;
    public int serverMaxPreviewBlockLimit;
    public double minesweeperProbeCooldownSeconds;
    public double sudokuProbeCooldownSeconds;
    public boolean enableCachedChain;
    public int searchWorkerThreads;
    public boolean suppressHodgepodgeWarnings;
    public boolean enableChainChunkLoading;
    public boolean useChunkCachedHarvest;
    public boolean crazyMode;
    public int chainIdleTimeoutSeconds;
    public int chainIdleCountdownSeconds;
    public boolean stopOnUnbreakable;
    public int chainCooldownTicks;
    public int xpDropMode;
    public boolean mergeXPOrbs;
    public boolean fireBreakEvent;

    public PacketSaveServerConfig() {}

    public PacketSaveServerConfig(int bigRadius, int blockLimit, int smallRadius, int tunnelWidth, int breakPerTick,
        int cachedBreakPerTick, boolean dropImmediately, double addExhaustion, boolean dropToPlayer,
        boolean serverUsePreview, int serverMaxPreviewBigRadius, int serverMaxPreviewBlockLimit,
        double minesweeperProbeCooldownSeconds, double sudokuProbeCooldownSeconds, boolean enableCachedChain,
        int searchWorkerThreads, boolean suppressHodgepodgeWarnings, boolean enableChainChunkLoading,
        boolean useChunkCachedHarvest, boolean crazyMode, int chainIdleTimeoutSeconds, int chainIdleCountdownSeconds,
        boolean stopOnUnbreakable, int chainCooldownTicks, int xpDropMode, boolean mergeXPOrbs,
        boolean fireBreakEvent) {
        this.bigRadius = bigRadius;
        this.blockLimit = blockLimit;
        this.smallRadius = smallRadius;
        this.tunnelWidth = tunnelWidth;
        this.breakPerTick = breakPerTick;
        this.cachedBreakPerTick = cachedBreakPerTick;
        this.dropImmediately = dropImmediately;
        this.addExhaustion = addExhaustion;
        this.dropToPlayer = dropToPlayer;
        this.serverUsePreview = serverUsePreview;
        this.serverMaxPreviewBigRadius = serverMaxPreviewBigRadius;
        this.serverMaxPreviewBlockLimit = serverMaxPreviewBlockLimit;
        this.minesweeperProbeCooldownSeconds = minesweeperProbeCooldownSeconds;
        this.sudokuProbeCooldownSeconds = sudokuProbeCooldownSeconds;
        this.enableCachedChain = enableCachedChain;
        this.searchWorkerThreads = searchWorkerThreads;
        this.suppressHodgepodgeWarnings = suppressHodgepodgeWarnings;
        this.enableChainChunkLoading = enableChainChunkLoading;
        this.useChunkCachedHarvest = useChunkCachedHarvest;
        this.crazyMode = crazyMode;
        this.chainIdleTimeoutSeconds = chainIdleTimeoutSeconds;
        this.chainIdleCountdownSeconds = chainIdleCountdownSeconds;
        this.stopOnUnbreakable = stopOnUnbreakable;
        this.chainCooldownTicks = chainCooldownTicks;
        this.xpDropMode = xpDropMode;
        this.mergeXPOrbs = mergeXPOrbs;
        this.fireBreakEvent = fireBreakEvent;
    }

    // Keep the old constructor for binary compatibility (not used but prevents
    // NoSuchMethodError if any external caller was compiled against the old signature).
    public PacketSaveServerConfig(int bigRadius, int blockLimit, int smallRadius, int tunnelWidth, int breakPerTick,
        int cachedBreakPerTick, boolean dropImmediately, double addExhaustion, boolean dropToPlayer,
        boolean serverUsePreview, int serverMaxPreviewBigRadius, int serverMaxPreviewBlockLimit,
        double minesweeperProbeCooldownSeconds, double sudokuProbeCooldownSeconds, boolean enableCachedChain,
        int searchWorkerThreads, boolean suppressHodgepodgeWarnings, boolean enableChainChunkLoading,
        boolean useChunkCachedHarvest, boolean crazyMode, int chainIdleTimeoutSeconds, int chainIdleCountdownSeconds,
        boolean stopOnUnbreakable, int chainCooldownTicks) {
        this(
            bigRadius,
            blockLimit,
            smallRadius,
            tunnelWidth,
            breakPerTick,
            cachedBreakPerTick,
            dropImmediately,
            addExhaustion,
            dropToPlayer,
            serverUsePreview,
            serverMaxPreviewBigRadius,
            serverMaxPreviewBlockLimit,
            minesweeperProbeCooldownSeconds,
            sudokuProbeCooldownSeconds,
            enableCachedChain,
            searchWorkerThreads,
            suppressHodgepodgeWarnings,
            enableChainChunkLoading,
            useChunkCachedHarvest,
            crazyMode,
            chainIdleTimeoutSeconds,
            chainIdleCountdownSeconds,
            stopOnUnbreakable,
            chainCooldownTicks,
            1,
            true,
            false);
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        bigRadius = buf.readInt();
        blockLimit = buf.readInt();
        smallRadius = buf.readInt();
        tunnelWidth = buf.readInt();
        breakPerTick = buf.readInt();
        cachedBreakPerTick = buf.readInt();
        dropImmediately = buf.readBoolean();
        addExhaustion = buf.readDouble();
        dropToPlayer = buf.readBoolean();
        serverUsePreview = buf.readBoolean();
        serverMaxPreviewBigRadius = buf.readInt();
        serverMaxPreviewBlockLimit = buf.readInt();
        minesweeperProbeCooldownSeconds = buf.readDouble();
        sudokuProbeCooldownSeconds = buf.readDouble();
        enableCachedChain = buf.readBoolean();
        searchWorkerThreads = buf.readInt();
        suppressHodgepodgeWarnings = buf.readBoolean();
        enableChainChunkLoading = buf.readBoolean();
        useChunkCachedHarvest = buf.readBoolean();
        crazyMode = buf.readBoolean();
        chainIdleTimeoutSeconds = buf.readInt();
        chainIdleCountdownSeconds = buf.readInt();
        stopOnUnbreakable = buf.readBoolean();
        chainCooldownTicks = buf.readInt();
        xpDropMode = buf.readInt();
        mergeXPOrbs = buf.readBoolean();
        fireBreakEvent = buf.readBoolean();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(bigRadius);
        buf.writeInt(blockLimit);
        buf.writeInt(smallRadius);
        buf.writeInt(tunnelWidth);
        buf.writeInt(breakPerTick);
        buf.writeInt(cachedBreakPerTick);
        buf.writeBoolean(dropImmediately);
        buf.writeDouble(addExhaustion);
        buf.writeBoolean(dropToPlayer);
        buf.writeBoolean(serverUsePreview);
        buf.writeInt(serverMaxPreviewBigRadius);
        buf.writeInt(serverMaxPreviewBlockLimit);
        buf.writeDouble(minesweeperProbeCooldownSeconds);
        buf.writeDouble(sudokuProbeCooldownSeconds);
        buf.writeBoolean(enableCachedChain);
        buf.writeInt(searchWorkerThreads);
        buf.writeBoolean(suppressHodgepodgeWarnings);
        buf.writeBoolean(enableChainChunkLoading);
        buf.writeBoolean(useChunkCachedHarvest);
        buf.writeBoolean(crazyMode);
        buf.writeInt(chainIdleTimeoutSeconds);
        buf.writeInt(chainIdleCountdownSeconds);
        buf.writeBoolean(stopOnUnbreakable);
        buf.writeInt(chainCooldownTicks);
        buf.writeInt(xpDropMode);
        buf.writeBoolean(mergeXPOrbs);
        buf.writeBoolean(fireBreakEvent);
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
            Config.breakPerTick = Math.max(1, Math.min(512, msg.breakPerTick));
            Config.cachedBreakPerTick = Math.max(1, Math.min(1024, msg.cachedBreakPerTick));
            Config.dropImmediately = msg.dropImmediately;
            Config.addExhaustion = msg.addExhaustion;
            Config.dropToPlayer = msg.dropToPlayer;
            Config.serverUsePreview = msg.serverUsePreview;
            Config.serverMaxPreviewBigRadius = Math.max(0, msg.serverMaxPreviewBigRadius);
            Config.serverMaxPreviewBlockLimit = Math.max(0, msg.serverMaxPreviewBlockLimit);
            Config.minesweeperProbeCooldownSeconds = Math.max(0.1, msg.minesweeperProbeCooldownSeconds);
            Config.sudokuProbeCooldownSeconds = Math.max(0.1, msg.sudokuProbeCooldownSeconds);
            Config.enableCachedChain = msg.enableCachedChain;
            Config.searchWorkerThreads = Math.max(0, Math.min(8, msg.searchWorkerThreads));
            Config.suppressHodgepodgeWarnings = msg.suppressHodgepodgeWarnings;
            Config.enableChainChunkLoading = msg.enableChainChunkLoading;
            Config.useChunkCachedHarvest = msg.useChunkCachedHarvest;
            Config.crazyMode = msg.crazyMode;
            Config.chainIdleTimeoutSeconds = msg.chainIdleTimeoutSeconds <= 0 ? -1
                : Math.max(1, msg.chainIdleTimeoutSeconds);
            Config.chainIdleCountdownSeconds = msg.chainIdleCountdownSeconds <= 0 ? -1
                : Math.max(1, msg.chainIdleCountdownSeconds);
            Config.stopOnUnbreakable = msg.stopOnUnbreakable;
            Config.chainCooldownTicks = Math.max(0, msg.chainCooldownTicks);
            Config.xpDropMode = Math.max(0, Math.min(1, msg.xpDropMode));
            Config.mergeXPOrbs = msg.mergeXPOrbs;
            Config.fireBreakEvent = msg.fireBreakEvent;

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
