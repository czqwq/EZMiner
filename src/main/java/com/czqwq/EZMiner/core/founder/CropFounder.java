package com.czqwq.EZMiner.core.founder;

import java.util.concurrent.LinkedBlockingQueue;

import net.minecraft.block.Block;
import net.minecraft.block.BlockCrops;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

import org.joml.Vector3i;

import com.czqwq.EZMiner.core.MinerConfig;

import ic2.api.crops.CropCard;
import ic2.core.crop.TileEntityCrop;

/** Blast mode - crop harvest: harvests vanilla crops and IC2 crops in radius. */
public class CropFounder extends BasePositionFounder {

    /** Metadata value at which a vanilla {@link BlockCrops} crop is considered fully grown. */
    public static final int VANILLA_CROP_MATURE_META = 7;

    public CropFounder(Vector3i center, LinkedBlockingQueue<Vector3i> results, EntityPlayer player,
        MinerConfig minerConfig) {
        super(center, results, player, minerConfig);
        setName("EZMiner-BlastCrop");
    }

    /**
     * Returns {@code true} when the block at the given position is a harvestable (mature) crop.
     *
     * <p>
     * {@link BlockCrops} crops (wheat, carrot, potato, beetroot in 1.7.10) are considered
     * mature at metadata {@code >= 7}. IC2 crops are considered mature when their
     * {@link CropCard#canBeHarvested(TileEntityCrop)} returns {@code true}.
     */
    public static boolean isMatureCrop(World world, int x, int y, int z) {
        if (world == null || !world.blockExists(x, y, z)) return false;
        Block block = world.getBlock(x, y, z);
        if (block instanceof BlockCrops) {
            return world.getBlockMetadata(x, y, z) >= VANILLA_CROP_MATURE_META;
        }
        TileEntity tile = world.getTileEntity(x, y, z);
        if (tile instanceof TileEntityCrop) {
            TileEntityCrop crop = (TileEntityCrop) tile;
            CropCard card = crop.getCrop();
            return card != null && card.canBeHarvested(crop);
        }
        return false;
    }

    @Override
    public boolean checkCanAdd(Vector3i pos) {
        if (isVisited(pos.x, pos.y, pos.z)) return false;
        if (player.worldObj == null) return false;
        if (!player.worldObj.blockExists(pos.x, pos.y, pos.z)) return false;
        Block block = player.worldObj.getBlock(pos.x, pos.y, pos.z);
        TileEntity tile = player.worldObj.getTileEntity(pos.x, pos.y, pos.z);
        if (block.equals(Blocks.air) || block.getMaterial()
            .isLiquid() || block.equals(Blocks.bedrock)) return false;
        if (pos.x == cachedPlayerFloorX && pos.y == (cachedPlayerFloorY - 1) && pos.z == cachedPlayerFloorZ)
            return false;
        if (!(block instanceof BlockCrops) && !(tile instanceof TileEntityCrop)) return false;
        return true;
    }
}
