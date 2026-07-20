package com.czqwq.EZMiner.chain.network;

import net.minecraft.entity.player.EntityPlayerMP;

import com.czqwq.EZMiner.EZMiner;
import com.czqwq.EZMiner.chain.state.ChainPlayerState;
import com.czqwq.EZMiner.network.MainThreadEnforcer;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

public class PacketKeyState implements IMessage {

    public boolean pressed;

    public PacketKeyState() {}

    public PacketKeyState(boolean pressed) {
        this.pressed = pressed;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        pressed = buf.readBoolean();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeBoolean(pressed);
    }

    public static class Handler implements IMessageHandler<PacketKeyState, IMessage> {

        @Override
        public IMessage onMessage(PacketKeyState msg, MessageContext ctx) {
            if (!ctx.side.isServer()) return null;
            return MainThreadEnforcer.guardedNull(ctx.side, () -> {
                EntityPlayerMP player = ctx.getServerHandler().playerEntity;
                ChainPlayerState state = EZMiner.chainStateService.getOrCreate(player.getUniqueID());
                state.keyPressed = msg.pressed;
                // When the player releases the chain key while in block swap mode, clear
                // the client-side block-swap result state.
                if (!msg.pressed && com.czqwq.EZMiner.core.PlayerManager.instance != null) {
                    com.czqwq.EZMiner.core.Manager mgr = com.czqwq.EZMiner.core.PlayerManager.instance.managers
                        .get(player.getUniqueID());
                    if (mgr != null && mgr.isBlockSwapMode()) {
                        EZMiner.network.network.sendTo(new PacketBlockSwapClear(), player);
                    }
                }
                // When the player re-presses the chain key in a special mode, re-send all
                // previously-flagged positions so the client can render them immediately.
                if (msg.pressed && com.czqwq.EZMiner.core.PlayerManager.instance != null) {
                    com.czqwq.EZMiner.core.Manager mgr = com.czqwq.EZMiner.core.PlayerManager.instance.managers
                        .get(player.getUniqueID());
                    if (mgr != null) {
                        if (mgr.isSpecialMinesweeperMode()) {
                            mgr.resendMinesweeperMarks(player);
                        } else if (mgr.isSpecialSudokuMode()) {
                            mgr.resendSudokuFills(player);
                        } else if (mgr.isBlockSwapMode()) {
                            // Block swap mode: no persisted state to resend on key press
                        }
                    }
                }
            });
        }
    }
}
