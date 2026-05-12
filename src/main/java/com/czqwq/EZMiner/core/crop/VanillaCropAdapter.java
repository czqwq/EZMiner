package com.czqwq.EZMiner.core.crop;

import net.minecraft.block.Block;
import net.minecraft.block.BlockCrops;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

/**
 * Crop adapter for vanilla {@link BlockCrops} (wheat, carrot, potato, beetroot).
 *
 * <p>
 * Harvesting delegates to {@code tryHarvestBlock}, which fires
 * {@link net.minecraftforge.event.world.BlockEvent.HarvestDropsEvent} so that EZMiner's drop
 * collector can aggregate the items. After a successful harvest the adapter replants the same crop
 * block at the same position (metadata&nbsp;0), preserving the farm without manual replanting.
 */
public class VanillaCropAdapter implements ICropAdapter {

    /** Minimum block-metadata value at which a {@link BlockCrops} is fully grown. */
    public static final int MATURE_META = 7;

    @Override
    public boolean isCrop(World world, int x, int y, int z) {
        return world.getBlock(x, y, z) instanceof BlockCrops;
    }

    @Override
    public boolean isMatureCrop(World world, int x, int y, int z) {
        Block block = world.getBlock(x, y, z);
        return block instanceof BlockCrops && world.getBlockMetadata(x, y, z) >= MATURE_META;
    }

    @Override
    public boolean harvest(EntityPlayerMP player, int x, int y, int z) {
        Block preBlock = player.worldObj.getBlock(x, y, z);
        int preMeta = player.worldObj.getBlockMetadata(x, y, z);
        boolean harvested = player.theItemInWorldManager.tryHarvestBlock(x, y, z);
        if (harvested && preBlock instanceof BlockCrops && preMeta >= MATURE_META) {
            replantIfPossible(player.worldObj, x, y, z, (BlockCrops) preBlock);
        }
        return harvested;
    }

    private void replantIfPossible(World world, int x, int y, int z, BlockCrops cropBlock) {
        if (y <= 0) return;
        if (world.getBlock(x, y, z) != Blocks.air) return;
        Block soil = world.getBlock(x, y - 1, z);
        if (soil == null || soil == Blocks.air) return;
        if (soil == Blocks.farmland || soil.canSustainPlant(world, x, y - 1, z, ForgeDirection.UP, cropBlock)) {
            world.setBlock(x, y, z, cropBlock, 0, 3);
        }
    }
}
