package com.czqwq.EZMiner.core.crop;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

import ic2.api.crops.CropCard;
import ic2.core.crop.TileEntityCrop;

/**
 * Crop adapter for IC2 {@link TileEntityCrop} crops.
 *
 * <p>
 * Harvesting calls {@link TileEntityCrop#harvest(boolean) harvest(false)}, which resets the crop's
 * growth-size counter and spawns the produce as item entities near the crop block. The crop-stick
 * block itself is <em>not</em> broken, so the farm infrastructure remains intact.
 *
 * <p>
 * Because IC2 spawns drops directly rather than through
 * {@link net.minecraftforge.event.world.BlockEvent.HarvestDropsEvent}, those drops are not
 * captured by EZMiner's drop collector; they appear at the crop block's location instead.
 */
public class Ic2CropAdapter implements ICropAdapter {

    @Override
    public boolean isCrop(World world, int x, int y, int z) {
        return world.getTileEntity(x, y, z) instanceof TileEntityCrop;
    }

    @Override
    public boolean isMatureCrop(World world, int x, int y, int z) {
        TileEntity te = world.getTileEntity(x, y, z);
        if (!(te instanceof TileEntityCrop crop)) return false;
        CropCard card = crop.getCrop();
        return card != null && card.canBeHarvested(crop);
    }

    @Override
    public boolean harvest(EntityPlayerMP player, int x, int y, int z) {
        TileEntity te = player.worldObj.getTileEntity(x, y, z);
        if (!(te instanceof TileEntityCrop crop)) return false;
        // harvest(false) = player-style harvest: does not require optimal size,
        // resets size to getSizeAfterHarvest(), and drops items as world entities.
        return crop.harvest(false);
    }
}
