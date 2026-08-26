package com.czqwq.EZMiner.compat.ore;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.block.BlockOre;
import net.minecraft.block.BlockRedstoneOre;
import net.minecraft.tileentity.TileEntity;

import com.czqwq.EZMiner.compat.EtFuturumOreCompat;

/**
 * Decoupled ore-detection registry (the "new module" for optional-mod ore
 * compatibility).
 *
 * <p>
 * {@code compat.adapter.CompatAdapters#isOreBlock(Block, TileEntity)}. It owns
 * every class-name based ore adapter for GT5U generations, BartWorks, GT++,
 * AE2 and Et-Futurum-Requiem, so {@code DeterminingIdentical} no longer needs
 * to hard-code those class checks.
 *
 * <p>
 * Query-time is cheap: the adapter list is small and each adapter's
 * {@link OreCompatAdapter#isAvailable()} is a simple boolean/class-null check.
 */
public final class OreCompatRegistry {

    private static final List<OreCompatAdapter> ADAPTERS;
    private static final boolean HAS_TILE_ENTITY_ONLY_DETECTORS;

    static {
        // EtFuturumOreCompat is lazily initialised and idempotent; make sure it is
        // ready before the anonymous adapter's isAvailable() may be queried.
        EtFuturumOreCompat.init();
        ADAPTERS = createAdapters();
        HAS_TILE_ENTITY_ONLY_DETECTORS = computeTileEntityOnlyFlag();
    }

    private OreCompatRegistry() {}

    /**
     * Returns true when {@code block} or its companion {@code tileEntity} is an
     * ore. Vanilla ores are checked first; optional-mod adapters follow.
     */
    public static boolean isOreBlock(Block block, TileEntity tileEntity) {
        if (block == null) {
            return false;
        }
        if (block instanceof BlockOre || block instanceof BlockRedstoneOre) {
            return true;
        }
        for (OreCompatAdapter adapter : ADAPTERS) {
            try {
                if (adapter.isAvailable() && adapter.isOreBlock(block, tileEntity)) {
                    return true;
                }
            } catch (RuntimeException | LinkageError ignored) {
                // A broken optional-mod adapter must never make ore detection fail open/closed.
            }
        }
        return false;
    }

    /**
     * True when at least one TE-only adapter is present. Callers may use this to
     * avoid fetching the world TileEntity on every non-ore candidate when no
     * TE-only ore family is installed.
     */
    public static boolean hasTileEntityOnlyDetectors() {
        return HAS_TILE_ENTITY_ONLY_DETECTORS;
    }

    /**
     * TE-aware fast path: evaluates only adapters with no block-class constraint.
     *
     * <p>
     * Used after the block-class based detection (and its per-Block cache) has
     * already missed; avoids re-scanning every block-only adapter for the same
     * candidate.
     */
    public static boolean isOreBlockByTileEntity(TileEntity tileEntity) {
        if (tileEntity == null) return false;
        for (OreCompatAdapter adapter : ADAPTERS) {
            if (!adapter.isTileEntityOnly()) continue;
            try {
                if (adapter.isOreBlock(null, tileEntity)) {
                    return true;
                }
            } catch (RuntimeException | LinkageError ignored) {
                // A broken optional-mod adapter must never make ore detection fail open/closed.
            }
        }
        return false;
    }

    private static List<OreCompatAdapter> createAdapters() {
        List<OreCompatAdapter> adapters = new ArrayList<>();
        // GT5U old generation (5.09.51.482 and earlier): BlockOresAbstract + TileEntityOres.
        addIfAvailable(
            adapters,
            new NamedClassOreCompatAdapter(
                "gregtech.common.blocks.BlockOresAbstract",
                "gregtech.common.blocks.TileEntityOres"));
        // GT5U new generation (GTNH 2.9+): metadata-only GTBlockOre, no TileEntity.
        addIfAvailable(adapters, new NamedClassOreCompatAdapter("gregtech.common.blocks.GTBlockOre", null));
        // GT5U renamed legacy hierarchy: BlockOresAbstractLegacy / BlockOresLegacy + TileEntityOres.
        addIfAvailable(
            adapters,
            new NamedClassOreCompatAdapter(
                "gregtech.common.blocks.BlockOresAbstractLegacy",
                "gregtech.common.blocks.TileEntityOres"));
        addIfAvailable(
            adapters,
            new NamedClassOreCompatAdapter(
                "gregtech.common.blocks.BlockOresLegacy",
                "gregtech.common.blocks.TileEntityOres"));
        // BartWorks: block-based ore classes plus TE-only ore tile entities.
        addIfAvailable(
            adapters,
            new NamedClassOreCompatAdapter("bartworks.system.material.BWMetaGeneratedSmallOres", null));
        addIfAvailable(adapters, new NamedClassOreCompatAdapter("bartworks.system.material.BWMetaGeneratedOres", null));
        addIfAvailable(
            adapters,
            new NamedClassOreCompatAdapter(null, "bartworks.system.material.BWTileEntityMetaGeneratedOre"));
        addIfAvailable(
            adapters,
            new NamedClassOreCompatAdapter(null, "bartworks.system.material.BWTileEntityMetaGeneratedSmallOre"));
        // GT++ metadata ores.
        addIfAvailable(adapters, new NamedClassOreCompatAdapter("gtPlusPlus.core.block.base.BlockBaseOre", null));
        // AE2 quartz ores.
        addIfAvailable(adapters, new NamedClassOreCompatAdapter("appeng.block.solids.OreQuartz", null));
        addIfAvailable(adapters, new NamedClassOreCompatAdapter("appeng.block.solids.OreQuartzCharged", null));
        // Et Futurum Requiem reuses the existing dedicated compat class.
        addIfAvailable(adapters, new OreCompatAdapter() {

            @Override
            public boolean isAvailable() {
                return EtFuturumOreCompat.isLoaded();
            }

            @Override
            public boolean isOreBlock(Block block, TileEntity tileEntity) {
                return EtFuturumOreCompat.isOreBlock(block);
            }
        });
        return Collections.unmodifiableList(adapters);
    }

    private static void addIfAvailable(List<OreCompatAdapter> adapters, OreCompatAdapter adapter) {
        if (adapter != null && adapter.isAvailable()) {
            adapters.add(adapter);
        }
    }

    private static boolean computeTileEntityOnlyFlag() {
        for (OreCompatAdapter adapter : ADAPTERS) {
            if (adapter.isTileEntityOnly()) {
                return true;
            }
        }
        return false;
    }
}
