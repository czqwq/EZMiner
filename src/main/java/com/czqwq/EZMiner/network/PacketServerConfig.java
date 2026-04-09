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

    public PacketServerConfig() {}

    public PacketServerConfig(int maxBigRadius, int maxBlockLimit, int maxSmallRadius, int maxTunnelWidth,
        int maxPreviewBigRadius, int maxPreviewBlockLimit, boolean allowPreview, int breakPerTick) {
        this.maxBigRadius = maxBigRadius;
        this.maxBlockLimit = maxBlockLimit;
        this.maxSmallRadius = maxSmallRadius;
        this.maxTunnelWidth = maxTunnelWidth;
        this.maxPreviewBigRadius = maxPreviewBigRadius;
        this.maxPreviewBlockLimit = maxPreviewBlockLimit;
        this.allowPreview = allowPreview;
        this.breakPerTick = breakPerTick;
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
                    msg.breakPerTick);
            }
            return null;
        }
    }
}
