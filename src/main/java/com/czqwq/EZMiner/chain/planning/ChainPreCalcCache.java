package com.czqwq.EZMiner.chain.planning;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.block.Block;

import org.joml.Vector3i;

/**
 * Per-player cache of pre-calculated chain-mining block positions.
 * Plain data store — decoupled from founder, operator, and network layers.
 * Thread-safe via {@link ConcurrentHashMap}; entries are immutable snapshots.
 */
public final class ChainPreCalcCache {

    private ChainPreCalcCache() {}

    private static final Map<UUID, CachedEntry> entries = new ConcurrentHashMap<>();

    public static CachedEntry get(UUID playerId) { return entries.get(playerId); }
    public static void put(UUID playerId, CachedEntry entry) { entries.put(playerId, Objects.requireNonNull(entry)); }
    public static void remove(UUID playerId) { entries.remove(playerId); }

    /** Hash of (pos, block class, meta, dimension). */
    public static int computeHash(Vector3i pos, Block block, int meta, int dimension) {
        return Objects.hash(pos.x, pos.y, pos.z, block.getClass(), meta, dimension);
    }

    /** Position-independent hash from (block class, meta, dimension). */
    public static int computeTypeHash(Block block, int meta, int dimension) {
        return Objects.hash(block.getClass(), meta, dimension);
    }

    /** Fuzzy mode: subclasses share the canonical class hash (e.g. OreQuartzCharged → OreQuartz). */
    public static int computeTypeHash(Class<?> blockClass, int meta, int dimension) {
        return Objects.hash(blockClass, meta, dimension);
    }

    /** Registry-ID + dimension hash (Bandit-style, metadata ignored). */
    public static int computeBlockIdHash(int blockId, int dimension) {
        return Objects.hash(blockId, dimension);
    }

    // ------------------------------------------------------------------ //

    /** Immutable snapshot of a completed pre-calculation. */
    public static final class CachedEntry {

        public final int contextHash;
        public final int typeHash;
        /** FQ class name for fuzzy matching (null = exact mode). */
        public final String fuzzyTypeClassName;
        public final List<Vector3i> positions;
        public final int dimension;
        public final int centerX, centerY, centerZ;

        public CachedEntry(int contextHash, int typeHash, String fuzzyTypeClassName, List<Vector3i> positions,
            int dimension, int centerX, int centerY, int centerZ) {
            this.contextHash = contextHash;
            this.typeHash = typeHash;
            this.fuzzyTypeClassName = fuzzyTypeClassName;
            this.positions = Collections.unmodifiableList(positions);
            this.dimension = dimension;
            this.centerX = centerX;
            this.centerY = centerY;
            this.centerZ = centerZ;
        }

        public boolean isEmpty() {
            return positions.isEmpty();
        }

        public int size() {
            return positions.size();
        }
    }
}
