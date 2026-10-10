package com.czqwq.EZMiner.chain.network;

import net.minecraft.entity.player.EntityPlayerMP;

import com.czqwq.EZMiner.EZMiner;
import com.czqwq.EZMiner.chain.state.ChainPlayerState;
import com.czqwq.EZMiner.core.MinerModeState;
import com.czqwq.EZMiner.network.MainThreadEnforcer;
import com.czqwq.EZMiner.utils.IMath;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

public class PacketChainModeSwitch implements IMessage {

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
        // Bounds come from the arrays themselves (MinerModeState.MAX_*_INDEX), not from
        // duplicated literals: with literals, adding a sub-mode silently clamped a legitimately
        // selected index away instead of accepting it.
        mainMode = IMath.clamp(buf.readInt(), 0, MinerModeState.MAX_MAIN_MODE_INDEX);
        blastMode = IMath.clamp(buf.readInt(), 0, MinerModeState.MAX_BLAST_MODE_INDEX);
        chainMode = IMath.clamp(buf.readInt(), 0, MinerModeState.MAX_CHAIN_MODE_INDEX);
        specialMode = IMath.clamp(buf.readInt(), 0, MinerModeState.MAX_SPECIAL_MODE_INDEX);
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
            return MainThreadEnforcer.guardedNull(ctx.side, () -> {
                EntityPlayerMP player = ctx.getServerHandler().playerEntity;
                // Reject a mode the server cannot currently expose rather than storing it:
                // MinerModeState's cycling logic skips not-visible modes, so a stored-but-
                // invisible index made the server run a mode combination the client's preview
                // does not describe (e.g. the cached chain sub-modes with enableCachedChain
                // off, which LegacyFounderPlanningFactory then still resolved to the fuzzy
                // founder).
                if (!MinerModeState.isChainModeSelectable(msg.chainMode)
                    || !MinerModeState.isSpecialModeSelectable(msg.specialMode)) {
                    return;
                }
                ChainPlayerState state = EZMiner.chainStateService.getOrCreate(player.getUniqueID());
                state.minerModeState.mainMode = msg.mainMode;
                state.minerModeState.blastMode = msg.blastMode;
                state.minerModeState.chainMode = msg.chainMode;
                state.minerModeState.specialMode = msg.specialMode;
            });
        }
    }
}
