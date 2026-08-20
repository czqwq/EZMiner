package com.czqwq.EZMiner.chain.execution;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;

import org.joml.Vector3i;

import com.czqwq.EZMiner.EZMiner;

import cpw.mods.fml.common.Loader;

/**
 * Direct {@link ExtendedBlockStorage} array writer for batch chain-mining block
 * removal.
 *
 * <p>
 * Bypasses {@link World#setBlock} (and its per-block chunk lookup, neighbor
 * notification, and individual {@code S23PacketBlockChange} overhead) by writing
 * directly to the sub-chunk arrays. Groups positions by chunk so that chunk and
 * EBS references are resolved once per chunk per batch.
 *
 * <p>
 * <strong>EndlessIDs safety:</strong> if EndlessIDs is loaded, its
 * {@code ExtendedBlockStorageMixin} injects crash-guards into
 * {@code getBlockLSBArray()}, {@code getBlockMSBArray()}, etc. This helper
 * therefore uses {@code ExtendedBlockStorage.func_150818_a} (which EndlessIDs
 * {@code @Overwrite}s to handle extended block IDs correctly) instead of directly
 * touching the raw byte arrays.
 *
 * <p>
 * <strong>Hodgepodge interaction:</strong> bypassing {@code World.setBlock} also
 * bypasses Hodgepodge's {@code MixinWorld_PreventChunkLoading} (which wraps
 * neighbor-change methods — not called here since we use flag=2 semantics) and
 * {@code MixinWorldServer_PendingTickIndex} (which wraps tick scheduling — not
 * called here). The skipped paths are identical to what the existing fast-harvest
 * mixin already avoids.
 *
 * <p>
 * <strong>What this does NOT handle (caller's responsibility):</strong>
 * <ul>
 * <li>{@link Block#onBlockHarvested} — per-block, different blocks have different
 * callbacks</li>
 * <li>{@link Block#harvestBlock} — per-block, drops vary by fortune/silk</li>
 * <li>{@link Block#onBlockDestroyedByPlayer} — per-block</li>
 * <li>{@link Block#dropXpOnBlockBreak} — per-block XP</li>
 * <li>Tool damage — per-block</li>
 * <li>{@code BreakEvent} — caller may fire a single representative event at batch
 * start</li>
 * </ul>
 */
public class ChunkBlockWriteHelper {

    private static final boolean ENDLESS_IDS_LOADED = Loader.isModLoaded("endlessids");

    /** Reflected {@code Chunk.isModified} (SRG: {@code field_76643_l}). */
    private static final Field CHUNK_IS_MODIFIED;
    /** Reflected {@code Chunk.heightMap} (SRG: {@code field_76634_f}). */
    private static final Field CHUNK_HEIGHT_MAP;
    /** Reflected {@code Chunk.heightMapMinimum}. */
    private static final Field CHUNK_HEIGHT_MAP_MINIMUM;
    static {
        Field isModified = null, heightMap = null, heightMapMinimum = null;
        try {
            isModified = Chunk.class.getDeclaredField("isModified");
            isModified.setAccessible(true);
            heightMap = Chunk.class.getDeclaredField("heightMap");
            heightMap.setAccessible(true);
            heightMapMinimum = Chunk.class.getDeclaredField("heightMapMinimum");
            heightMapMinimum.setAccessible(true);
        } catch (NoSuchFieldException e) {
            try {
                // SRG fallback
                heightMapMinimum = Chunk.class.getDeclaredField("field_76635_g");
                heightMapMinimum.setAccessible(true);
            } catch (NoSuchFieldException ignored) {}
        }
        CHUNK_IS_MODIFIED = isModified;
        CHUNK_HEIGHT_MAP = heightMap;
        CHUNK_HEIGHT_MAP_MINIMUM = heightMapMinimum;
    }

    /** Grouping of positions by chunk for batched processing. */
    public static class ChunkBatch {

        public final Chunk chunk;
        public final World world;
        /** (x, y, z) positions in world coordinates. */
        public final List<Vector3i> positions = new ArrayList<>(64);
        /** bitmask of X/Z columns touched within this chunk (for heightMap update). */
        public final boolean[] touchedColumns = new boolean[256];

        public ChunkBatch(Chunk chunk, World world) {
            this.chunk = chunk;
            this.world = world;
        }

        public void add(Vector3i pos) {
            positions.add(pos);
            touchedColumns[(pos.x & 15) | ((pos.z & 15) << 4)] = true;
        }
    }

    /**
     * Group a list of world positions by their containing chunk.
     *
     * @param positions block positions in world coordinates
     * @param world     the world (must not be null)
     * @return map of chunk coordinate key (encoded as {@code (long)chunkX << 32 | (chunkZ & 0xffffffffL)})
     *         to ChunkBatch
     */
    public static Map<Long, ChunkBatch> groupByChunk(List<Vector3i> positions, World world) {
        Map<Long, ChunkBatch> batches = new HashMap<>();
        for (Vector3i pos : positions) {
            int cx = pos.x >> 4;
            int cz = pos.z >> 4;
            long key = (long) cx << 32 | (cz & 0xffffffffL);
            ChunkBatch batch = batches.get(key);
            if (batch == null) {
                if (!world.blockExists(pos.x, pos.y, pos.z)) continue;
                Chunk chunk = world.getChunkFromChunkCoords(cx, cz);
                if (chunk == null) continue;
                batch = new ChunkBatch(chunk, world);
                batches.put(key, batch);
            }
            batch.add(pos);
        }
        return batches;
    }

    /**
     * Write air directly into the ExtendedBlockStorage for a single position,
     * using a pre-resolved chunk and EBS. Skips World.setBlock overhead.
     *
     * <p>
     * Uses {@code ExtendedBlockStorage.func_150818_a} (which is safely
     * {@code @Overwrite}n by EndlessIDs to handle extended block IDs), so
     * this works correctly regardless of whether EndlessIDs is installed.
     *
     * <p>
     * <b>Metadata is always zeroed.</b> Vanilla {@code Chunk.func_150807_a}
     * clears the metadata nibble whenever a block is replaced (incl. by air);
     * {@code func_150818_a} alone writes the block-ID arrays and leaves the old
     * metadata in place. Leaving a stale nibble on an air block is what surfaces
     * GT ore positions as wrong/unlocalized names ("name.0") after chain mining:
     * the S23 block-change packet serializes the live {@code getBlockMetadata},
     * and the stale meta is also saved to disk. Zeroing is one cheap nibble
     * write with no refcount/neighbour cost.
     *
     * @return true if the block was set to air
     */
    public static boolean writeAirToEbs(Chunk chunk, ExtendedBlockStorage ebs, int x, int y, int z) {
        if (ebs == null) return false;
        int lx = x & 15;
        int ly = y & 15;
        int lz = z & 15;

        // Check if already air (avoid unnecessary refcount changes) — but still
        // clear any stale metadata nibble. func_150818_a alone leaves the old
        // metadata in place, so an already-air cell can carry a leftover GT-ore
        // nibble that S23PacketBlockChange serializes and that persists into the
        // chunk save, surfacing the mined position as a wrong/unlocalized name
        // ("name.0"). Zeroing is one nibble write with no refcount/neighbour cost.
        Block existing = ebs.getBlockByExtId(lx, ly, lz);
        if (existing == Blocks.air) {
            ebs.setExtBlockMetadata(lx, ly, lz, 0);
            return false;
        }

        // func_150818_a handles blockRefCount and tickRefCount correctly in both
        // vanilla and EndlessIDs-patched ExtendedBlockStorage. EndlessIDs @Overwrites
        // this method to work with extended block ID arrays (b2High, b3).
        ebs.func_150818_a(lx, ly, lz, Blocks.air);
        // Clear the stale metadata nibble (mirror of Chunk.func_150807_a:654/681) —
        // fixes GT-ore "air + leftover meta" blocks showing wrong/unlocalized names.
        ebs.setExtBlockMetadata(lx, ly, lz, 0);
        return true;
    }

    /**
     * One removed block during a chain-mining batch: world position plus the block
     * that was there (needed to keep neighbour notifications faithful to vanilla,
     * which passes the *old* block into {@code onNeighborBlockChange}).
     */
    public static final class RemovedBlock {

        public final int x;
        public final int y;
        public final int z;
        public final Block oldBlock;

        public RemovedBlock(int x, int y, int z, Block oldBlock) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.oldBlock = oldBlock;
        }
    }

    /**
     * Fires neighbour block notifications for every position in a chain-mining
     * batch, deduplicated so each neighbour coordinate is notified at most once.
     *
     * <p>
     * This is what vanilla {@code World.setBlock(..., flag & 1)} does per block
     * (notify the 6 neighbours' {@code onNeighborBlockChange}). Skipping it is
     * the root cause of "floating" water / sand / gravel / attached blocks after
     * chain mining: liquids only flow and falling blocks only fall when their
     * {@code onNeighborBlockChange} fires. Because it is invoked once per batch
     * (not once per block) and skips already-air neighbours, the cost is
     * proportional to the surface of the mined region, not its volume.
     *
     * <p>
     * Caller gates this behind {@code Config.notifyNeighborsOnChainBreak}
     * (default off) — it is the performance-expensive option.
     *
     * @param world   server world being modified
     * @param removed the batch of removed positions with their old blocks
     */
    public static void notifyBatchNeighborChange(World world, List<RemovedBlock> removed) {
        if (world == null || world.isRemote || removed == null || removed.isEmpty()) return;
        // Distinct neighbour coords (compact long key: x<<40 | z<<8 | (y&0xFF)).
        java.util.Set<Long> notified = new java.util.HashSet<>(Math.max(16, removed.size() * 3));
        for (RemovedBlock rb : removed) {
            int x = rb.x, y = rb.y, z = rb.z;
            for (int axis = 0; axis < 6; axis++) {
                int nx = x + NEIGHBOR_DX[axis];
                int ny = y + NEIGHBOR_DY[axis];
                int nz = z + NEIGHBOR_DZ[axis];
                long key = ((long) nx << 40) | ((long) nz << 8) | (long) (ny & 0xFF);
                if (!notified.add(key)) continue;
                Block nb = world.getBlock(nx, ny, nz);
                if (nb == null || nb == Blocks.air) continue;
                // Notify one neighbour of the block change. Direct onNeighborBlockChange
                // (identical to World.notifyBlockOfNeighborChange's body) so no
                // World-layer wrapper (e.g. Hodgepodge MixinWorld_PreventChunkLoading)
                // can intercept it, and so a single misbehaving neighbour cannot abort
                // the rest of the batch.
                try {
                    nb.onNeighborBlockChange(world, nx, ny, nz, rb.oldBlock);
                } catch (Exception e) {
                    EZMiner.LOG.warn(
                        "EZMiner notifyNeighborsOnChainBreak: neighbour ({},{},{}) onNeighborBlockChange failed",
                        nx,
                        ny,
                        nz,
                        e);
                }
                // CoFH Core replaces water with BlockTickingWater (a BlockDynamicLiquid)
                // whose onNeighborBlockChange is a no-op for water — fill the vacated
                // cavity directly (vanilla func_149813_h semantics).
                com.czqwq.EZMiner.compat.CoFHWaterBridge.ensureWaterFlowUpdate(world, nb, nx, ny, nz);
                // Bush family (tall grass/flowers/ferns): guarantee a lost-support plant
                // pops even if its own onNeighborBlockChange was co-opted by another mod.
                com.czqwq.EZMiner.compat.BushSupportBridge.popIfUnsupported(world, nb, nx, ny, nz);
            }
        }
        // Water support sweep: for every mined block, walk the column above it and fill
        // any water whose support is now air — independent of the 6-face neighbour
        // enumeration, so it also fixes water left floating by earlier mining.
        for (RemovedBlock rb : removed) {
            com.czqwq.EZMiner.compat.CoFHWaterBridge.sweepFloatingWaterAbove(world, rb.x, rb.y, rb.z);
        }
        // Force the affected region to re-render on clients (including the local world
        // in single player). Server-side reactions (water->flowing, tall grass popping,
        // sand falling) become visible even if a per-block S23 was coalesced/lost.
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for (RemovedBlock rb : removed) {
            minX = Math.min(minX, rb.x);
            minY = Math.min(minY, rb.y);
            minZ = Math.min(minZ, rb.z);
            maxX = Math.max(maxX, rb.x);
            maxY = Math.max(maxY, rb.y);
            maxZ = Math.max(maxZ, rb.z);
        }
        minY = Math.max(0, minY - 1);
        maxY = Math.min(255, maxY + 1);
        // Only force-relight/remesh compact regions; large veins rely on per-block
        // S23 updates to avoid pathological client-side re-render cost.
        int w = maxX - minX;
        int d = maxZ - minZ;
        if (w * d <= 32 * 32) {
            world.markBlockRangeForRenderUpdate(minX - 1, minY, minZ - 1, maxX + 1, maxY, maxZ + 1);
        }
    }

    private static final int[] NEIGHBOR_DX = { -1, 1, 0, 0, 0, 0 };
    private static final int[] NEIGHBOR_DY = { 0, 0, -1, 1, 0, 0 };
    private static final int[] NEIGHBOR_DZ = { 0, 0, 0, 0, -1, 1 };

    /**
     * Flags neighbouring leaf blocks for vanilla decay when a wood (log) block is
     * removed. Mirrors {@code BlockLeaves.breakBlock}: setting the bit-8 flag via
     * {@code Block.beginLeavesDecay} lets each leaf's own random tick run the
     * log-distance scan and remove out-of-range leaves. Without this the fast
     * harvest path (which never calls {@code Block.breakBlock}) leaves tree
     * canopies floating forever.
     *
     * <p>
     * O(1) per harvested block: the 26-neighbour walk only happens for wood
     * blocks (non-wood mining pays nothing), and non-leaf neighbours are skipped
     * on the {@code Block.isLeaves} check before any world write.
     *
     * @param world   server world
     * @param x,y,z   position of the removed block
     * @param removed the removed block (must be the log that was there)
     */
    public static void flagNeighbouringLeavesForDecay(World world, int x, int y, int z, Block removed) {
        if (world == null || world.isRemote
            || removed == null
            || removed.getMaterial() != net.minecraft.block.material.Material.wood) return;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx == 0 && dy == 0 && dz == 0) continue;
                    int nx = x + dx;
                    int ny = y + dy;
                    int nz = z + dz;
                    if (!world.blockExists(nx, ny, nz)) continue;
                    Block nb = world.getBlock(nx, ny, nz);
                    if (nb == null || !nb.isLeaves(world, nx, ny, nz)) continue;
                    nb.beginLeavesDecay(world, nx, ny, nz);
                }
            }
        }
    }

    /**
     * Update the height map for touched columns in a chunk after batch block removal.
     * For each column where a block was removed, scans downward from the old height
     * to find the new highest non-air block.
     *
     * @param chunk          the modified chunk
     * @param touchedColumns 256-element boolean array indexed by {@code x | (z << 4)}
     */
    public static void updateHeightMap(Chunk chunk, boolean[] touchedColumns) {
        if (CHUNK_HEIGHT_MAP == null) return;
        try {
            int[] heightMap = (int[]) CHUNK_HEIGHT_MAP.get(chunk);
            if (heightMap == null) return;
            int minY = 0;
            if (CHUNK_HEIGHT_MAP_MINIMUM != null) {
                try {
                    minY = CHUNK_HEIGHT_MAP_MINIMUM.getInt(chunk);
                } catch (IllegalAccessException ignored) {}
            }

            ExtendedBlockStorage[] storage = chunk.getBlockStorageArray();
            World world = chunk.worldObj;

            for (int i = 0; i < 256; i++) {
                if (!touchedColumns[i]) continue;
                int lx = i & 15;
                int lz = (i >> 4) & 15;
                int oldHeight = heightMap[i];

                // Scan downward from old height to find new highest non-air block
                int newHeight = minY - 1;
                for (int y = oldHeight; y >= minY; y--) {
                    int y4 = y >> 4;
                    if (y4 < 0 || y4 >= storage.length) break;
                    ExtendedBlockStorage ebs = storage[y4];
                    if (ebs == null || ebs.isEmpty()) {
                        y = (y4 << 4) - 1; // skip to next sub-chunk boundary
                        continue;
                    }
                    Block block = ebs.getBlockByExtId(lx, y & 15, lz);
                    if (block != null && block != Blocks.air
                        && (world == null
                            || !block.isAir(world, (chunk.xPosition << 4) + lx, y, (chunk.zPosition << 4) + lz))) {
                        newHeight = y;
                        break;
                    }
                }
                if (newHeight >= minY) {
                    heightMap[i] = newHeight;
                } else {
                    heightMap[i] = minY - 1;
                }
            }
        } catch (IllegalAccessException e) {
            EZMiner.LOG.error("Failed to update heightMap", e);
        }
    }

    /**
     * Mark a chunk as modified (dirty) so it gets saved to disk.
     */
    public static void markChunkModified(Chunk chunk) {
        if (CHUNK_IS_MODIFIED != null) {
            try {
                CHUNK_IS_MODIFIED.setBoolean(chunk, true);
            } catch (IllegalAccessException e) {
                chunk.setChunkModified();
            }
        } else {
            chunk.setChunkModified();
        }
    }

    /**
     * Returns true if EndlessIDs is detected as loaded. Callers may use this to
     * adjust behavior (e.g. prefer {@code func_150818_a} over raw array access).
     */
    public static boolean isEndlessIDsLoaded() {
        return ENDLESS_IDS_LOADED;
    }

    /**
     * Returns the sub-chunk (ExtendedBlockStorage) for the given Y coordinate
     * from a pre-resolved chunk, or null if the sub-chunk is entirely air.
     */
    public static ExtendedBlockStorage getEbs(Chunk chunk, int y) {
        ExtendedBlockStorage[] storage = chunk.getBlockStorageArray();
        int y4 = y >> 4;
        if (y4 < 0 || y4 >= storage.length) return null;
        return storage[y4];
    }
}
