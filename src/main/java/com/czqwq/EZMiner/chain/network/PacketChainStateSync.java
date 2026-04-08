package com.czqwq.EZMiner.chain.network;

import java.util.UUID;

import com.czqwq.EZMiner.ClientProxy;
import com.czqwq.EZMiner.EZMiner;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

/**
 * Server authoritative runtime projection for client display state.
 */
public class PacketChainStateSync implements IMessage {

    public long sessionMost;
    public long sessionLeast;
    public int chainedCount;
    public long elapsedMs;
    public boolean inOperate;

    public PacketChainStateSync() {}

    public PacketChainStateSync(UUID sessionId, int chainedCount, long elapsedMs, boolean inOperate) {
        this.sessionMost = sessionId == null ? 0L : sessionId.getMostSignificantBits();
        this.sessionLeast = sessionId == null ? 0L : sessionId.getLeastSignificantBits();
        this.chainedCount = chainedCount;
        this.elapsedMs = elapsedMs;
        this.inOperate = inOperate;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        sessionMost = buf.readLong();
        sessionLeast = buf.readLong();
        chainedCount = buf.readInt();
        elapsedMs = buf.readLong();
        inOperate = buf.readBoolean();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeLong(sessionMost);
        buf.writeLong(sessionLeast);
        buf.writeInt(chainedCount);
        buf.writeLong(elapsedMs);
        buf.writeBoolean(inOperate);
    }

    public static class Handler implements IMessageHandler<PacketChainStateSync, IMessage> {

        @Override
        public IMessage onMessage(PacketChainStateSync msg, MessageContext ctx) {
            if (EZMiner.proxy instanceof ClientProxy) {
                ClientProxy proxy = (ClientProxy) EZMiner.proxy;
                proxy.clientState.chainClientState.chainedCount = msg.chainedCount;
                proxy.clientState.chainClientState.elapsedMs = msg.elapsedMs;
                proxy.clientState.chainedBlockCount = msg.chainedCount;
                proxy.clientState.chainElapsedMs = msg.elapsedMs;
            }
            return null;
        }
    }
}
