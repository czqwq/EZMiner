package com.czqwq.EZMiner.core.founder;

import java.util.concurrent.LinkedBlockingQueue;

import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraftforge.oredict.OreDictionary;

import org.joml.Vector3i;

import com.czqwq.EZMiner.core.MinerConfig;

/** Blast mode – logging: breaks only wood and leaf blocks. */
public class LogFounder extends BasePositionFounder {

    public LogFounder(Vector3i center, LinkedBlockingQueue<Vector3i> results, EntityPlayer player,
        MinerConfig minerConfig) {
        super(center, results, player, minerConfig);
        setName("EZMiner-BlastLog");
    }

    @Override
    public void run1() {
        int curRadius = 1;
        int highRadius = 1;
        while (curCount < minerConfig.blockLimit) {
            for (int x = center.x - curRadius; x <= center.x + curRadius; x++) {
                for (int y = center.y - highRadius; y <= center.y + highRadius; y++) {
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
            curRadius = Math.min(curRadius + 1, minerConfig.bigRadius);
            highRadius++;
            if (curRadius >= minerConfig.bigRadius && highRadius > minerConfig.bigRadius * 4) break;
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

        // Check OreDict for log/leaf
        boolean isWoodOrLeaf = false;
        int[] oreIDs = OreDictionary.getOreIDs(new ItemStack(block));
        for (int oreID : oreIDs) {
            String name = OreDictionary.getOreName(oreID);
            if (name.equals("logWood") || name.equals("treeLeaves")) {
                isWoodOrLeaf = true;
                break;
            }
        }
        if (!isWoodOrLeaf) return false;
        if (player.capabilities.isCreativeMode) return true;
        return block.canHarvestBlock(player, blockMeta);
    }
}
