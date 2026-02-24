package com.czqwq.EZMiner.core.founder;

import java.util.concurrent.LinkedBlockingQueue;

import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;

import org.joml.Vector3i;

import com.czqwq.EZMiner.core.MinerConfig;

/**
 * Chain mode: only adds blocks that are adjacent (within smallRadius) to an already-found block
 * AND match the sample block type.
 */
public class ChainPositionFounder extends BasePositionFounder {

    public ChainPositionFounder(Vector3i center, LinkedBlockingQueue<Vector3i> results, EntityPlayer player,
        MinerConfig minerConfig) {
        super(center, results, player, minerConfig);
        setName("EZMiner-ChainSearch");
    }

    @Override
    public void run1() {
        int curRadius = 1;
        while (curCount < minerConfig.blockLimit && curRadius <= minerConfig.bigRadius) {
            for (int x = center.x - curRadius; x <= center.x + curRadius; x++) {
                for (int y = center.y - curRadius; y <= center.y + curRadius; y++) {
                    for (int z = center.z - curRadius; z <= center.z + curRadius; z++) {
                        Vector3i pos = new Vector3i(x, y, z);
                        if (checkCanAdd(pos)) addResult(pos);
                        if (curCount >= minerConfig.blockLimit) return;
                        waitUntil();
                        if (Thread.currentThread()
                            .isInterrupted()) return;
                    }
                }
            }
            curRadius++;
        }
    }

    @Override
    public boolean checkCanAdd(Vector3i pos) {
        if (foundedPositions.contains(pos)) return false;
        Block block = player.worldObj.getBlock(pos.x, pos.y, pos.z);
        if (block.equals(Blocks.air) || block.getMaterial()
            .isLiquid() || block.equals(Blocks.bedrock)) return false;
        int blockMeta = player.worldObj.getBlockMetadata(pos.x, pos.y, pos.z);
        Vector3i playerPos = playerFloorPos();
        if (pos.x == playerPos.x && pos.y == (playerPos.y - 1) && pos.z == playerPos.z) return false;

        // Must match the sample block
        if (!DeterminingIdentical.identical(sampleBlock, sampleBlockMeta, sampleTileEntity, pos, player)) return false;

        // Must be adjacent to an already-found block within smallRadius
        boolean adjacent = false;
        for (Vector3i known : foundedPositions) {
            Vector3i off = new Vector3i(known).sub(pos);
            if (Math.abs(off.x) <= minerConfig.smallRadius && Math.abs(off.y) <= minerConfig.smallRadius
                && Math.abs(off.z) <= minerConfig.smallRadius) {
                adjacent = true;
                break;
            }
        }
        if (!adjacent) return false;

        if (player.capabilities.isCreativeMode) return true;
        return block.canHarvestBlock(player, blockMeta);
    }
}
