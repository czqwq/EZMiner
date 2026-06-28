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
 * Standalone per-player cache for pre-calculated chain mining block positions.
 *
 * <p>
 * Decoupled from founder, operator, and network layers — this is a plain data store.
 * The cache is keyed by a compound hash of (block position, block class, metadata, dimension)
 * so that the cached list is only reused when the player targets the same block type
 * at the same location in the same dimension.
 *
 * <p>
 * Thread safety: reads and writes are guarded by {@link ConcurrentHashMap}. The cache
 * entries themselves are immutable snapshots. All world access during pre-calculation
 * happens inside the server-tick window via the existing {@code Pauseable} system,
 * so Hodgepodge chunk-loading guards are never triggered off-thread.
 */
public final class ChainPreCalcCache {

    private ChainPreCalcCache() {}

    /** Per-player cache entries, keyed by player UUID. */
    private static final Map<UUID, CachedEntry> entries = new ConcurrentHashMap<>();

    /**
     * Returns the cached entry for {@code playerId}, or {@code null} if none exists.
     */
    public static CachedEntry get(UUID playerId) {
        return entries.get(playerId);
    }

    /**
     * Stores a pre-calculated result for {@code playerId}.
     */
    public static void put(UUID playerId, CachedEntry entry) {
        entries.put(playerId, Objects.requireNonNull(entry));
    }

    /**
     * Removes and discards the cached entry for {@code playerId}.
     */
    public static void remove(UUID playerId) {
        entries.remove(playerId);
    }

    /**
     * Computes a hash that uniquely identifies a mining target context.
     * Two calls with the same arguments produce the same hash.
     */
    public static int computeHash(Vector3i pos, Block block, int meta, int dimension) {
        return Objects.hash(pos.x, pos.y, pos.z, block.getClass(), meta, dimension);
    }

    /**
     * Computes a position-independent hash from block type, metadata and dimension.
     * Two blocks of the same type in the same dimension produce the same hash
     * regardless of where they are located. Used for cache validation when the
     * player breaks a block that may not be the exact center the pre-calc started from.
     */
    public static int computeTypeHash(Block block, int meta, int dimension) {
        return Objects.hash(block.getClass(), meta, dimension);
    }

    /**
     * Computes a position-independent hash from a canonical {@link Class},
     * metadata and dimension. Used by the pre-calc engine in fuzzy mode so that
     * subclasses (e.g. {@code OreQuartzCharged extends OreQuartz}) share the
     * same type hash as their parent class.
     */
    public static int computeTypeHash(Class<?> blockClass, int meta, int dimension) {
        return Objects.hash(blockClass, meta, dimension);
    }

    /**
     * Computes a hash from block registry ID and dimension. Used in exact
     * (non-fuzzy) cached chain mode with Bandit-style matching: only block
     * identity matters, metadata is ignored. This avoids matching failures
     * when NEID or GT compatibility layers modify block metadata.
     */
    public static int computeBlockIdHash(int blockId, int dimension) {
        return Objects.hash(blockId, dimension);
    }

    // ------------------------------------------------------------------ //

    /**
     * Immutable snapshot of a completed pre-calculation.
     */
    public static final class CachedEntry {

        /** The hash that identifies the target context this entry was computed for. */
        public final int contextHash;
        /** Position-independent type hash for cache validation. */
        public final int typeHash;
        /**
         * Fully-qualified class name of the canonical type for fuzzy matching,
         * or {@code null} when this entry was created in exact (non-fuzzy) mode.
         * When non-null, {@code tryStartCachedChain} uses
         * {@code isAssignableFrom} to validate the broken block's class against
         * this class instead of requiring an exact class match.
         */
        public final String fuzzyTypeClassName;
        /** Unmodifiable list of block positions matching the target. */
        public final List<Vector3i> positions;
        /** Dimension the pre-calculation was performed in. */
        public final int dimension;
        /** Center position the pre-calculation BFS started from. */
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
