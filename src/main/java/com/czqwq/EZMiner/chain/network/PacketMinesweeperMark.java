package com.czqwq.EZMiner.chain.network;

import org.joml.Vector3i;

import com.czqwq.EZMiner.ClientProxy;
import com.czqwq.EZMiner.EZMiner;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

/**
 * Server → Client: notifies the client that a minesweeper bomb was flagged at the given world position.
 * Also carries the probe cooldown duration so the client can display a countdown in the HUD.
 */
public class PacketMinesweeperMark implements IMessage {

    public int x;
    public int y;
    public int z;
    /** Remaining cooldown in milliseconds after this probe fired. */
    public long cooldownMs;

    public PacketMinesweeperMark() {}

    public PacketMinesweeperMark(int x, int y, int z, long cooldownMs) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.cooldownMs = cooldownMs;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        x = buf.readInt();
        y = buf.readInt();
        z = buf.readInt();
        cooldownMs = buf.readLong();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(x);
        buf.writeInt(y);
        buf.writeInt(z);
        buf.writeLong(cooldownMs);
    }

    public static class Handler implements IMessageHandler<PacketMinesweeperMark, IMessage> {

        @Override
        public IMessage onMessage(PacketMinesweeperMark msg, MessageContext ctx) {
            if (EZMiner.proxy instanceof ClientProxy) {
                ClientProxy proxy = (ClientProxy) EZMiner.proxy;
                proxy.clientState.addMinesweeperMark(new Vector3i(msg.x, msg.y, msg.z));
                // Update the cooldown deadline so the HUD can show a live countdown.
                long nextProbeAt = System.currentTimeMillis() + msg.cooldownMs;
                if (nextProbeAt > proxy.clientState.minesweeperNextProbeClientMs) {
                    proxy.clientState.minesweeperNextProbeClientMs = nextProbeAt;
                }
            }
            return null;
        }
    }
}
