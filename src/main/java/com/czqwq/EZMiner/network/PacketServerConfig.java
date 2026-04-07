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

    public int bigRadius;
    public int blockLimit;
    public int smallRadius;
    public int tunnelWidth;
    public int breakPerTick;

    public PacketServerConfig() {}

    public PacketServerConfig(int bigRadius, int blockLimit, int smallRadius, int tunnelWidth, int breakPerTick) {
        this.bigRadius = bigRadius;
        this.blockLimit = blockLimit;
        this.smallRadius = smallRadius;
        this.tunnelWidth = tunnelWidth;
        this.breakPerTick = breakPerTick;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        bigRadius = buf.readInt();
        blockLimit = buf.readInt();
        smallRadius = buf.readInt();
        tunnelWidth = buf.readInt();
        breakPerTick = buf.readInt();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(bigRadius);
        buf.writeInt(blockLimit);
        buf.writeInt(smallRadius);
        buf.writeInt(tunnelWidth);
        buf.writeInt(breakPerTick);
    }

    public static class Handler implements IMessageHandler<PacketServerConfig, IMessage> {

        @Override
        public IMessage onMessage(PacketServerConfig msg, MessageContext ctx) {
            if (ctx.side.isClient()) {
                // Update in-memory server limits so that preview and HUD use correct values.
                // The client's own file-based config is not overwritten; these are runtime
                // overrides that disappear when the client disconnects.
                Config.bigRadius = msg.bigRadius;
                Config.blockLimit = msg.blockLimit;
                Config.smallRadius = msg.smallRadius;
                Config.tunnelWidth = msg.tunnelWidth;
                Config.breakPerTick = msg.breakPerTick;
            }
            return null;
        }
    }
}
