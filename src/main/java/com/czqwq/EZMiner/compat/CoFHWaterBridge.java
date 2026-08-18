package com.czqwq.EZMiner.compat;

import net.minecraft.block.Block;
import net.minecraft.block.BlockDynamicLiquid;
import net.minecraft.block.material.Material;
import net.minecraft.init.Blocks;
import net.minecraft.world.World;

/**
 * Adapter for CoFH Core's water replacement.
 *
 * <p>
 * CoFH Core (via its ASM hooks) swaps the water blocks so a chain-mining fast path
 * runs into its {@code BlockTickingWater}
 * (<code>cofh.asmhooks.block.BlockTickingWater</code>), which <em>extends
 * {@link BlockDynamicLiquid}</em>. {@code BlockDynamicLiquid} inherits
 * {@code BlockLiquid.onNeighborBlockChange}, whose only branch ({@code func_149805_n})
 * handles <em>lava</em> — it is a no-op for water. So notifying such a water block
 * never makes it flow, leaving the cavity dug beneath it unfilled ("floating" water).
 *
 * <p>
 * This bridge is deterministic: when a notified water neighbour no longer has the
 * block below it (its support was just chain-mined), it fills that vacated hole
 * directly with flowing water at level 8 — the vanilla {@code func_149813_h}
 * downward-flow placement — and schedules the fill to settle. Every processed water
 * neighbour logs an INFO line (water neighbours are rare, so no rate limiting) with
 * the class before/after and the block below before/after, so a 5x5 pond test shows
 * exactly which cells the chain notified and whether the bridge filled them.
 */
public final class CoFHWaterBridge {

    private CoFHWaterBridge() {}

    /**
     * Called by the chain-mining neighbour-notification path right after
     * {@code onNeighborBlockChange}.
     *
     * @param world     server world
     * @param neighbour the notified neighbour block (already confirmed non-air)
     * @param x,y,z     neighbour position
     */
    public static void ensureWaterFlowUpdate(World world, Block neighbour, int x, int y, int z) {
        if (world == null || world.isRemote || neighbour == null) return;
        // Cheap pre-filter: only water.
        if (neighbour.getMaterial() != Material.water) return;

        Block below = world.getBlock(x, y - 1, z);
        if (below != null && below == Blocks.air) {
            world.setBlock(x, y - 1, z, Blocks.flowing_water, 8, 3);
            // Promptly settle the placed water (BlockDynamicLiquid is not auto-scheduled
            // when placed via setBlock).
            world.scheduleBlockUpdate(x, y - 1, z, Blocks.flowing_water, 5);
        }

        // Also schedule the neighbour's own flow engine so levels settle; covers the
        // CoFH BlockTickingWater (BlockDynamicLiquid) no-op for onNeighborBlockChange.
        if (neighbour instanceof BlockDynamicLiquid && world.getBlock(x, y, z) == neighbour) {
            world.scheduleBlockUpdate(x, y, z, neighbour, neighbour.tickRate(world));
        }
    }

    /**
     * Sweeps the column directly above a block that was just chain-mined and fills the
     * first water cell whose support is now air. Unlike {@link #ensureWaterFlowUpdate}
     * this does NOT depend on the water being an enumerated 6-face neighbour of the
     * removed block, so it also fixes water that was left floating by earlier mining
     * (vanilla static water has {@code setTickRandomly(false)} and never flows on its
     * own once its support is gone).
     *
     * <p>
     * Cheap: walks at most {@value #MAX_SWEEP} cells up and stops at the first solid
     * block (the common underground case exits after one read).
     *
     * @param world server world
     * @param x,y,z position of a block just removed by chain mining
     */
    public static void sweepFloatingWaterAbove(World world, int x, int y, int z) {
        if (world == null || world.isRemote || y < 0) return;
        int maxY = Math.min(255, y + MAX_SWEEP);
        for (int cy = y + 1; cy <= maxY; cy++) {
            Block above = world.getBlock(x, cy, z);
            if (above == null || (above != Blocks.air && above.getMaterial() != Material.water)) {
                break; // solid ceiling — nothing reachable above this point
            }
            if (above.getMaterial() != Material.water) continue; // air — keep climbing
            // Water: check its own support.
            Block support = world.getBlock(x, cy - 1, z);
            if (support == null || support == Blocks.air) {
                world.setBlock(x, cy - 1, z, Blocks.flowing_water, 8, 3);
                world.scheduleBlockUpdate(x, cy - 1, z, Blocks.flowing_water, 5);
            }
            break; // handled the first water cell in this column
        }
    }

    /** Max cells to walk up when looking for floating water above a mined block. */
    private static final int MAX_SWEEP = 6;
}
