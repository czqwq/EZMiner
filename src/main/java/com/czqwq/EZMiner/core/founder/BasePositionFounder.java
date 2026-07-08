package com.czqwq.EZMiner.core.founder;

import java.util.HashSet;
import java.util.concurrent.LinkedBlockingQueue;

import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.tileentity.TileEntity;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.joml.Vector3i;

import com.czqwq.EZMiner.core.MinerConfig;
import com.czqwq.EZMiner.thread.Pauseable;

/**
 * Shell-expansion block finder. Scans concentric cubes from radius 1 to bigRadius,
 * collecting non-air/liquid/bedrock blocks harvestable by the player.
 *
 * <p>Performance: visited set uses compact {@code long} keys ({@link #encodePos})
 * instead of {@code Vector3i} to avoid GC pressure. Player floor pos and sample block
 * are cached at construction time.</p>
 */
public class BasePositionFounder extends Pauseable {

    public static final Logger LOG = LogManager.getLogger();

    public Vector3i center;
    public EntityPlayer player;
    public MinerConfig minerConfig;
    /** External queue consumed by the operator. */
    public LinkedBlockingQueue<Vector3i> positions;

    /** Dedup set using compact long keys from {@link #encodePos} — avoids Vector3i allocations. */
    protected final HashSet<Long> visitedPositions = new HashSet<>();

    public int curCount = 0;
    protected boolean skipHarvestCheck = false;

    public final Block sampleBlock;
    public final int sampleBlockMeta;
    public final TileEntity sampleTileEntity;

    /** Player floor position cached at construction time. */
    protected final int cachedPlayerFloorX;
    protected final int cachedPlayerFloorY;
    protected final int cachedPlayerFloorZ;

    public BasePositionFounder(Vector3i center, LinkedBlockingQueue<Vector3i> results, EntityPlayer player,
        MinerConfig minerConfig) {
        setName("EZMiner-BlastSearch");
        DeterminingIdentical.checkCompatibility();

        this.center = center;
        this.player = player;
        this.positions = results;
        this.minerConfig = minerConfig;

        sampleBlock = player.worldObj.getBlock(center.x, center.y, center.z);
        sampleBlockMeta = player.worldObj.getBlockMetadata(center.x, center.y, center.z);
        sampleTileEntity = player.worldObj.getTileEntity(center.x, center.y, center.z);

        cachedPlayerFloorX = (int) Math.floor(player.posX);
        cachedPlayerFloorY = (int) Math.floor(player.posY);
        cachedPlayerFloorZ = (int) Math.floor(player.posZ);

        addResult(center);
    }

    @Override
    public void run1() {
        int curRadius = 1;
        while (curCount < minerConfig.blockLimit && curRadius <= minerConfig.bigRadius) {
            if (player == null || player.isDead || player.worldObj == null) return;
            int xMin = center.x - curRadius, xMax = center.x + curRadius;
            int yMin = center.y - curRadius, yMax = center.y + curRadius;
            int zMin = center.z - curRadius, zMax = center.z + curRadius;
            for (int x = xMin; x <= xMax; x++) {
                for (int y = yMin; y <= yMax; y++) {
                    for (int z = zMin; z <= zMax; z++) {
                        // Only scan the shell at curRadius; interior positions were already
                        // covered when processing smaller radii, so skip them here.
                        if (x != xMin && x != xMax && y != yMin && y != yMax && z != zMin && z != zMax) continue;
                        // Fast visited check (no Vector3i allocation) before doing any world reads.
                        if (isVisited(x, y, z)) continue;
                        Vector3i pos = new Vector3i(x, y, z);
                        if (checkCanAdd(pos)) addResult(pos);
                        if (curCount >= minerConfig.blockLimit) return;
                        waitUntil();
                        if (Thread.currentThread()
                            .isInterrupted()) return;
                    }
                }
            }
            curRadius++;
        }
    }

    public boolean checkCanAdd(Vector3i pos) {
        if (isVisited(pos.x, pos.y, pos.z)) return false;
        if (player.worldObj == null) return false; // player logged out
        // Avoid chunk-gen on background thread (corrupts TickNextTick).
        if (!player.worldObj.blockExists(pos.x, pos.y, pos.z)) return false;
        Block block = player.worldObj.getBlock(pos.x, pos.y, pos.z);
        if (block.equals(Blocks.air) || block.getMaterial()
            .isLiquid() || block.equals(Blocks.bedrock)) return false;
        int blockMeta = player.worldObj.getBlockMetadata(pos.x, pos.y, pos.z);
        // Protect the block directly under the player's feet (cached, no allocation).
        if (pos.x == cachedPlayerFloorX && pos.y == (cachedPlayerFloorY - 1) && pos.z == cachedPlayerFloorZ)
            return false;
        if (skipHarvestCheck) return true;
        if (player.capabilities.isCreativeMode) return true;
        return block.canHarvestBlock(player, blockMeta);
    }

    public void setSkipHarvestCheck(boolean skipHarvestCheck) {
        this.skipHarvestCheck = skipHarvestCheck;
    }

    public void addResult(Vector3i pos) {
        try {
            positions.put(pos);
            visitedPositions.add(encodePos(pos.x, pos.y, pos.z));
            curCount++;
        } catch (InterruptedException e) {
            Thread.currentThread()
                .interrupt();
            return; // respect interrupt
        }
    }

    /** Check visited set using compact long key — call BEFORE allocating a Vector3i. */
    protected boolean isVisited(int x, int y, int z) {
        return visitedPositions.contains(encodePos(x, y, z));
    }

    /**
     * Compact coordinate encoding: x/z + 30M bias in 26 bits, y in 12 bits → single long.
     * Bias avoids collisions from two's-complement negative coordinates.
     */
    public static long encodePos(int x, int y, int z) {
        return ((long) (x + 30_000_000) << 38) | ((long) (y & 0xFFF) << 26) | (long) (z + 30_000_000);
    }

    /** @deprecated Use cached fields to avoid Vector3i allocation. */
    @Deprecated
    protected Vector3i playerFloorPos() {
        return new Vector3i(cachedPlayerFloorX, cachedPlayerFloorY, cachedPlayerFloorZ);
    }
}
