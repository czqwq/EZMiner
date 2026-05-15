package com.czqwq.EZMiner.chain.execution;

import net.minecraft.entity.player.EntityPlayerMP;

import org.joml.Vector3i;

import com.czqwq.EZMiner.core.crop.CropAdapterRegistry;

/**
 * Crop-mode block executor.
 *
 * <p>
 * Delegates each harvest to {@link CropAdapterRegistry} so that IC2 and CropsNH crops are
 * harvested via their native tile-entity API rather than through
 * {@code ItemInWorldManager.tryHarvestBlock}, which would destroy the crop sticks and remove the
 * entire crop block.
 */
public class CropHarvestActionExecutor implements ChainActionExecutor {

    @Override
    public boolean execute(Vector3i pos, EntityPlayerMP player) {
        return CropAdapterRegistry.harvest(player, pos.x, pos.y, pos.z);
    }
}
