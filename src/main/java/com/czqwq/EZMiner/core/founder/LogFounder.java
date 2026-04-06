package com.czqwq.EZMiner.core.founder;

import java.util.concurrent.ConcurrentHashMap;
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

    /**
     * Per-block-type cache for the OreDict wood/leaf check.
     * Avoids creating a new {@link ItemStack} and scanning OreDict entries on every
     * candidate block; the result for each unique {@link Block} instance is computed
     * once and reused for all subsequent occurrences.
     */
    private static final ConcurrentHashMap<Block, Boolean> woodLeafCache = new ConcurrentHashMap<>();

    public LogFounder(Vector3i center, LinkedBlockingQueue<Vector3i> results, EntityPlayer player,
        MinerConfig minerConfig) {
        super(center, results, player, minerConfig);
        setName("EZMiner-BlastLog");
    }

    @Override
    public void run1() {
        int curRadius = 1;
        int highRadius = 1;
        // Track the bounds already covered so each position is visited only once.
        int prevCurRadius = 0;
        int prevHighRadius = 0;
        while (curCount < minerConfig.blockLimit) {
            for (int x = center.x - curRadius; x <= center.x + curRadius; x++) {
                for (int y = center.y - highRadius; y <= center.y + highRadius; y++) {
                    for (int z = center.z - curRadius; z <= center.z + curRadius; z++) {
                        // Skip positions that were inside the previous iteration's bounding box;
                        // they were already processed and adding them again would be a no-op
                        // (foundedPositions dedup) but wastes a world lookup for each air block.
                        if (Math.abs(x - center.x) <= prevCurRadius && Math.abs(y - center.y) <= prevHighRadius
                            && Math.abs(z - center.z) <= prevCurRadius) continue;
                        Vector3i pos = new Vector3i(x, y, z);
                        if (checkCanAdd(pos)) addResult(pos);
                        if (curCount >= minerConfig.blockLimit) return;
                        waitUntil();
                        if (Thread.currentThread()
                            .isInterrupted()) return;
                    }
                }
            }
            prevCurRadius = curRadius;
            prevHighRadius = highRadius;
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
        if (!woodLeafCache.computeIfAbsent(block, LogFounder::isWoodOrLeaf)) return false;
        if (skipHarvestCheck) return true;
        if (player.capabilities.isCreativeMode) return true;
        return block.canHarvestBlock(player, blockMeta);
    }

    /** OreDict check – called at most once per unique {@link Block} type. */
    private static boolean isWoodOrLeaf(Block block) {
        int[] oreIDs = OreDictionary.getOreIDs(new ItemStack(block));
        for (int oreID : oreIDs) {
            String name = OreDictionary.getOreName(oreID);
            if (name.equals("logWood") || name.equals("treeLeaves")) return true;
        }
        return false;
    }
}
