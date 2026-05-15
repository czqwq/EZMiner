package com.czqwq.EZMiner.core.founder;

import java.util.concurrent.LinkedBlockingQueue;

import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.world.World;

import org.joml.Vector3i;

import com.czqwq.EZMiner.core.MinerConfig;
import com.czqwq.EZMiner.core.crop.CropAdapterRegistry;

/**
 * Crop-mode position founder: collects vanilla, IC2, and CropsNH crops within the configured
 * radius for chain harvesting.
 */
public class CropFounder extends BasePositionFounder {

    public CropFounder(Vector3i center, LinkedBlockingQueue<Vector3i> results, EntityPlayer player,
        MinerConfig minerConfig) {
        super(center, results, player, minerConfig);
        setName("EZMiner-CropSearch");
    }

    /**
     * Returns {@code true} when the block at the given position is a harvestable (mature) crop,
     * delegating to all registered {@link com.czqwq.EZMiner.core.crop.ICropAdapter}s.
     */
    public static boolean isMatureCrop(World world, int x, int y, int z) {
        if (world == null || !world.blockExists(x, y, z)) return false;
        return CropAdapterRegistry.isMatureCrop(world, x, y, z);
    }

    @Override
    public boolean checkCanAdd(Vector3i pos) {
        if (isVisited(pos.x, pos.y, pos.z)) return false;
        if (player.worldObj == null) return false;
        if (!player.worldObj.blockExists(pos.x, pos.y, pos.z)) return false;
        Block block = player.worldObj.getBlock(pos.x, pos.y, pos.z);
        if (block.equals(Blocks.air) || block.getMaterial()
            .isLiquid() || block.equals(Blocks.bedrock)) return false;
        if (pos.x == cachedPlayerFloorX && pos.y == (cachedPlayerFloorY - 1) && pos.z == cachedPlayerFloorZ)
            return false;
        return CropAdapterRegistry.isCrop(player.worldObj, pos.x, pos.y, pos.z);
    }
}
