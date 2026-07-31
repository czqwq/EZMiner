package com.czqwq.EZMiner.network;

import com.czqwq.EZMiner.client.gui.HudConfigGui;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import io.netty.buffer.ByteBuf;

/**
 * Server→Client packet that instructs the client to open the HUD drag-config
 * screen.
 *
 * <p>
 * Sent in response to the {@code /EZMiner hud config} command. The packet
 * carries no payload — its mere arrival triggers the GUI.
 */
public class PacketOpenHudConfig implements IMessage {

    public PacketOpenHudConfig() {}

    @Override
    public void fromBytes(ByteBuf buf) {
        // No payload.
    }

    @Override
    public void toBytes(ByteBuf buf) {
        // No payload.
    }

    public static class Handler implements IMessageHandler<PacketOpenHudConfig, IMessage> {

        @Override
        @SideOnly(Side.CLIENT)
        public IMessage onMessage(PacketOpenHudConfig msg, MessageContext ctx) {
            HudConfigGui.open();
            return null;
        }
    }
}
