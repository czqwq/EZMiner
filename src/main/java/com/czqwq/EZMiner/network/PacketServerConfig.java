package com.czqwq.EZMiner.network;

import com.czqwq.EZMiner.Config;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

/**
 * Server → client packet that delivers the server's enforced config limits.
 *
 * <p>
 * Sent once when a player logs in so that the client's preview renderer and HUD
 * immediately reflect the actual server constraints (radius, block limit, etc.)
 * rather than whatever values happen to be in the client's local config file.
 */
public class PacketServerConfig implements IMessage {

    public int maxBigRadius;
    public int maxBlockLimit;
    public int maxSmallRadius;
    public int maxTunnelWidth;
    public int maxPreviewBigRadius;
    public int maxPreviewBlockLimit;
    public boolean allowPreview;
    public int breakPerTick;
    public int maxBlockSwapRadius;
    public int maxBlockSwapLimit;
    public boolean enableBlockSwapMode;
    // Performance settings shown/edited in the OP server-settings GUI tab. Synced so the
    // GUI reflects the server's actual values instead of the client's local config file.
    public int searchBudgetPerYield;
    public boolean useDualFrontierBfs;
    public boolean usePrimitiveVisitedSet;
    // Fields that were previously editable in the OP GUI but NOT synced (P1-1 fix).
    // Without these, the client displays stale local-config values and OP saves silently
    // reset them on dedicated servers.
    public int cachedBreakPerTick;
    public boolean dropImmediately;
    public double addExhaustion;
    public boolean dropToPlayer;
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
    /** Whether the receiving client has OP permission on this server. Used to show/hide server config tab in GUI. */
    public boolean isOp;

    public PacketServerConfig() {}

    public PacketServerConfig(int maxBigRadius, int maxBlockLimit, int maxSmallRadius, int maxTunnelWidth,
        int maxPreviewBigRadius, int maxPreviewBlockLimit, boolean allowPreview, int breakPerTick, boolean isOp,
        int maxBlockSwapRadius, int maxBlockSwapLimit, boolean enableBlockSwapMode) {
        this.maxBigRadius = maxBigRadius;
        this.maxBlockLimit = maxBlockLimit;
        this.maxSmallRadius = maxSmallRadius;
        this.maxTunnelWidth = maxTunnelWidth;
        this.maxPreviewBigRadius = maxPreviewBigRadius;
        this.maxPreviewBlockLimit = maxPreviewBlockLimit;
        this.allowPreview = allowPreview;
        this.breakPerTick = breakPerTick;
        this.isOp = isOp;
        this.maxBlockSwapRadius = maxBlockSwapRadius;
        this.maxBlockSwapLimit = maxBlockSwapLimit;
        this.enableBlockSwapMode = enableBlockSwapMode;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        maxBigRadius = buf.readInt();
        maxBlockLimit = buf.readInt();
        maxSmallRadius = buf.readInt();
        maxTunnelWidth = buf.readInt();
        maxPreviewBigRadius = buf.readInt();
        maxPreviewBlockLimit = buf.readInt();
        allowPreview = buf.readBoolean();
        breakPerTick = buf.readInt();
        isOp = buf.readBoolean();
        maxBlockSwapRadius = buf.readInt();
        maxBlockSwapLimit = buf.readInt();
        enableBlockSwapMode = buf.readBoolean();
        searchBudgetPerYield = buf.readInt();
        useDualFrontierBfs = buf.readBoolean();
        usePrimitiveVisitedSet = buf.readBoolean();
        cachedBreakPerTick = buf.readInt();
        dropImmediately = buf.readBoolean();
        addExhaustion = buf.readDouble();
        dropToPlayer = buf.readBoolean();
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
        buf.writeInt(maxBigRadius);
        buf.writeInt(maxBlockLimit);
        buf.writeInt(maxSmallRadius);
        buf.writeInt(maxTunnelWidth);
        buf.writeInt(maxPreviewBigRadius);
        buf.writeInt(maxPreviewBlockLimit);
        buf.writeBoolean(allowPreview);
        buf.writeInt(breakPerTick);
        buf.writeBoolean(isOp);
        buf.writeInt(maxBlockSwapRadius);
        buf.writeInt(maxBlockSwapLimit);
        buf.writeBoolean(enableBlockSwapMode);
        buf.writeInt(searchBudgetPerYield);
        buf.writeBoolean(useDualFrontierBfs);
        buf.writeBoolean(usePrimitiveVisitedSet);
        buf.writeInt(cachedBreakPerTick);
        buf.writeBoolean(dropImmediately);
        buf.writeDouble(addExhaustion);
        buf.writeBoolean(dropToPlayer);
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

    /**
     * Builds a packet with all current server config values and the OP status of {@code player}.
     * Use this factory instead of the full constructor to avoid duplicating the OP check at each call site.
     */
    public static PacketServerConfig buildForPlayer(net.minecraft.entity.player.EntityPlayerMP player) {
        PacketServerConfig packet = new PacketServerConfig(
            Config.bigRadius,
            Config.blockLimit,
            Config.smallRadius,
            Config.tunnelWidth,
            Config.serverMaxPreviewBigRadius,
            Config.serverMaxPreviewBlockLimit,
            Config.serverUsePreview,
            Config.breakPerTick,
            player.canCommandSenderUseCommand(2, "EZMiner"),
            Config.blockSwapRadius,
            Config.blockSwapLimit,
            Config.enableBlockSwapMode);
        packet.searchBudgetPerYield = Config.searchBudgetPerYield;
        packet.useDualFrontierBfs = Config.useDualFrontierBfs;
        packet.usePrimitiveVisitedSet = Config.usePrimitiveVisitedSet;
        packet.cachedBreakPerTick = Config.cachedBreakPerTick;
        packet.dropImmediately = Config.dropImmediately;
        packet.addExhaustion = Config.addExhaustion;
        packet.dropToPlayer = Config.dropToPlayer;
        packet.minesweeperProbeCooldownSeconds = Config.minesweeperProbeCooldownSeconds;
        packet.sudokuProbeCooldownSeconds = Config.sudokuProbeCooldownSeconds;
        packet.enableCachedChain = Config.enableCachedChain;
        packet.searchWorkerThreads = Config.searchWorkerThreads;
        packet.suppressHodgepodgeWarnings = Config.suppressHodgepodgeWarnings;
        packet.enableChainChunkLoading = Config.enableChainChunkLoading;
        packet.useChunkCachedHarvest = Config.useChunkCachedHarvest;
        packet.crazyMode = Config.crazyMode;
        packet.chainIdleTimeoutSeconds = Config.chainIdleTimeoutSeconds;
        packet.chainIdleCountdownSeconds = Config.chainIdleCountdownSeconds;
        packet.stopOnUnbreakable = Config.stopOnUnbreakable;
        packet.chainCooldownTicks = Config.chainCooldownTicks;
        packet.xpDropMode = Config.xpDropMode;
        packet.mergeXPOrbs = Config.mergeXPOrbs;
        packet.fireBreakEvent = Config.fireBreakEvent;
        return packet;
    }

    public static class Handler implements IMessageHandler<PacketServerConfig, IMessage> {

        @Override
        public IMessage onMessage(PacketServerConfig msg, MessageContext ctx) {
            if (ctx.side.isClient()) {
                Config.applyServerRuntimeLimits(
                    msg.maxBigRadius,
                    msg.maxBlockLimit,
                    msg.maxSmallRadius,
                    msg.maxTunnelWidth,
                    msg.maxPreviewBigRadius,
                    msg.maxPreviewBlockLimit,
                    msg.allowPreview,
                    msg.breakPerTick,
                    msg.maxBlockSwapRadius,
                    msg.maxBlockSwapLimit,
                    msg.enableBlockSwapMode);
                Config.applyServerRuntimePerformance(
                    msg.searchBudgetPerYield,
                    msg.useDualFrontierBfs,
                    msg.usePrimitiveVisitedSet);
                Config.applyServerRuntimeConfig(
                    msg.cachedBreakPerTick,
                    msg.dropImmediately,
                    msg.addExhaustion,
                    msg.dropToPlayer,
                    msg.minesweeperProbeCooldownSeconds,
                    msg.sudokuProbeCooldownSeconds,
                    msg.enableCachedChain,
                    msg.searchWorkerThreads,
                    msg.suppressHodgepodgeWarnings,
                    msg.enableChainChunkLoading,
                    msg.useChunkCachedHarvest,
                    msg.crazyMode,
                    msg.chainIdleTimeoutSeconds,
                    msg.chainIdleCountdownSeconds,
                    msg.stopOnUnbreakable,
                    msg.chainCooldownTicks,
                    msg.xpDropMode,
                    msg.mergeXPOrbs,
                    msg.fireBreakEvent);
                com.czqwq.EZMiner.EZMiner.clientIsOp = msg.isOp;
            }
            return null;
        }
    }
}
