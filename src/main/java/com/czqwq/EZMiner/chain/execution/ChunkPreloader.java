package com.czqwq.EZMiner.chain.execution;

import java.util.HashSet;
import java.util.Set;

import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.storage.AnvilChunkLoader;
import net.minecraft.world.gen.ChunkProviderServer;

import org.joml.Vector3i;

/**
 * Incremental chunk pre-loader for chain-mining search areas.
 *
 * <p>
 * <strong>Hodgepodge compatibility:</strong> Hodgepodge's
 * {@code MixinChunkProviderServer_EntityGuard} {@code @Overwrite}s
 * {@code provideChunk()} to return {@code defaultEmptyChunk} when
 * {@code ChunkGenScheduler.isBlocked()}. This preloader uses
 * {@link ChunkProviderServer#loadChunk(int, int)} directly to bypass the guard.
 *
 * <p>
 * <strong>Performance:</strong> GTNH worldgen is extremely expensive
 * (~1.3s per new chunk for terrain + population). This preloader therefore
 * <em>only</em> loads chunks that already exist on disk (previously explored
 * terrain). New chunk generation is never triggered — chunks that don't exist
 * on disk are skipped. This avoids multi-second MSPT spikes.
 *
 * <p>
 * <strong>Rate limit:</strong> at most 1 chunk per tick to keep MSPT low.
 * The founder runs before operatorTask (pre-tick), so a chunk loaded in tick N
 * is available for the founder to scan in tick N+1.
 *
 * <p>
 * <strong>Usage:</strong> call {@link #init} once at chain start, then
 * {@link #tick} each server tick.
 */
public class ChunkPreloader {

    /** Maximum chunks to load from disk per tick. */
    private static final int PER_TICK = 1;

    private final Set<Long> loaded = new HashSet<>();
    private int chunkRadius = 0;
    private int lastCenterX = Integer.MIN_VALUE;
    private int lastCenterZ = Integer.MIN_VALUE;
    private int maxChunkRadius;
    /** Cached AnvilChunkLoader reference, refreshed each tick. */
    private AnvilChunkLoader chunkLoader;
    /** Cached ChunkProviderServer reference. */
    private ChunkProviderServer cps;

    /**
     * Initialize the preloader for a new chain operation. Does NOT load any
     * chunks — chunk loading only happens in {@link #tick} to avoid blocking
     * the BreakEvent handler.
     *
     * @param world     the world
     * @param center    center block position
     * @param bigRadius max search radius in blocks
     */
    public void init(World world, Vector3i center, int bigRadius) {
        loaded.clear();
        chunkRadius = 0;
        lastCenterX = center.x;
        lastCenterZ = center.z;
        maxChunkRadius = (bigRadius + 15) / 16;
        chunkLoader = null;
        cps = null;
        if (world instanceof WorldServer) {
            cps = ((WorldServer) world).theChunkProviderServer;
            if (cps != null && cps.currentChunkLoader instanceof AnvilChunkLoader) {
                chunkLoader = (AnvilChunkLoader) cps.currentChunkLoader;
            }
        }
    }

    /**
     * Load up to {@link #PER_TICK} chunks that exist on disk in the next
     * shell layer. New (never-generated) chunks are skipped.
     *
     * @return true if more chunks remain to be loaded
     */
    public boolean tick() {
        if (cps == null) return false;

        int cx0 = lastCenterX >> 4;
        int cz0 = lastCenterZ >> 4;
        int count = 0;

        while (chunkRadius <= maxChunkRadius && count < PER_TICK) {
            int r = chunkRadius;
            boolean anyNew = false;

            for (int dx = -r; dx <= r && count < PER_TICK; dx++) {
                for (int dz = -r; dz <= r && count < PER_TICK; dz++) {
                    if (r > 0 && Math.abs(dx) < r && Math.abs(dz) < r) continue;
                    int cx = cx0 + dx;
                    int cz = cz0 + dz;
                    long key = (long) cx << 32 | (cz & 0xffffffffL);
                    if (loaded.contains(key)) continue;
                    loaded.add(key);
                    anyNew = true;

                    // Only load chunks that already exist on disk.
                    // Never trigger terrain generation during chain mining —
                    // GTNH worldgen takes ~1.3s per new chunk.
                    if (!cps.chunkExists(cx, cz)) {
                        if (chunkLoader != null && chunkLoader.chunkExists(null, cx, cz)) {
                            cps.loadChunk(cx, cz);
                        }
                        // else: chunk doesn't exist on disk — skip it silently.
                        // The player hasn't explored this area yet.
                    }
                    count++;
                }
            }
            chunkRadius++;
            if (!anyNew && r >= maxChunkRadius) break;
        }

        return chunkRadius <= maxChunkRadius;
    }

    /** Reset all state. */
    public void reset() {
        loaded.clear();
        chunkRadius = 0;
        lastCenterX = Integer.MIN_VALUE;
        lastCenterZ = Integer.MIN_VALUE;
        chunkLoader = null;
        cps = null;
    }
}
