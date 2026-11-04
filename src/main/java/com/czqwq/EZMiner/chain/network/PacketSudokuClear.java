package com.czqwq.EZMiner.chain.network;

import com.czqwq.EZMiner.ClientProxy;
import com.czqwq.EZMiner.EZMiner;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

/**
 * Server → Client: notifies the client that all Sudoku filled-cell positions should
 * be cleared. Sent when a LootGames Sudoku game ends, when the assistant's remembered
 * fills no longer match the live board (a wrong submission resets the player grid while
 * keeping the puzzle/solution), and before a validated fill list is republished.
 */
public class PacketSudokuClear implements IMessage {

    public PacketSudokuClear() {}

    @Override
    public void fromBytes(ByteBuf buf) {
        // No payload.
    }

    @Override
    public void toBytes(ByteBuf buf) {
        // No payload.
    }

    public static class Handler implements IMessageHandler<PacketSudokuClear, IMessage> {

        @Override
        public IMessage onMessage(PacketSudokuClear msg, MessageContext ctx) {
            if (EZMiner.proxy instanceof ClientProxy) {
                ClientProxy proxy = (ClientProxy) EZMiner.proxy;
                proxy.clientState.clearSudokuFills();
                proxy.clientState.sudokuNextProbeClientMs = 0L;
            }
            return null;
        }
    }
}
