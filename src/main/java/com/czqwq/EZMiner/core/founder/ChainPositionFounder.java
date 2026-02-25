package com.czqwq.EZMiner.core.founder;

import java.util.Comparator;
import java.util.PriorityQueue;
import java.util.concurrent.LinkedBlockingQueue;

import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;

import org.joml.Vector3i;

import com.czqwq.EZMiner.core.MinerConfig;

/**
 * Chain mode: priority-queue BFS flood-fill from the targeted block.
 *
 * <p>
 * Blocks are expanded in non-decreasing Euclidean-distance order from the
 * origin (same strategy as Bandit-Legacy's {@code ManhattanExecutorGenerator}).
 * This produces "ring-layer" outward scanning: all same-type blocks at distance 1
 * are found before those at distance 2, etc., so the player sees a smooth sphere
 * of outlines expanding from the targeted block.
 *
 * <p>
 * Each found block expands its {@code smallRadius}-neighbourhood; only blocks
 * of the same type within {@code bigRadius} of the origin are included.
 */
public class ChainPositionFounder extends BasePositionFounder {

    public ChainPositionFounder(Vector3i center, LinkedBlockingQueue<Vector3i> results, EntityPlayer player,
        MinerConfig minerConfig) {
        super(center, results, player, minerConfig);
        setName("EZMiner-ChainSearch");
    }

    @Override
    public void run1() {
        // Priority-queue ordered by distance² from center – smallest distance first.
        PriorityQueue<Vector3i> frontier = new PriorityQueue<>(Comparator.comparingDouble(v -> distSq(v, center)));
        // Center is already in foundedPositions (added by BasePositionFounder constructor).
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

    private static double distSq(Vector3i a, Vector3i b) {
        double dx = a.x - b.x, dy = a.y - b.y, dz = a.z - b.z;
        return dx * dx + dy * dy + dz * dz;
    }

    /**
     * No explicit adjacency check needed – PQ-BFS guarantees we only visit positions
     * reachable from an already-found block within {@code smallRadius}.
     */
    @Override
    public boolean checkCanAdd(Vector3i pos) {
        if (foundedPositions.contains(pos)) return false;
        if (player.worldObj == null) return false; // player logged out
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
