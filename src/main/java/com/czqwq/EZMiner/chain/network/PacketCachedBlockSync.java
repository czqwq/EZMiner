package com.czqwq.EZMiner.chain.network;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.minecraft.client.Minecraft;

import org.joml.Vector3i;

import com.czqwq.EZMiner.ClientProxy;
import com.czqwq.EZMiner.EZMiner;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import io.netty.buffer.ByteBuf;

/**
 * Server→Client packet that delivers a pre-calculated block position list
 * for client-side preview rendering in cached chain sub-modes.
 *
 * <p>
 * <strong>Decoupling:</strong> this packet is a plain data-transfer object.
 * It does not know about mining execution, cache storage, or founder logic.
 * The client-side handler stores the positions in {@code ClientStateContainer}
 * for the {@code MinerRenderer} to consume.
 */
public class PacketCachedBlockSync implements IMessage {

    private List<Vector3i> positions;
    private int targetX, targetY, targetZ;
    private int dimension;

    public PacketCachedBlockSync() {}

    /**
     * @param positions the pre-calculated positions (may be empty but not null)
     * @param targetX   the block X the player was looking at during pre-calculation
     * @param targetY   the block Y
     * @param targetZ   the block Z
     * @param dimension the dimension the pre-calculation was performed in
     */
    public PacketCachedBlockSync(List<Vector3i> positions, int targetX, int targetY, int targetZ, int dimension) {
        this.positions = positions;
        this.targetX = targetX;
        this.targetY = targetY;
        this.targetZ = targetZ;
        this.dimension = dimension;
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(targetX);
        buf.writeInt(targetY);
        buf.writeInt(targetZ);
        buf.writeInt(dimension);
        buf.writeInt(positions.size());
        for (Vector3i pos : positions) {
            buf.writeInt(pos.x);
            buf.writeInt(pos.y);
            buf.writeInt(pos.z);
        }
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        targetX = buf.readInt();
        targetY = buf.readInt();
        targetZ = buf.readInt();
        dimension = buf.readInt();
        int count = buf.readInt();
        List<Vector3i> list = new ArrayList<>(Math.min(count, 4096));
        for (int i = 0; i < count; i++) {
            list.add(new Vector3i(buf.readInt(), buf.readInt(), buf.readInt()));
        }
        positions = Collections.unmodifiableList(list);
    }

    public List<Vector3i> getPositions() {
        return positions;
    }

    public int getTargetX() {
        return targetX;
    }

    public int getTargetY() {
        return targetY;
    }

    public int getTargetZ() {
        return targetZ;
    }

    public int getDimension() {
        return dimension;
    }

    public static class Handler implements IMessageHandler<PacketCachedBlockSync, IMessage> {

        @Override
        @SideOnly(Side.CLIENT)
        public IMessage onMessage(PacketCachedBlockSync msg, MessageContext ctx) {
            if (EZMiner.proxy instanceof ClientProxy) {
                ClientProxy proxy = (ClientProxy) EZMiner.proxy;
                // Validate dimension match — discard stale cross-dimension packets.
                Minecraft mc = Minecraft.getMinecraft();
                if (mc.thePlayer != null && mc.thePlayer.dimension == msg.getDimension()) {
                    proxy.clientState.cachedPreviewPositions = msg.getPositions();
                    proxy.clientState.cachedPreviewTarget = new Vector3i(
                        msg.getTargetX(),
                        msg.getTargetY(),
                        msg.getTargetZ());
                    proxy.clientState.cachedPreviewDimension = msg.getDimension();
                    proxy.clientState.cachedPreviewVersion++;
                }
            }
            return null;
        }
    }
}
