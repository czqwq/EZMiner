package com.czqwq.EZMiner.compat;

import net.minecraft.block.Block;
import net.minecraft.block.BlockBush;
import net.minecraft.world.World;

/**
 * Adapter that guarantees grass-plant / bush-family blocks ("草丛之类") pop off when
 * they lose their support during chain mining.
 *
 * <p>
 * {@link BlockTallGrass} / flowers / ferns / saplings / dead bushes all extend
 * {@link BlockBush}, whose {@code onNeighborBlockChange} drops + clears the block
 * when {@code canBlockStay} fails. That normally works once the neighbour is
 * notified — but to stay robust against mods that replace or co-opt the plant's
 * {@code onNeighborBlockChange} (the same way CoFH Core co-opts water), this
 * helper re-verifies support directly after the notification and, if the plant is
 * still present but unsupported, performs the drop exactly like vanilla
 * {@code BlockBush.checkAndDropBlock}.
 *
 * <p>
 * It only acts when all of: the caller already confirmed the neighbour is non-air,
 * it is a {@link BlockBush}, the block at the position is still that plant (the
 * notification may already have popped it), and {@code canBlockStay} is now false.
 * No-op otherwise; safe with or without any plant-replacing mod present.
 */
public final class BushSupportBridge {

    private BushSupportBridge() {}

    /**
     * Pops a still-present, no-longer-supported bush/plant at {@code (x,y,z)}.
     *
     * @param world     server world
     * @param neighbour the notified neighbour (a non-air block)
     * @param x,y,z     neighbour position
     */
    public static void popIfUnsupported(World world, Block neighbour, int x, int y, int z) {
        if (world == null || world.isRemote || neighbour == null) return;
        if (!(neighbour instanceof BlockBush)) return; // cheap pre-filter for the common case
        if (world.getBlock(x, y, z) != neighbour) return; // already popped / replaced
        if (((BlockBush) neighbour).canBlockStay(world, x, y, z)) return; // still supported
        // Mirror BlockBush.checkAndDropBlock:
        neighbour.dropBlockAsItem(world, x, y, z, world.getBlockMetadata(x, y, z), 0);
        world.setBlockToAir(x, y, z);
        world.markBlockForUpdate(x, y, z);
    }
}
