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
 * Chain mode: priority-queue BFS flood-fill, same-type blocks only.
 * Expands in ring layers by Euclidean distance from origin.
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
        // Center is already in visitedPositions (added by BasePositionFounder constructor).
        frontier.add(center);

        // Candidate checks performed in the current tick window. We yield to the
        // tick-pause mechanism every SCAN_SLICE_SIZE checks so that with a large
        // smallRadius (e.g. 2 → (2*2+1)³ = 125 neighbours per node) the search
        // thread does not monopolise the CPU for an entire server tick.
        int checksThisSlice = 0;
        final int SCAN_SLICE_SIZE = 64;

        while (!frontier.isEmpty() && curCount < minerConfig.blockLimit) {
            Vector3i current = frontier.poll();

            for (int dx = -minerConfig.smallRadius; dx <= minerConfig.smallRadius; dx++) {
                int cx = current.x + dx;
                // Reject out-of-radius candidates early, before any allocation.
                if (Math.abs(cx - center.x) > minerConfig.bigRadius) continue;
                for (int dy = -minerConfig.smallRadius; dy <= minerConfig.smallRadius; dy++) {
                    int cy = current.y + dy;
                    if (Math.abs(cy - center.y) > minerConfig.bigRadius) continue;
                    for (int dz = -minerConfig.smallRadius; dz <= minerConfig.smallRadius; dz++) {
                        if (dx == 0 && dy == 0 && dz == 0) continue;
                        int cz = current.z + dz;
                        if (Math.abs(cz - center.z) > minerConfig.bigRadius) continue;
                        // Skip already-visited positions without allocating a Vector3i.
                        if (isVisited(cx, cy, cz)) continue;
                        Vector3i candidate = new Vector3i(cx, cy, cz);
                        if (checkCanAdd(candidate)) {
                            addResult(candidate);
                            frontier.add(candidate);
                        }
                        if (curCount >= minerConfig.blockLimit) return;
                        if (Thread.currentThread()
                            .isInterrupted()) return;
                        checksThisSlice++;
                        if (checksThisSlice >= SCAN_SLICE_SIZE) {
                            checksThisSlice = 0;
                            waitUntil();
                            if (Thread.currentThread()
                                .isInterrupted()) return;
                        }
                    }
                }
            }
            // Always yield at the end of a frontier node too.
            waitUntil();
            if (Thread.currentThread()
                .isInterrupted()) return;
        }
    }

    private static double distSq(Vector3i a, Vector3i b) {
        double dx = a.x - b.x, dy = a.y - b.y, dz = a.z - b.z;
        return dx * dx + dy * dy + dz * dz;
    }

    /** Pre-fetches block+meta for identical() check — single world lookup per candidate. */
    @Override
    public boolean checkCanAdd(Vector3i pos) {
        if (isVisited(pos.x, pos.y, pos.z)) return false;
        if (player.worldObj == null) return false; // player logged out
        if (!player.worldObj.blockExists(pos.x, pos.y, pos.z)) return false;
        Block block = player.worldObj.getBlock(pos.x, pos.y, pos.z);
        if (block.equals(Blocks.air) || block.getMaterial()
            .isLiquid() || block.equals(Blocks.bedrock)) return false;
        int blockMeta = player.worldObj.getBlockMetadata(pos.x, pos.y, pos.z);
        if (pos.x == cachedPlayerFloorX && pos.y == (cachedPlayerFloorY - 1) && pos.z == cachedPlayerFloorZ)
            return false;
        if (!DeterminingIdentical
            .identical(sampleBlock, sampleBlockMeta, sampleTileEntity, block, blockMeta, pos, player)) return false;
        if (skipHarvestCheck) return true;
        if (player.capabilities.isCreativeMode) return true;
        return block.canHarvestBlock(player, blockMeta);
    }
}
