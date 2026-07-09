package com.czqwq.EZMiner.core.founder;

import java.util.concurrent.LinkedBlockingQueue;

import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;

import org.joml.Vector3i;

import com.czqwq.EZMiner.core.MinerConfig;

/**
 * Fuzzy chain mode: priority-queue BFS flood-fill that matches blocks by type only,
 * ignoring metadata.
 *
 * <p>
 * Standard chain mode ({@link ChainPositionFounder}) requires an exact block-type
 * <em>and</em> metadata match. This means blocks that share the same material but
 * differ in orientation (e.g. oak logs placed along the X, Y, or Z axis each have
 * distinct metadata values in 1.7.10) are treated as different blocks, cutting the
 * chain short when the tree was felled at an angle or when logs were placed sideways.
 *
 * <p>
 * Fuzzy mode relaxes the identity check to block-type equality only, so any placement
 * orientation or variant within the same {@link Block} instance is included in the
 * chain. The BFS algorithm (PQ ordered by distance² from the origin) is inherited
 * unchanged from {@link ChainPositionFounder}.
 */
public class FuzzyChainPositionFounder extends ChainPositionFounder {

    public FuzzyChainPositionFounder(Vector3i center, LinkedBlockingQueue<Vector3i> results, EntityPlayer player,
        MinerConfig minerConfig) {
        super(center, results, player, minerConfig);
        setName("EZMiner-FuzzyChainSearch");
    }

    /** Fuzzy matching: block class equality only, ignoring metadata. */
    @Override
    protected boolean checkCanAddImpl(Vector3i pos) {
        if (player.worldObj == null) return false;
        if (!player.worldObj.blockExists(pos.x, pos.y, pos.z)) return false;
        Block block = player.worldObj.getBlock(pos.x, pos.y, pos.z);
        if (block.equals(Blocks.air) || block.getMaterial()
            .isLiquid() || block.equals(Blocks.bedrock)) return false;
        int blockMeta = player.worldObj.getBlockMetadata(pos.x, pos.y, pos.z);
        if (pos.x == cachedPlayerFloorX && pos.y == (cachedPlayerFloorY - 1) && pos.z == cachedPlayerFloorZ)
            return false;
        if (!sampleBlock.getClass()
            .equals(block.getClass())) return false;
        if (skipHarvestCheck) return true;
        if (player.capabilities.isCreativeMode) return true;
        return block.canHarvestBlock(player, blockMeta);
    }
}
