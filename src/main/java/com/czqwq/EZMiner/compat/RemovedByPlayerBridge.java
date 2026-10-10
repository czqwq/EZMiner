package com.czqwq.EZMiner.compat;

import java.lang.reflect.Method;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.world.World;

/**
 * Detects blocks that override {@link Block#removedByPlayer}.
 *
 * <p>
 * <strong>Why this exists.</strong> EZMiner's fast paths remove a block with a raw
 * {@code World.setBlock(..., air, 0, 2)} (or a direct {@code ExtendedBlockStorage} write in the
 * batch path). Vanilla instead goes through {@link Block#removedByPlayer}, the mod-facing removal
 * hook that many mods override for side effects — the concrete case the compat audit found is GT's
 * {@code BlockReinforced}, whose {@code removedByPlayer} turns meta 5 into a primed powder barrel,
 * so a chain-mined barrel used to be deleted silently instead of exploding.
 * </p>
 *
 * <p>
 * <strong>Why a probe and not the hook for everything.</strong> Vanilla's default
 * {@code removedByPlayer} is {@code world.setBlockToAir(x, y, z)}, i.e. flag <strong>3</strong>,
 * which fires six neighbour notifications per block. The fast paths deliberately avoid that (they
 * batch and de-duplicate neighbour notifications afterwards via
 * {@code ChunkBlockWriteHelper.notifyBatchNeighborChange}), so calling the hook unconditionally
 * would undo a documented performance design. Instead:
 * </p>
 *
 * <ul>
 * <li>the <strong>fast path</strong> ({@code MixinItemInWorldManager}) calls
 * {@code removedByPlayer} only for blocks that actually declare their own override — those are the
 * only ones whose semantics differ from the raw write;</li>
 * <li>the <strong>batch paths</strong> route such blocks to the vanilla
 * {@code ItemInWorldManager.tryHarvestBlock} escape hatch, which the executors already use for
 * tile-entity carriers and GT ore containers, so the override runs with full vanilla ordering.</li>
 * </ul>
 *
 * <p>
 * The result is cached per {@code Block} instance: {@code getMethod} is a reflective lookup and the
 * harvest paths run per block, so an uncached probe would be measurable. The cache is keyed by
 * block identity and populated once per block type, and the probe itself is failure-safe (any
 * reflective failure is treated as "no override", i.e. today's behaviour).
 * </p>
 */
public final class RemovedByPlayerBridge {

    /** Signature of {@code Block.removedByPlayer}. */
    private static final Class<?>[] PARAM_TYPES = { World.class, EntityPlayer.class, int.class, int.class, int.class,
        boolean.class };

    /** Per-block answer. Identity-keyed, so a modded block with its own override is probed once. */
    private static final ConcurrentHashMap<Block, Boolean> OVERRIDE_CACHE = new ConcurrentHashMap<>();

    private RemovedByPlayerBridge() {}

    /**
     * True when {@code block}'s class declares its own {@code removedByPlayer}, i.e. when the raw
     * {@code setBlock(..., air, 0, 2)} write would skip mod behaviour.
     */
    public static boolean overridesRemovedByPlayer(Block block) {
        if (block == null) return false;
        Boolean cached = OVERRIDE_CACHE.get(block);
        if (cached != null) return cached;

        boolean overrides;
        try {
            Method method = block.getClass()
                .getMethod("removedByPlayer", PARAM_TYPES);
            // The declaring class is Block itself only when nobody overrode it.
            overrides = method.getDeclaringClass() != Block.class;
        } catch (Throwable t) {
            // Not overridable / not resolvable: keep today's raw-write behaviour.
            overrides = false;
        }
        OVERRIDE_CACHE.put(block, overrides);
        return overrides;
    }

    /**
     * Cache size, exposed only so a verifier can sanity-check the probe is not unbounded in a test
     * harness. Never used by the harvest paths.
     */
    public static int cacheSize() {
        return OVERRIDE_CACHE.size();
    }
}
