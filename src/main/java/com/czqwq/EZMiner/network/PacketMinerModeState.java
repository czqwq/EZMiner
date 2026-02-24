package com.czqwq.EZMiner.network;

import net.minecraft.entity.player.EntityPlayerMP;

import com.czqwq.EZMiner.core.Manager;
import com.czqwq.EZMiner.core.MinerModeState;
import com.czqwq.EZMiner.core.PlayerManager;
import com.czqwq.EZMiner.utils.IMath;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

public class PacketMinerModeState implements IMessage {

    public MinerModeState state = new MinerModeState();

    public PacketMinerModeState() {}

    public PacketMinerModeState(MinerModeState state) {
        this.state = state;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        state.mainMode = IMath.clamp(buf.readInt(), 0, MinerModeState.MAIN_MODES.length - 1);
        state.blastMode = IMath.clamp(buf.readInt(), 0, MinerModeState.BLAST_MODES.length - 1);
        state.chainMode = IMath.clamp(buf.readInt(), 0, MinerModeState.CHAIN_MODES.length - 1);
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(state.mainMode);
        buf.writeInt(state.blastMode);
        buf.writeInt(state.chainMode);
    }

    public static class Handler implements IMessageHandler<PacketMinerModeState, IMessage> {

        @Override
        public IMessage onMessage(PacketMinerModeState msg, MessageContext ctx) {
            if (ctx.side.isServer()) {
                EntityPlayerMP player = ctx.getServerHandler().playerEntity;
                Manager mgr = PlayerManager.instance.managers.get(player.getUniqueID());
                if (mgr != null) mgr.minerModeState = msg.state;
            }
            return null;
        }
    }
}
