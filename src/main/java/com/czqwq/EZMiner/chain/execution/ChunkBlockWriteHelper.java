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
     * @return true if the block was set to air
     */
    public static boolean writeAirToEbs(Chunk chunk, ExtendedBlockStorage ebs, int x, int y, int z) {
        if (ebs == null) return false;
        int lx = x & 15;
        int ly = y & 15;
        int lz = z & 15;

        // Check if already air (avoid unnecessary refcount changes)
        Block existing = ebs.getBlockByExtId(lx, ly, lz);
        if (existing == Blocks.air) return false;

        // func_150818_a handles blockRefCount and tickRefCount correctly in both
        // vanilla and EndlessIDs-patched ExtendedBlockStorage. EndlessIDs @Overwrites
        // this method to work with extended block ID arrays (b2High, b3).
        ebs.func_150818_a(lx, ly, lz, Blocks.air);
        return true;
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
