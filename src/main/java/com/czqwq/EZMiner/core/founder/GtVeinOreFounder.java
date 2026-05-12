package com.czqwq.EZMiner.core.founder;

import java.util.concurrent.LinkedBlockingQueue;

import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;

import org.joml.Vector3i;

import com.czqwq.EZMiner.core.MinerConfig;

/**
 * Blast mode – GT vein ore (precise): breaks only GregTech large-vein ore blocks,
 * explicitly excluding GT surface small ores (贫瘠矿).
 *
 * <p>
 * Compared to {@link OreFounder} (which accepts any ore block recognised by
 * {@link DeterminingIdentical#isOreBlock}), this founder is deliberately narrow:
 * <ul>
 * <li>For the new GT ore system ({@code GTBlockOre}): only blocks where
 * {@code GTBlockOre.isSmallOre(meta) == false} are accepted.</li>
 * <li>For the legacy GT ore system ({@code BlockOresAbstract}): all blocks are
 * accepted, as the legacy system uses a separate class for small ores.</li>
 * <li>Non-GT ores (vanilla, BartWorks, GTPlusPlus, AE2, …) are always rejected.</li>
 * </ul>
 *
 * <p>
 * This makes it ideal for "vein-chain" use: activate on a GT ore vein and break
 * only the large-vein ore blocks without accidentally consuming the scattered
 * small surface deposits nearby.
 */
public class GtVeinOreFounder extends BasePositionFounder {

    public GtVeinOreFounder(Vector3i center, LinkedBlockingQueue<Vector3i> results, EntityPlayer player,
        MinerConfig minerConfig) {
        super(center, results, player, minerConfig);
        setName("EZMiner-BlastGTVein");
    }

    @Override
    public boolean checkCanAdd(Vector3i pos) {
        if (isVisited(pos.x, pos.y, pos.z)) return false;
        if (player.worldObj == null) return false;
        if (!player.worldObj.blockExists(pos.x, pos.y, pos.z)) return false;
        Block block = player.worldObj.getBlock(pos.x, pos.y, pos.z);
        if (block.equals(Blocks.air) || block.getMaterial()
            .isLiquid() || block.equals(Blocks.bedrock)) return false;
        int blockMeta = player.worldObj.getBlockMetadata(pos.x, pos.y, pos.z);
        if (pos.x == cachedPlayerFloorX && pos.y == (cachedPlayerFloorY - 1) && pos.z == cachedPlayerFloorZ)
            return false;
        if (!DeterminingIdentical.isGTLargeVeinOre(block, blockMeta)) return false;
        if (skipHarvestCheck) return true;
        if (player.capabilities.isCreativeMode) return true;
        return block.canHarvestBlock(player, blockMeta);
    }
}
