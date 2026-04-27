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
 * Base position finder: collects all non-air, non-liquid, non-bedrock blocks
 * within bigRadius that can be harvested by the player.
 *
 * <p>
 * <strong>Performance notes:</strong>
 * <ul>
 * <li>Visited positions are tracked via a {@code HashSet<Long>} using a compact
 * coordinate encoding ({@link #encodePos}) rather than {@code HashSet<Vector3i>},
 * reducing GC pressure significantly for large mining operations.</li>
 * <li>The player's floor position is cached once at construction time to avoid
 * creating a new {@link Vector3i} on every {@link #checkCanAdd} call.</li>
 * <li>The inner shell-scan loop checks the visited set before allocating a
 * {@link Vector3i}, so rejected positions create no garbage.</li>
 * </ul>
 */
public class BasePositionFounder extends Pauseable {

    public static final Logger LOG = LogManager.getLogger();

    public Vector3i center;
    public EntityPlayer player;
    public MinerConfig minerConfig;
    /** External queue consumed by the operator. */
    public LinkedBlockingQueue<Vector3i> positions;

    /**
     * Visited-position dedup set, keyed by {@link #encodePos(int, int, int)}.
     *
     * <p>
     * Uses {@code Long} rather than {@code Vector3i} to avoid allocating a temporary
     * wrapper object on every containment check. Since the vast majority of candidates
     * are rejected, this eliminates millions of short-lived {@code Vector3i} allocations
     * per large mining operation.
     *
     * <p>
     * Subclasses that inspect or mutate this set must use
     * {@link #encodePos(int, int, int)} / {@link #isVisited(int, int, int)}.
     */
    protected final HashSet<Long> visitedPositions = new HashSet<>();

    public int curCount = 0;
    protected boolean skipHarvestCheck = false;

    // Sample block at center
    public final Block sampleBlock;
    public final int sampleBlockMeta;
    public final TileEntity sampleTileEntity;

    /**
     * Player floor position, cached once at construction time.
     *
     * <p>
     * The player's position changes slowly relative to the search thread's lifetime,
     * and using the position at search-start is both safe and consistent.
     * Caching avoids creating a new {@link Vector3i} on every
     * {@link #checkCanAdd} invocation (which can occur hundreds of thousands of times
     * during a large blast or chain operation).
     */
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
        // Skip blocks whose chunk is not loaded. Calling getBlock() on an unloaded chunk
        // triggers WorldServer to generate it on this background thread, which corrupts
        // Minecraft's TickNextTick lists and causes "TickNextTick list out of synch" crashes.
        // This guard is relevant when tunnelWidth is very large (many positions fall outside
        // the server's loaded-chunk radius); for all other modes the radius is small enough
        // that every accessed chunk is already loaded.
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

    /**
     * Returns {@code true} if the given coordinates have already been visited
     * (added to the result queue), using the compact long encoding.
     *
     * <p>
     * Callers should check this BEFORE constructing a {@link Vector3i} to avoid
     * unnecessary object allocation for positions that will be immediately rejected.
     */
    protected boolean isVisited(int x, int y, int z) {
        return visitedPositions.contains(encodePos(x, y, z));
    }

    /**
     * Encodes Minecraft world coordinates as a single {@code long} for use as a
     * hash-set key.
     *
     * <p>
     * Coordinate ranges supported:
     * <ul>
     * <li>x, z: {@code [-30,000,000 .. +30,000,000]} (Minecraft world border)</li>
     * <li>y: {@code [0 .. 4095]}</li>
     * </ul>
     *
     * <p>
     * The encoding adds a bias of 30,000,000 to x and z so that negative coordinates
     * produce distinct non-negative values, avoiding collisions that would arise from
     * simple bitmask truncation of two's-complement negative numbers.
     *
     * @param x world X
     * @param y world Y (0 – 255 in vanilla, higher in modded worlds)
     * @param z world Z
     * @return a unique {@code long} key for the coordinate triple
     */
    public static long encodePos(int x, int y, int z) {
        // x+30M and z+30M each fit in 26 bits; y fits in 12 bits → total 64 bits.
        return ((long) (x + 30_000_000) << 38) | ((long) (y & 0xFFF) << 26) | (long) (z + 30_000_000);
    }

    /**
     * @deprecated Use {@link #cachedPlayerFloorX}/{@link #cachedPlayerFloorY}/{@link #cachedPlayerFloorZ}
     *             instead of allocating a new {@link Vector3i} on every call.
     */
    @Deprecated
    protected Vector3i playerFloorPos() {
        return new Vector3i(cachedPlayerFloorX, cachedPlayerFloorY, cachedPlayerFloorZ);
    }
}
