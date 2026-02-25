package com.czqwq.EZMiner.core.founder;

import java.util.ArrayDeque;
import java.util.concurrent.LinkedBlockingQueue;

import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;

import org.joml.Vector3i;

import com.czqwq.EZMiner.core.MinerConfig;

/**
 * Chain mode: BFS flood-fill from the targeted block.
 *
 * <p>
 * For each discovered block we expand outward by up to {@code smallRadius} in each axis,
 * constrained to a cube of {@code bigRadius} from the origin. Only blocks of the same type
 * as the targeted block are included.
 *
 * <p>
 * BFS ordering guarantees that nearby blocks are always discovered before distant ones,
 * fixing the issue where expanding-shell iteration could miss adjacent blocks while
 * finding distant ones (because shell order is not adjacency order).
 */
public class ChainPositionFounder extends BasePositionFounder {

    public ChainPositionFounder(Vector3i center, LinkedBlockingQueue<Vector3i> results, EntityPlayer player,
        MinerConfig minerConfig) {
        super(center, results, player, minerConfig);
        setName("EZMiner-ChainSearch");
    }

    @Override
    public void run1() {
        // BFS frontier – centre is already in foundedPositions (added by super constructor)
        ArrayDeque<Vector3i> frontier = new ArrayDeque<>();
        frontier.add(center);

        while (!frontier.isEmpty() && curCount < minerConfig.blockLimit) {
            Vector3i current = frontier.poll();

            for (int dx = -minerConfig.smallRadius; dx <= minerConfig.smallRadius; dx++) {
                for (int dy = -minerConfig.smallRadius; dy <= minerConfig.smallRadius; dy++) {
                    for (int dz = -minerConfig.smallRadius; dz <= minerConfig.smallRadius; dz++) {
                        if (dx == 0 && dy == 0 && dz == 0) continue;
                        Vector3i candidate = new Vector3i(current.x + dx, current.y + dy, current.z + dz);
                        if (!inBigRadius(candidate)) continue;
                        if (checkCanAdd(candidate)) {
                            addResult(candidate);
                            frontier.add(candidate);
                        }
                        if (curCount >= minerConfig.blockLimit) return;
                        if (Thread.currentThread()
                            .isInterrupted()) return;
                    }
                }
            }
        }
    }

    private boolean inBigRadius(Vector3i pos) {
        return Math.abs(pos.x - center.x) <= minerConfig.bigRadius
            && Math.abs(pos.y - center.y) <= minerConfig.bigRadius
            && Math.abs(pos.z - center.z) <= minerConfig.bigRadius;
    }

    /**
     * No explicit adjacency check needed – BFS guarantees we only visit positions reachable
     * from an already-found block within {@code smallRadius}.
     */
    @Override
    public boolean checkCanAdd(Vector3i pos) {
        if (foundedPositions.contains(pos)) return false;
        Block block = player.worldObj.getBlock(pos.x, pos.y, pos.z);
        if (block.equals(Blocks.air) || block.getMaterial()
            .isLiquid() || block.equals(Blocks.bedrock)) return false;
        int blockMeta = player.worldObj.getBlockMetadata(pos.x, pos.y, pos.z);
        Vector3i playerPos = playerFloorPos();
        if (pos.x == playerPos.x && pos.y == (playerPos.y - 1) && pos.z == playerPos.z) return false;
        if (!DeterminingIdentical.identical(sampleBlock, sampleBlockMeta, sampleTileEntity, pos, player)) return false;
        if (player.capabilities.isCreativeMode) return true;
        return block.canHarvestBlock(player, blockMeta);
    }
}
