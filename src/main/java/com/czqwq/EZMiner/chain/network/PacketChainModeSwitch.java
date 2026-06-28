package com.czqwq.EZMiner.chain.network;

import net.minecraft.entity.player.EntityPlayerMP;

import com.czqwq.EZMiner.EZMiner;
import com.czqwq.EZMiner.chain.state.ChainPlayerState;
import com.czqwq.EZMiner.utils.IMath;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

public class PacketChainModeSwitch implements IMessage {

    private static final int MAX_MAIN_MODE = 2;
    // Keep in sync with MinerModeState.BLAST_MODES.length - 1.
    private static final int MAX_BLAST_MODE = 6;
    // Keep in sync with MinerModeState.CHAIN_MODES.length - 1.
    private static final int MAX_CHAIN_MODE = 3;
    // Keep in sync with MinerModeState.SPECIAL_MODES.length - 1.
    private static final int MAX_SPECIAL_MODE = 2;

    public int mainMode;
    public int blastMode;
    public int chainMode;
    public int specialMode;

    public PacketChainModeSwitch() {}

    public PacketChainModeSwitch(int mainMode, int blastMode, int chainMode, int specialMode) {
        this.mainMode = mainMode;
        this.blastMode = blastMode;
        this.chainMode = chainMode;
        this.specialMode = specialMode;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        mainMode = IMath.clamp(buf.readInt(), 0, MAX_MAIN_MODE);
        blastMode = IMath.clamp(buf.readInt(), 0, MAX_BLAST_MODE);
        chainMode = IMath.clamp(buf.readInt(), 0, MAX_CHAIN_MODE);
        specialMode = IMath.clamp(buf.readInt(), 0, MAX_SPECIAL_MODE);
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(mainMode);
        buf.writeInt(blastMode);
        buf.writeInt(chainMode);
        buf.writeInt(specialMode);
    }

    public static class Handler implements IMessageHandler<PacketChainModeSwitch, IMessage> {

        @Override
        public IMessage onMessage(PacketChainModeSwitch msg, MessageContext ctx) {
            if (!ctx.side.isServer()) return null;
            EntityPlayerMP player = ctx.getServerHandler().playerEntity;
            ChainPlayerState state = EZMiner.chainStateService.getOrCreate(player.getUniqueID());
            state.minerModeState.mainMode = msg.mainMode;
            state.minerModeState.blastMode = msg.blastMode;
            state.minerModeState.chainMode = msg.chainMode;
            state.minerModeState.specialMode = msg.specialMode;
            return null;
        }
    }
}
