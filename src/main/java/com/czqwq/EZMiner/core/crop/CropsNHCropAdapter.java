package com.czqwq.EZMiner.core.crop;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

import com.gtnewhorizon.cropsnh.api.ICropStickTile;

/**
 * Crop adapter for CropsNH {@link ICropStickTile} crops.
 *
 * <p>
 * Harvesting calls {@link ICropStickTile#doPlayerHarvest(net.minecraft.entity.player.EntityPlayer,
 * boolean) doPlayerHarvest(player, false)}, which collects the produce, resets the crop's growth
 * state, and drops items near the crop-stick block. The sticks themselves are preserved.
 *
 * <p>
 * This adapter is only registered when the {@code cropsnh} mod is present; see
 * {@link CropAdapterRegistry#init()}.
 */
public class CropsNHCropAdapter implements ICropAdapter {

    @Override
    public boolean isCrop(World world, int x, int y, int z) {
        TileEntity te = world.getTileEntity(x, y, z);
        return te instanceof ICropStickTile tile && tile.hasCrop();
    }

    @Override
    public boolean isMatureCrop(World world, int x, int y, int z) {
        TileEntity te = world.getTileEntity(x, y, z);
        if (!(te instanceof ICropStickTile tile)) return false;
        return tile.canHarvest();
    }

    @Override
    public boolean harvest(EntityPlayerMP player, int x, int y, int z) {
        TileEntity te = player.worldObj.getTileEntity(x, y, z);
        if (!(te instanceof ICropStickTile tile)) return false;
        // isRemovingCrop=false: harvest only, do not pull out the seed / break the sticks.
        return tile.doPlayerHarvest(player, false);
    }
}
