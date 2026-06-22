package com.czqwq.EZMiner.chain.network;

import com.czqwq.EZMiner.ClientProxy;
import com.czqwq.EZMiner.EZMiner;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

/**
 * Server → Client: notifies the client that all minesweeper flagged positions should be cleared.
 *
 * <p>
 * Sent when a LootGames minesweeper game transitions from {@code StageWaiting} to a terminal
 * stage (win / lose / finished) so that stale flagged-mine wireframes from the previous game
 * are removed before the next game starts.
 */
public class PacketMinesweeperClear implements IMessage {

    public PacketMinesweeperClear() {}

    @Override
    public void fromBytes(ByteBuf buf) {
        // No payload – the packet itself is the signal.
    }

    @Override
    public void toBytes(ByteBuf buf) {
        // No payload – the packet itself is the signal.
    }

    public static class Handler implements IMessageHandler<PacketMinesweeperClear, IMessage> {

        @Override
        public IMessage onMessage(PacketMinesweeperClear msg, MessageContext ctx) {
            if (EZMiner.proxy instanceof ClientProxy) {
                ClientProxy proxy = (ClientProxy) EZMiner.proxy;
                proxy.clientState.clearMinesweeperMarks();
                proxy.clientState.minesweeperNextProbeClientMs = 0L;
            }
            return null;
        }
    }
}
