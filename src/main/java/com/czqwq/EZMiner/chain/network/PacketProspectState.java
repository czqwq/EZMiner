package com.czqwq.EZMiner.chain.network;

import com.czqwq.EZMiner.ClientProxy;
import com.czqwq.EZMiner.EZMiner;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

/**
 * Server → Client: notifies the client that a prospecting probe fired.
 * Carries the probe interval so the client can display a countdown in the HUD
 * (same pattern as {@link PacketMinesweeperMark}, without coordinates).
 */
public class PacketProspectState implements IMessage {

    /** Probe interval in milliseconds (the countdown restarts after each probe). */
    public long cooldownMs;

    public PacketProspectState() {}

    public PacketProspectState(long cooldownMs) {
        this.cooldownMs = cooldownMs;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        cooldownMs = buf.readLong();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeLong(cooldownMs);
    }

    public static class Handler implements IMessageHandler<PacketProspectState, IMessage> {

        @Override
        public IMessage onMessage(PacketProspectState msg, MessageContext ctx) {
            if (EZMiner.proxy instanceof ClientProxy) {
                ClientProxy proxy = (ClientProxy) EZMiner.proxy;
                // Update the cooldown deadline so the HUD can show a live countdown.
                long nextProbeAt = System.currentTimeMillis() + msg.cooldownMs;
                if (nextProbeAt > proxy.clientState.prospectNextProbeClientMs) {
                    proxy.clientState.prospectNextProbeClientMs = nextProbeAt;
                }
            }
            return null;
        }
    }
}
