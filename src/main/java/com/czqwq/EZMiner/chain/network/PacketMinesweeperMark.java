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
 * The client stores the position and renders a block outline over it.
 */
public class PacketMinesweeperMark implements IMessage {

    public int x;
    public int y;
    public int z;

    public PacketMinesweeperMark() {}

    public PacketMinesweeperMark(int x, int y, int z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        x = buf.readInt();
        y = buf.readInt();
        z = buf.readInt();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(x);
        buf.writeInt(y);
        buf.writeInt(z);
    }

    public static class Handler implements IMessageHandler<PacketMinesweeperMark, IMessage> {

        @Override
        public IMessage onMessage(PacketMinesweeperMark msg, MessageContext ctx) {
            if (EZMiner.proxy instanceof ClientProxy) {
                ClientProxy proxy = (ClientProxy) EZMiner.proxy;
                proxy.clientState.addMinesweeperMark(new Vector3i(msg.x, msg.y, msg.z));
            }
            return null;
        }
    }
}
