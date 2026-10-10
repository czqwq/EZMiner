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
import io.netty.handler.codec.DecoderException;

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
    /**
     * Index in the full pre-calculation result list at which {@link #positions} starts.
     *
     * <p>
     * {@code 0} means "this is the complete list, replace the client's preview". A value {@code > 0}
     * means "append these positions after the ones the client already has". The engine used to
     * re-serialise the whole growing result list on every tick of the BFS, which is O(n²) bytes per
     * pre-calculation and a fresh netty allocation each tick.
     * </p>
     */
    private int startIndex;

    public PacketCachedBlockSync() {}

    /**
     * @param positions the pre-calculated positions (may be empty but not null)
     * @param targetX   the block X the player was looking at during pre-calculation
     * @param targetY   the block Y
     * @param targetZ   the block Z
     * @param dimension the dimension the pre-calculation was performed in
     */
    public PacketCachedBlockSync(List<Vector3i> positions, int targetX, int targetY, int targetZ, int dimension) {
        this(positions, targetX, targetY, targetZ, dimension, 0);
    }

    /**
     * @param startIndex index in the engine's full result list where {@code positions} begins;
     *                   {@code 0} replaces the client's preview, anything else appends
     */
    public PacketCachedBlockSync(List<Vector3i> positions, int targetX, int targetY, int targetZ, int dimension,
        int startIndex) {
        this.positions = positions;
        this.targetX = targetX;
        this.targetY = targetY;
        this.targetZ = targetZ;
        this.dimension = dimension;
        this.startIndex = startIndex;
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(targetX);
        buf.writeInt(targetY);
        buf.writeInt(targetZ);
        buf.writeInt(dimension);
        buf.writeInt(startIndex);
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
        startIndex = buf.readInt();
        int count = buf.readInt();
        // Validate against the remaining bytes before allocating/looping: the old code bounded
        // only the initial capacity with Math.min, so a malformed or desynced stream (or the
        // packet-id drift hazard) with a huge count ran readInt off the end of the buffer and
        // threw IndexOutOfBounds inside the netty decoder. Each position is 3 ints = 12 bytes.
        if (count < 0 || count > buf.readableBytes() / 12) {
            throw new DecoderException(
                "PacketCachedBlockSync: implausible position count " + count
                    + " for "
                    + buf.readableBytes()
                    + " readable bytes");
        }
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

    /** See {@link #startIndex}: 0 = replace the client's preview, &gt; 0 = append. */
    public int getStartIndex() {
        return startIndex;
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
                    if (msg.getStartIndex() > 0) {
                        // Append-only delta (V08): the engine sends only the positions added since
                        // the last packet, so the client continues the list it already has instead
                        // of replacing it. A startIndex beyond the current list means the client
                        // missed an earlier packet — treat it as a fresh full sync rather than
                        // appending out of order.
                        List<Vector3i> current = proxy.clientState.cachedPreviewPositions;
                        if (current == null || current.size() != msg.getStartIndex()) {
                            proxy.clientState.cachedPreviewPositions = msg.getPositions();
                        } else {
                            List<Vector3i> merged = new ArrayList<>(
                                current.size() + msg.getPositions()
                                    .size());
                            merged.addAll(current);
                            merged.addAll(msg.getPositions());
                            proxy.clientState.cachedPreviewPositions = Collections.unmodifiableList(merged);
                        }
                    } else {
                        proxy.clientState.cachedPreviewPositions = msg.getPositions();
                    }
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
