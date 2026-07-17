package com.czqwq.EZMiner.core.founder;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;
import java.util.Queue;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.LinkedBlockingQueue;

import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;

import org.joml.Vector3i;

import com.czqwq.EZMiner.Config;
import com.czqwq.EZMiner.core.MinerConfig;
import com.czqwq.EZMiner.thread.SearchWorkerPool;

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
    protected void doSingleThreadedSearch() {
        if (Config.useDualFrontierBfs) {
            doSingleThreadedSearchDualFrontier();
        } else {
            doSingleThreadedSearchPriorityQueue();
        }
    }

    /** Original PriorityQueue-based BFS (kept as fallback). */
    private void doSingleThreadedSearchPriorityQueue() {
        PriorityQueue<Vector3i> frontier = new PriorityQueue<>(Comparator.comparingDouble(v -> distSq(v, center)));
        frontier.add(center);

        while (!frontier.isEmpty() && curCount.get() < minerConfig.blockLimit) {
            Vector3i current = frontier.poll();

            for (int dx = -minerConfig.smallRadius; dx <= minerConfig.smallRadius; dx++) {
                int cx = current.x + dx;
                if (Math.abs(cx - center.x) > minerConfig.bigRadius) continue;
                for (int dy = -minerConfig.smallRadius; dy <= minerConfig.smallRadius; dy++) {
                    int cy = current.y + dy;
                    if (Math.abs(cy - center.y) > minerConfig.bigRadius) continue;
                    for (int dz = -minerConfig.smallRadius; dz <= minerConfig.smallRadius; dz++) {
                        if (dx == 0 && dy == 0 && dz == 0) continue;
                        int cz = current.z + dz;
                        if (Math.abs(cz - center.z) > minerConfig.bigRadius) continue;
                        if (isVisited(cx, cy, cz)) continue;
                        Vector3i candidate = new Vector3i(cx, cy, cz);
                        if (checkCanAdd(candidate)) {
                            addResult(candidate);
                            frontier.add(candidate);
                        }
                        if (curCount.get() >= minerConfig.blockLimit) return;
                        if (!consumeBudget()) return;
                    }
                }
            }
            if (!consumeBudget()) return;
        }
    }

    /**
     * Dual-frontier BFS: two plain queues ({@code currentFrontier} + {@code nextFrontier})
     * replace the PriorityQueue for O(1) poll instead of O(log n). Wave-front order
     * differs slightly from distance-ordered expansion but total block count is identical.
     */
    private void doSingleThreadedSearchDualFrontier() {
        // Single-threaded: ArrayDeque avoids ConcurrentLinkedQueue's per-offer node allocation.
        Queue<Vector3i> currentFrontier = new ArrayDeque<>();
        Queue<Vector3i> nextFrontier = new ArrayDeque<>();
        currentFrontier.add(center);

        while (curCount.get() < minerConfig.blockLimit) {
            Vector3i current = currentFrontier.poll();
            if (current == null) {
                // Rotate: swap frontiers, start next BFS wave
                Queue<Vector3i> tmp = currentFrontier;
                currentFrontier = nextFrontier;
                nextFrontier = tmp;
                if (currentFrontier.isEmpty()) break; // BFS complete
                continue;
            }

            for (int dx = -minerConfig.smallRadius; dx <= minerConfig.smallRadius; dx++) {
                int cx = current.x + dx;
                if (Math.abs(cx - center.x) > minerConfig.bigRadius) continue;
                for (int dy = -minerConfig.smallRadius; dy <= minerConfig.smallRadius; dy++) {
                    int cy = current.y + dy;
                    if (Math.abs(cy - center.y) > minerConfig.bigRadius) continue;
                    for (int dz = -minerConfig.smallRadius; dz <= minerConfig.smallRadius; dz++) {
                        if (dx == 0 && dy == 0 && dz == 0) continue;
                        int cz = current.z + dz;
                        if (Math.abs(cz - center.z) > minerConfig.bigRadius) continue;
                        if (isVisited(cx, cy, cz)) continue;
                        Vector3i candidate = new Vector3i(cx, cy, cz);
                        if (checkCanAdd(candidate)) {
                            addResult(candidate);
                            nextFrontier.add(candidate);
                        }
                        if (curCount.get() >= minerConfig.blockLimit) return;
                        if (!consumeBudget()) return;
                    }
                }
            }
            if (!consumeBudget()) return;
        }
    }

    @Override
    protected void doMultiThreadedSearch() {
        if (Config.useDualFrontierBfs) {
            doMultiThreadedSearchDualFrontier();
        } else {
            doMultiThreadedSearchPriorityQueue();
        }
    }

    /** Original PriorityQueue-based multi-threaded BFS (kept as fallback). */
    private void doMultiThreadedSearchPriorityQueue() {
        final int numWorkers = Math.max(1, Config.searchWorkerThreads);
        final int FRONTIER_BATCH = 8;
        final Object frontierLock = new Object();

        PriorityQueue<Vector3i> frontier = new PriorityQueue<>(Comparator.comparingDouble(v -> distSq(v, center)));
        frontier.add(center);

        while (curCount.get() < minerConfig.blockLimit) {
            List<Vector3i> batch = new ArrayList<>(FRONTIER_BATCH);
            synchronized (frontierLock) {
                while (!frontier.isEmpty() && batch.size() < FRONTIER_BATCH
                    && curCount.get() < minerConfig.blockLimit) {
                    batch.add(frontier.poll());
                }
            }
            if (batch.isEmpty()) break;

            List<Vector3i> allNeighbours = new ArrayList<>(batch.size() * 124);
            for (Vector3i node : batch) {
                collectNeighbours(node, allNeighbours);
            }
            if (allNeighbours.isEmpty()) {
                if (!consumeBudget()) return;
                continue;
            }

            int batchSz = Math.max(1, allNeighbours.size() / numWorkers);
            List<Callable<Void>> tasks = new ArrayList<>(numWorkers);
            for (int w = 0; w < numWorkers; w++) {
                final int s = w * batchSz;
                final int e = (w == numWorkers - 1) ? allNeighbours.size() : s + batchSz;
                if (s >= e) continue;
                final List<Vector3i> slice = allNeighbours.subList(s, e);
                tasks.add(() -> {
                    for (Vector3i c : slice) {
                        if (curCount.get() >= minerConfig.blockLimit) break;
                        // markVisited handles both visited-set implementations (never NPEs).
                        if (!markVisited(encodePos(c.x, c.y, c.z))) continue;
                        if (checkCanAddAfterVisited(c)) {
                            curCount.incrementAndGet();
                            positions.offer(c);
                            synchronized (frontierLock) {
                                frontier.add(c);
                            }
                        }
                    }
                    return null;
                });
            }
            try {
                SearchWorkerPool.get()
                    .invokeAll(tasks);
            } catch (InterruptedException e) {
                Thread.currentThread()
                    .interrupt();
                return;
            }
            if (!consumeBudget()) return;
        }
    }

    /**
     * Dual-frontier multi-threaded BFS. Uses ConcurrentLinkedQueue for the next-frontier
     * (lock-free offer from workers) while the current-frontier is batch-polled.
     */
    private void doMultiThreadedSearchDualFrontier() {
        final int numWorkers = Math.max(1, Config.searchWorkerThreads);
        final int FRONTIER_BATCH = 8;

        Queue<Vector3i> currentFrontier = new ConcurrentLinkedQueue<>();
        Queue<Vector3i> nextFrontier = new ConcurrentLinkedQueue<>();
        currentFrontier.add(center);

        while (curCount.get() < minerConfig.blockLimit) {
            // Collect a batch from currentFrontier; rotate if empty
            List<Vector3i> batch = new ArrayList<>(FRONTIER_BATCH);
            for (int i = 0; i < FRONTIER_BATCH && curCount.get() < minerConfig.blockLimit; i++) {
                Vector3i node = currentFrontier.poll();
                if (node == null) break;
                batch.add(node);
            }
            if (batch.isEmpty()) {
                // Rotate
                Queue<Vector3i> tmp = currentFrontier;
                currentFrontier = nextFrontier;
                nextFrontier = tmp;
                if (currentFrontier.isEmpty()) break;
                continue;
            }

            List<Vector3i> allNeighbours = new ArrayList<>(batch.size() * 124);
            for (Vector3i node : batch) {
                collectNeighbours(node, allNeighbours);
            }
            if (allNeighbours.isEmpty()) {
                if (!consumeBudget()) return;
                continue;
            }

            // Capture nextFrontier as effectively-final for the lambda
            final Queue<Vector3i> targetFrontier = nextFrontier;
            int batchSz = Math.max(1, allNeighbours.size() / numWorkers);
            List<Callable<Void>> tasks = new ArrayList<>(numWorkers);
            for (int w = 0; w < numWorkers; w++) {
                final int s = w * batchSz;
                final int e = (w == numWorkers - 1) ? allNeighbours.size() : s + batchSz;
                if (s >= e) continue;
                final List<Vector3i> slice = allNeighbours.subList(s, e);
                tasks.add(() -> {
                    for (Vector3i c : slice) {
                        if (curCount.get() >= minerConfig.blockLimit) break;
                        // markVisited handles both visited-set implementations (never NPEs).
                        if (!markVisited(encodePos(c.x, c.y, c.z))) continue;
                        if (checkCanAddAfterVisited(c)) {
                            curCount.incrementAndGet();
                            positions.offer(c);
                            targetFrontier.offer(c);
                        }
                    }
                    return null;
                });
            }
            try {
                SearchWorkerPool.get()
                    .invokeAll(tasks);
            } catch (InterruptedException e) {
                Thread.currentThread()
                    .interrupt();
                return;
            }
            if (!consumeBudget()) return;
        }
    }

    /**
     * Collects all valid neighbour positions of {@code node} into {@code out},
     * applying radius bounds but <strong>not</strong> visiting or world reads.
     */
    private void collectNeighbours(Vector3i node, List<Vector3i> out) {
        for (int dx = -minerConfig.smallRadius; dx <= minerConfig.smallRadius; dx++) {
            int cx = node.x + dx;
            if (Math.abs(cx - center.x) > minerConfig.bigRadius) continue;
            for (int dy = -minerConfig.smallRadius; dy <= minerConfig.smallRadius; dy++) {
                int cy = node.y + dy;
                if (Math.abs(cy - center.y) > minerConfig.bigRadius) continue;
                for (int dz = -minerConfig.smallRadius; dz <= minerConfig.smallRadius; dz++) {
                    if (dx == 0 && dy == 0 && dz == 0) continue;
                    int cz = node.z + dz;
                    if (Math.abs(cz - center.z) > minerConfig.bigRadius) continue;
                    out.add(new Vector3i(cx, cy, cz));
                }
            }
        }
    }

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
        if (!DeterminingIdentical
            .identical(sampleBlock, sampleBlockMeta, sampleTileEntity, block, blockMeta, pos, player)) return false;
        if (skipHarvestCheck) return true;
        if (player.capabilities.isCreativeMode) return true;
        return block.canHarvestBlock(player, blockMeta);
    }

    private static double distSq(Vector3i a, Vector3i b) {
        double dx = a.x - b.x, dy = a.y - b.y, dz = a.z - b.z;
        return dx * dx + dy * dy + dz * dz;
    }
}
