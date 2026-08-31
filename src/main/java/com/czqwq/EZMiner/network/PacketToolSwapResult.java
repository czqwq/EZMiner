package com.czqwq.EZMiner.network;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;

import com.czqwq.EZMiner.compat.GT5ToolCompat;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import io.netty.buffer.ByteBuf;

/**
 * Server → Client: a GT Toolbox was moved into the given hotbar slot by the
 * server-authoritative swap. The client configures the toolbox's internal tool.
 */
public class PacketToolSwapResult implements IMessage {

    private int hotbarSlot;
    private int internalSlot;

    public PacketToolSwapResult() {}

    public PacketToolSwapResult(int hotbarSlot, int internalSlot) {
        this.hotbarSlot = hotbarSlot;
        this.internalSlot = internalSlot;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        hotbarSlot = buf.readByte();
        internalSlot = buf.readByte();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeByte(hotbarSlot);
        buf.writeByte(internalSlot);
    }

    public static class Handler implements IMessageHandler<PacketToolSwapResult, IMessage> {

        @Override
        @SideOnly(Side.CLIENT)
        public IMessage onMessage(PacketToolSwapResult msg, MessageContext ctx) {
            Minecraft mc = Minecraft.getMinecraft();
            if (mc.thePlayer == null) return null;
            EntityPlayer player = mc.thePlayer;
            if (msg.hotbarSlot < 0 || msg.hotbarSlot >= 9) return null;
            if (msg.internalSlot < 0) return null;
            // The server has already placed the toolbox in this hotbar slot.
            GT5ToolCompat.setToolboxSelectedTool(msg.hotbarSlot, msg.internalSlot);
            return null;
        }
    }
}
