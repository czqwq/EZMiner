package com.czqwq.EZMiner.core.crop;

import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

import mods.natura.blocks.crops.CropBlock;

/**
 * Crop adapter for Natura {@link CropBlock} crops (barley and cotton).
 *
 * <p>
 * A single {@link CropBlock} instance covers two crop families sharing metadata space:
 * <ul>
 * <li><b>Barley</b> – meta 0–3, mature at meta 3. No non-destructive right-click harvest exists;
 * the block is broken via {@code tryHarvestBlock} and the adapter replants at meta 0 when the
 * soil below can sustain the crop.</li>
 * <li><b>Cotton</b> – meta 4–8, mature at meta 8. The block provides a non-destructive
 * right-click harvest ({@link CropBlock#onBlockActivated}) that resets growth to meta 6 and drops
 * cotton without breaking the plant; the adapter calls this method directly.</li>
 * </ul>
 *
 * <p>
 * This adapter is only registered when the {@code Natura} mod is present; see
 * {@link CropAdapterRegistry#init()}.
 */
public class NaturaCropAdapter implements ICropAdapter {

    /** Metadata threshold separating barley (below) from cotton (at or above). */
    private static final int COTTON_START_META = 4;

    @Override
    public boolean isCrop(World world, int x, int y, int z) {
        return world.getBlock(x, y, z) instanceof CropBlock;
    }

    @Override
    public boolean isMatureCrop(World world, int x, int y, int z) {
        Block block = world.getBlock(x, y, z);
        if (!(block instanceof CropBlock cropBlock)) return false;
        int meta = world.getBlockMetadata(x, y, z);
        return meta == cropBlock.getMaxGrowth(meta);
    }

    @Override
    public boolean harvest(EntityPlayerMP player, int x, int y, int z) {
        Block block = player.worldObj.getBlock(x, y, z);
        if (!(block instanceof CropBlock cropBlock)) return false;
        int meta = player.worldObj.getBlockMetadata(x, y, z);
        if (meta < COTTON_START_META) {
            // Barley: destructive harvest + replant, drops go through HarvestDropsEvent.
            return harvestBarley(player, x, y, z, cropBlock);
        } else {
            // Cotton: non-destructive – onBlockActivated resets growth and spawns the drop.
            return cropBlock.onBlockActivated(player.worldObj, x, y, z, player, 0, 0f, 0f, 0f);
        }
    }

    private boolean harvestBarley(EntityPlayerMP player, int x, int y, int z, CropBlock cropBlock) {
        boolean harvested = player.theItemInWorldManager.tryHarvestBlock(x, y, z);
        if (harvested) {
            replantIfPossible(player.worldObj, x, y, z, cropBlock);
        }
        return harvested;
    }

    private void replantIfPossible(World world, int x, int y, int z, CropBlock cropBlock) {
        if (y <= 0) return;
        if (world.getBlock(x, y, z) != Blocks.air) return;
        Block soil = world.getBlock(x, y - 1, z);
        if (soil == null || soil == Blocks.air) return;
        if (soil == Blocks.farmland || soil.canSustainPlant(world, x, y - 1, z, ForgeDirection.UP, cropBlock)) {
            world.setBlock(x, y, z, cropBlock, 0, 3);
        }
    }
}
