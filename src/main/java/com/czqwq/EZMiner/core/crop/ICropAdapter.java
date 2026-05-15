package com.czqwq.EZMiner.core.crop;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.world.World;

/**
 * Per-mod adapter for detecting and harvesting crops without destroying the crop block or sticks.
 *
 * <p>
 * Implementations are registered at FML post-init via {@link CropAdapterRegistry#init()} and are
 * queried in registration order for every crop-mode operation.
 */
public interface ICropAdapter {

    /**
     * Returns {@code true} if the block at {@code (x, y, z)} belongs to this adapter's crop type
     * (mature or not). Used by {@link com.czqwq.EZMiner.core.founder.CropFounder} to include the
     * position in the harvest-scan BFS.
     */
    boolean isCrop(World world, int x, int y, int z);

    /**
     * Returns {@code true} if the crop at {@code (x, y, z)} is fully grown and ready to harvest.
     */
    boolean isMatureCrop(World world, int x, int y, int z);

    /**
     * Harvests the crop at {@code (x, y, z)}.
     *
     * <p>
     * Implementations must <em>not</em> break the underlying crop block or sticks; they should
     * only collect the produce and reset the crop's growth state.
     *
     * @return {@code true} if the crop was successfully harvested.
     */
    boolean harvest(EntityPlayerMP player, int x, int y, int z);
}
