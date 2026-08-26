package com.czqwq.EZMiner.compat.ore;

import net.minecraft.block.Block;
import net.minecraft.tileentity.TileEntity;

/**
 * Optional-mod ore detection adapter.
 *
 * <p>
 * {@code compat.adapter.OreCompatAdapter}. Each adapter knows how to recognise
 * one ore family either by block class, by tile-entity class, or by both.
 * Adapters must be safe to query when the optional mod is absent and must never
 * throw from {@link #isOreBlock(Block, TileEntity)}.
 */
public interface OreCompatAdapter {

    /** True when this adapter's optional classes are present on the classpath. */
    boolean isAvailable();

    /**
     * Returns true if {@code block} (or its companion {@code tileEntity}) is an
     * ore. Either argument may be null; implementations must be null-safe.
     */
    boolean isOreBlock(Block block, TileEntity tileEntity);

    /**
     * True when this adapter can only match through the TileEntity and has no
     * block-class constraint. Callers use this to skip the {@code getTileEntity}
     * lookup entirely when no such adapter is present.
     */
    default boolean isTileEntityOnly() {
        return false;
    }
}
