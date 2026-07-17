package com.czqwq.EZMiner.core.founder;

import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;

import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.tileentity.TileEntity;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.joml.Vector3i;

import com.czqwq.EZMiner.Config;
import com.czqwq.EZMiner.chain.planning.SearchEventBus;
import com.czqwq.EZMiner.core.MinerConfig;
import com.czqwq.EZMiner.thread.Pauseable;
import com.czqwq.EZMiner.thread.SearchWorkerPool;
import com.czqwq.EZMiner.utils.LongOpenHashSet;

/**
 * Shell-expansion block finder. Scans concentric cubes from radius 1 to bigRadius,
 * collecting non-air/liquid/bedrock blocks harvestable by the player.
 *
 * <p>
 * Performance: visited set uses compact {@code long} keys ({@link #encodePos})
 * instead of {@code Vector3i} to avoid GC pressure. Player floor pos and sample block
 * are cached at construction time.
 * </p>
 */
public class BasePositionFounder extends Pauseable {

    public static final Logger LOG = LogManager.getLogger();

    public Vector3i center;
    public EntityPlayer player;
    public MinerConfig minerConfig;
    /** External queue consumed by the operator. */
    public LinkedBlockingQueue<Vector3i> positions;

    /** Dedup set using compact long keys from {@link #encodePos} — lock-free for multi-threaded search. */
    protected final Set<Long> visitedPositions;

    /**
     * Optional primitive visited set for reduced GC pressure. Non-null only when
     * {@link Config#usePrimitiveVisitedSet} is true. Has its own thread safety
     * via {@code ReadWriteLock} — not guarded by visitedPositions.
     */
    protected final LongOpenHashSet visitedPrimitive;

    /**
     * Optional event bus for generation-gated publishing. When non-null,
     * {@link #addResult} publishes through the bus with generation check.
     */
    protected SearchEventBus eventBus = null;
    /** Snapshot of the bus generation at the time this founder was created. */
    protected int busGeneration = 0;

    /** Number of positions added so far. Atomic for multi-threaded workers. */
    public final AtomicInteger curCount = new AtomicInteger(0);

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

        // Select visited-set implementation: primitive long[]-backed set (opt-in)
        // avoids Long boxing overhead for large searches.
        if (Config.usePrimitiveVisitedSet) {
            visitedPrimitive = new LongOpenHashSet(minerConfig.blockLimit);
            visitedPositions = null;
        } else {
            visitedPrimitive = null;
            visitedPositions = ConcurrentHashMap.newKeySet(256);
        }

        this.center = center;
        this.player = player;
        this.positions = results;
        this.minerConfig = minerConfig;

        // Chunk pre-loading is intentionally NOT done here.
        //
        // The constructor runs on the server thread inside a BreakEvent handler.
        // Calling world.getChunkFromChunkCoords → provideChunk here triggers
        // synchronous disk I/O (AnvilChunkLoader.loadChunk → .mca reads) when
        // Hodgepodge's ChunkGenScheduler has chunk generation blocked (isBlocked()
        // is true from tick start until the chunk-gen phase completes).
        //
        // For cached chain mode, enableChainChunkLoading is handled by
        // ChainPreCalcEngine.tickBfs() which runs on the server thread during
        // the tick — it calls world.getChunkFromChunkCoords safely between
        // the network-packet and chunk-gen phases.
        //
        // For blast mode, worker threads cannot safely mutate ChunkProviderServer
        // state. Unloaded-chunk positions are skipped (not added to visited) so
        // they are retried on future ticks after the player naturally loads them.

        sampleBlock = player.worldObj.getBlock(center.x, center.y, center.z);
        sampleBlockMeta = player.worldObj.getBlockMetadata(center.x, center.y, center.z);
        sampleTileEntity = player.worldObj.getTileEntity(center.x, center.y, center.z);

        cachedPlayerFloorX = (int) Math.floor(player.posX);
        cachedPlayerFloorY = (int) Math.floor(player.posY);
        cachedPlayerFloorZ = (int) Math.floor(player.posZ);

        addResult(center);

        // Set cooperative yielding budget from config (0 = disabled by default).
        setWorkBudget(Config.searchBudgetPerYield);
    }

    @Override
    public void run1() {
        if (minerConfig.blockLimit < 64 || Config.searchWorkerThreads <= 0
            || SearchWorkerPool.get() == null
            || SearchWorkerPool.get()
                .isShutdown()) {
            doSingleThreadedSearch();
        } else {
            doMultiThreadedSearch();
        }
    }

    protected void doSingleThreadedSearch() {
        run1SingleThreaded();
    }

    protected void doMultiThreadedSearch() {
        run1MultiThreaded();
    }

    /** Original single-threaded shell-expansion scan (fallback for small operations). */
    private void run1SingleThreaded() {
        int curRadius = 1;
        while (curCount.get() < minerConfig.blockLimit && curRadius <= minerConfig.bigRadius) {
            if (player == null || player.isDead || player.worldObj == null) return;
            int xMin = center.x - curRadius, xMax = center.x + curRadius;
            int yMin = center.y - curRadius, yMax = center.y + curRadius;
            int zMin = center.z - curRadius, zMax = center.z + curRadius;
            scanShellFaces(xMin, xMax, yMin, yMax, zMin, zMax);
            if (curCount.get() >= minerConfig.blockLimit) return;
            waitUntil();
            if (Thread.currentThread()
                .isInterrupted()) return;
            curRadius++;
        }
    }

    /**
     * Iterates the 6 faces of the cube defined by [xMin..xMax]×[yMin..yMax]×[zMin..zMax]
     * using explicit face loops — O(R²) instead of the naive O(R³) cubic scan.
     * Edges between faces are excluded to avoid duplicate processing.
     */
    private void scanShellFaces(int xMin, int xMax, int yMin, int yMax, int zMin, int zMax) {
        // Face x=xMin and x=xMax: full yz-plane (includes all edges)
        for (int y = yMin; y <= yMax; y++) {
            for (int z = zMin; z <= zMax; z++) {
                if (tryProcessShellPos(xMin, y, z)) return;
            }
        }
        for (int y = yMin; y <= yMax; y++) {
            for (int z = zMin; z <= zMax; z++) {
                if (tryProcessShellPos(xMax, y, z)) return;
            }
        }
        // Face y=yMin and y=yMax: exclude x-edges already covered above
        int xMinInner = xMin + 1, xMaxInner = xMax - 1;
        int zMinInner = zMin + 1, zMaxInner = zMax - 1;
        if (xMinInner <= xMaxInner && zMinInner <= zMaxInner) {
            for (int x = xMinInner; x <= xMaxInner; x++) {
                for (int z = zMinInner; z <= zMaxInner; z++) {
                    if (tryProcessShellPos(x, yMin, z)) return;
                }
            }
            for (int x = xMinInner; x <= xMaxInner; x++) {
                for (int z = zMinInner; z <= zMaxInner; z++) {
                    if (tryProcessShellPos(x, yMax, z)) return;
                }
            }
        }
        // Face z=zMin and z=zMax: exclude x and y edges already covered above
        int yMinInner = yMin + 1, yMaxInner = yMax - 1;
        if (xMinInner <= xMaxInner && yMinInner <= yMaxInner) {
            for (int x = xMinInner; x <= xMaxInner; x++) {
                for (int y = yMinInner; y <= yMaxInner; y++) {
                    if (tryProcessShellPos(x, y, zMin)) return;
                }
            }
            for (int x = xMinInner; x <= xMaxInner; x++) {
                for (int y = yMinInner; y <= yMaxInner; y++) {
                    if (tryProcessShellPos(x, y, zMax)) return;
                }
            }
        }
    }

    /** Process a single shell position. Returns true if the search should stop (limit reached). */
    private boolean tryProcessShellPos(int x, int y, int z) {
        if (curCount.get() >= minerConfig.blockLimit) return true;
        if (!consumeBudget()) return true; // yield if budget exhausted or interrupted
        if (isVisited(x, y, z)) return false;
        Vector3i pos = new Vector3i(x, y, z);
        if (checkCanAdd(pos)) addResult(pos);
        return curCount.get() >= minerConfig.blockLimit;
    }

    // ── Multi-threaded shell-expansion ────────────────────────────────────────

    /** Divides each shell layer into X-axis strips processed by worker threads. */
    private void run1MultiThreaded() {
        final int numWorkers = Math.max(1, Config.searchWorkerThreads);
        int curRadius = 1;
        while (curCount.get() < minerConfig.blockLimit && curRadius <= minerConfig.bigRadius) {
            if (player == null || player.isDead || player.worldObj == null) return;
            final int xMin = center.x - curRadius, xMax = center.x + curRadius;
            final int yMin = center.y - curRadius, yMax = center.y + curRadius;
            final int zMin = center.z - curRadius, zMax = center.z + curRadius;

            final int stripW = Math.max(1, (xMax - xMin + 1) / numWorkers);
            java.util.List<Callable<Void>> tasks = new java.util.ArrayList<>(numWorkers);
            for (int w = 0; w < numWorkers; w++) {
                final int sx = xMin + w * stripW;
                final int ex = (w == numWorkers - 1) ? xMax : sx + stripW - 1;
                if (sx > ex) continue;
                tasks.add(() -> {
                    scanShellStrip(sx, ex, xMin, xMax, yMin, yMax, zMin, zMax);
                    return null;
                });
            }
            try {
                SearchWorkerPool.get()
                    .invokeAll(tasks);
            } catch (InterruptedException e) {
                Thread.currentThread()
                    .interrupt();
                return;
            }
            curRadius++;
            waitUntil();
            if (Thread.currentThread()
                .isInterrupted()) return;
        }
    }

    /**
     * Scans a subset of the shell for the given X-range. Called by worker threads.
     * Uses explicit face iteration — O(stripWidth × R) instead of O(stripWidth × R²).
     */
    private void scanShellStrip(int sx, int ex, int xMin, int xMax, int yMin, int yMax, int zMin, int zMax) {
        // Face x=xMin: full yz-plane, only if this strip covers xMin
        if (sx <= xMin && xMin <= ex) {
            for (int y = yMin; y <= yMax; y++) {
                for (int z = zMin; z <= zMax; z++) {
                    if (tryProcessShellPos(xMin, y, z)) return;
                }
            }
        }
        // Face x=xMax: full yz-plane, only if this strip covers xMax
        if (sx <= xMax && xMax <= ex) {
            for (int y = yMin; y <= yMax; y++) {
                for (int z = zMin; z <= zMax; z++) {
                    if (tryProcessShellPos(xMax, y, z)) return;
                }
            }
        }
        // Inner x-range for y and z faces (excluding x-edge faces already covered)
        int xMinInner = Math.max(sx, xMin + 1);
        int xMaxInner = Math.min(ex, xMax - 1);
        int zMinInner = zMin + 1, zMaxInner = zMax - 1;
        int yMinInner = yMin + 1, yMaxInner = yMax - 1;
        // Face y=yMin and y=yMax: exclude x and z edges
        if (xMinInner <= xMaxInner && zMinInner <= zMaxInner) {
            for (int x = xMinInner; x <= xMaxInner; x++) {
                for (int z = zMinInner; z <= zMaxInner; z++) {
                    if (tryProcessShellPos(x, yMin, z)) return;
                }
            }
            for (int x = xMinInner; x <= xMaxInner; x++) {
                for (int z = zMinInner; z <= zMaxInner; z++) {
                    if (tryProcessShellPos(x, yMax, z)) return;
                }
            }
        }
        // Face z=zMin and z=zMax: exclude x and y edges
        if (xMinInner <= xMaxInner && yMinInner <= yMaxInner) {
            for (int x = xMinInner; x <= xMaxInner; x++) {
                for (int y = yMinInner; y <= yMaxInner; y++) {
                    if (tryProcessShellPos(x, y, zMin)) return;
                }
            }
            for (int x = xMinInner; x <= xMaxInner; x++) {
                for (int y = yMinInner; y <= yMaxInner; y++) {
                    if (tryProcessShellPos(x, y, zMax)) return;
                }
            }
        }
    }

    public boolean checkCanAdd(Vector3i pos) {
        if (isVisited(pos.x, pos.y, pos.z)) return false;
        return checkCanAddImpl(pos);
    }

    /** Same as {@link #checkCanAdd} but skips the visited-set lookup (caller already atomically added the key). */
    protected boolean checkCanAddAfterVisited(Vector3i pos) {
        return checkCanAddImpl(pos);
    }

    protected boolean checkCanAddImpl(Vector3i pos) {
        if (player.worldObj == null) return false;
        if (!player.worldObj.blockExists(pos.x, pos.y, pos.z)) return false;
        Block block = player.worldObj.getBlock(pos.x, pos.y, pos.z);
        if (block.equals(Blocks.air) || block.getMaterial()
            .isLiquid() || block.equals(Blocks.bedrock)) return false;
        int blockMeta = player.worldObj.getBlockMetadata(pos.x, pos.y, pos.z);
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
        long key = encodePos(pos.x, pos.y, pos.z);
        if (visitedPrimitive != null) {
            if (!visitedPrimitive.add(key)) return; // already visited
        } else {
            if (!visitedPositions.add(key)) return; // already visited
        }
        if (eventBus != null) {
            if (!eventBus.publish(pos, busGeneration)) return; // generation mismatch — chain cancelled
        } else {
            positions.offer(pos);
        }
        curCount.incrementAndGet();
    }

    /** Sets the event bus for decoupled founder→operator communication. */
    public void setEventBus(SearchEventBus bus, int generation) {
        this.eventBus = bus;
        this.busGeneration = generation;
    }

    /** Check visited set using compact long key — call BEFORE allocating a Vector3i. */
    protected boolean isVisited(int x, int y, int z) {
        long key = encodePos(x, y, z);
        if (visitedPrimitive != null) {
            return visitedPrimitive.contains(key);
        }
        return visitedPositions.contains(key);
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
