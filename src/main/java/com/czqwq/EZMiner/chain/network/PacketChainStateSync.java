package com.czqwq.EZMiner.chain.network;

import java.util.UUID;

import com.czqwq.EZMiner.ClientProxy;
import com.czqwq.EZMiner.EZMiner;
import com.czqwq.EZMiner.chain.state.ChainSession;

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
    public boolean hasSession;
    public long sessionStartMs;
    public int sessionDimension;
    public int chainedCount;
    public long elapsedMs;
    public boolean inOperate;

    public PacketChainStateSync() {}

    public PacketChainStateSync(ChainSession session, int chainedCount, long elapsedMs, boolean inOperate) {
        this.hasSession = session != null;
        UUID sessionId = session != null ? session.sessionId : null;
        this.sessionMost = hasSession ? sessionId.getMostSignificantBits() : 0L;
        this.sessionLeast = hasSession ? sessionId.getLeastSignificantBits() : 0L;
        this.sessionStartMs = hasSession ? session.startTimeMs : 0L;
        this.sessionDimension = hasSession ? session.dimensionId : 0;
        this.chainedCount = chainedCount;
        this.elapsedMs = elapsedMs;
        this.inOperate = inOperate;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        hasSession = buf.readBoolean();
        if (hasSession) {
            sessionMost = buf.readLong();
            sessionLeast = buf.readLong();
            sessionStartMs = buf.readLong();
            sessionDimension = buf.readInt();
        } else {
            sessionMost = 0L;
            sessionLeast = 0L;
            sessionStartMs = 0L;
            sessionDimension = 0;
        }
        chainedCount = buf.readInt();
        elapsedMs = buf.readLong();
        inOperate = buf.readBoolean();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeBoolean(hasSession);
        if (hasSession) {
            buf.writeLong(sessionMost);
            buf.writeLong(sessionLeast);
            buf.writeLong(sessionStartMs);
            buf.writeInt(sessionDimension);
        }
        buf.writeInt(chainedCount);
        buf.writeLong(elapsedMs);
        buf.writeBoolean(inOperate);
    }

    public static class Handler implements IMessageHandler<PacketChainStateSync, IMessage> {

        @Override
        public IMessage onMessage(PacketChainStateSync msg, MessageContext ctx) {
            if (EZMiner.proxy instanceof ClientProxy) {
                ClientProxy proxy = (ClientProxy) EZMiner.proxy;
                UUID msgSession = msg.hasSession ? new UUID(msg.sessionMost, msg.sessionLeast) : null;
                if (msg.hasSession) {
                    if (proxy.clientState.chainClientState.sessionId != null) {
                        if (msg.sessionStartMs < proxy.clientState.chainClientState.sessionStartMs) return null;
                        if (msg.sessionStartMs == proxy.clientState.chainClientState.sessionStartMs
                            && !proxy.clientState.chainClientState.sessionId.equals(msgSession)) {
                            return null;
                        }
                    }
                    proxy.clientState.chainClientState.sessionId = msgSession;
                    proxy.clientState.chainClientState.sessionStartMs = msg.sessionStartMs;
                    proxy.clientState.chainClientState.sessionDimension = msg.sessionDimension;
                } else {
                    proxy.clientState.chainClientState.sessionId = null;
                    proxy.clientState.chainClientState.sessionStartMs = 0L;
                    proxy.clientState.chainClientState.sessionDimension = 0;
                }
                boolean wasOperate = proxy.clientState.chainClientState.inOperate;
                proxy.clientState.chainClientState.inOperate = msg.inOperate;
                proxy.clientState.chainClientState.chainedCount = msg.chainedCount;
                proxy.clientState.chainClientState.elapsedMs = msg.elapsedMs;
                proxy.clientState.chainedBlockCount = msg.chainedCount;
                proxy.clientState.chainElapsedMs = msg.elapsedMs;

                // Drive preview freeze/unfreeze from the authoritative inOperate transition.
                // This keeps the preview lifecycle decoupled from key-press timing (Bug-R fix).
                if (!wasOperate && msg.inOperate) {
                    // Chain execution started → freeze the current wireframe in place.
                    proxy.minerRenderer.freeze();
                } else if (wasOperate && !msg.inOperate) {
                    // Chain execution ended.
                    if (proxy.clientState.chainClientState.keyPressed) {
                        // Key still held: resume live preview so the player can immediately
                        // see the next chain target without releasing and re-pressing the key.
                        proxy.minerRenderer.unfreeze();
                    }
                    // Key already released: stopChain() already called unfreeze() → no-op here.
                }
            }
            return null;
        }
    }
}
