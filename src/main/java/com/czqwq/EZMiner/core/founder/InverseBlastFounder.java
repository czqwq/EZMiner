package com.czqwq.EZMiner.core.founder;

import java.util.concurrent.LinkedBlockingQueue;

import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;

import org.joml.Vector3i;

import com.czqwq.EZMiner.core.MinerConfig;

/**
 * Blast mode – inverse chain: breaks every block within the configured radius that does
 * <em>not</em> match the targeted block.
 *
 * <p>
 * The targeted block is captured at construction time as the {@link #sampleBlock} /
 * {@link #sampleBlockMeta} pair (same as all other founders). The shell-scan BFS
 * inherited from {@link BasePositionFounder} is reused without changes; only
 * {@link #checkCanAdd} is overridden to invert the identity predicate: a candidate
 * position is accepted if and only if its block does <em>not</em> match the sample.
 *
 * <p>
 * Air, liquids, and bedrock are still excluded, as is the block directly under the
 * player's feet, to avoid griefing the player's standing position.
 *
 * <p>
 * Example: aiming at a grass block and activating inverse-chain mode will break all
 * stone, dirt, ore, etc. within the radius while leaving every grass block intact.
 */
public class InverseBlastFounder extends BasePositionFounder {

    public InverseBlastFounder(Vector3i center, LinkedBlockingQueue<Vector3i> results, EntityPlayer player,
        MinerConfig minerConfig) {
        super(center, results, player, minerConfig);
        setName("EZMiner-BlastInverse");
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
        // Accept only blocks that do NOT match the targeted sample.
        if (DeterminingIdentical
            .identical(sampleBlock, sampleBlockMeta, sampleTileEntity, block, blockMeta, pos, player)) return false;
        if (skipHarvestCheck) return true;
        if (player.capabilities.isCreativeMode) return true;
        return block.canHarvestBlock(player, blockMeta);
    }
}
