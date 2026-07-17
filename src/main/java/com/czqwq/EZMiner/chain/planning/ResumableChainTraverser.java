package com.czqwq.EZMiner.chain.planning;

import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;

import org.joml.Vector3i;

import com.czqwq.EZMiner.Config;
import com.czqwq.EZMiner.core.MinerConfig;
import com.czqwq.EZMiner.core.founder.BasePositionFounder;
import com.czqwq.EZMiner.core.founder.DeterminingIdentical;

/**
 * Resumable state-machine BFS chain traverser — the first implementation of
 * {@link ChainTraverser} in the new chain subsystem.
 *
 * <p>
 * Unlike the legacy {@code ChainPositionFounder} (which runs as a background
 * daemon thread), this traverser is designed to be driven step-by-step from
 * the server thread. Each call to {@link #traverse} processes up to
 * {@link Config#searchBudgetPerYield} positions and then returns, saving its
 * exact state so the next call continues where it left off.
 * </p>
 *
 * <p>
 * State machine phases:
 * <ol>
 * <li>{@code PROCESS_FRONTIER} — poll from currentFrontier, test against
 * the block matcher, enqueue accepted positions into output.</li>
 * <li>{@code GENERATE_NEIGHBORS} — for each accepted node, expand 6-axis
 * neighbors, check visited set and radius bounds, add to
 * nextFrontier.</li>
 * <li>{@code ROTATE_FRONTIER} — when currentFrontier is drained, swap it
 * with nextFrontier to begin the next BFS wave.</li>
 * <li>{@code DONE} — BFS exhausted.</li>
 * </ol>
 * </p>
 *
 * <p>
 * Enabled via {@link Config#useResumableTraverser} (default false).
 * When disabled, the legacy {@code ChainPositionFounder} thread-based BFS
 * is used instead.
 * </p>
 */
public class ResumableChainTraverser implements ChainTraverser {

    private enum Phase {
        PROCESS_FRONTIER,
        GENERATE_NEIGHBORS,
        ROTATE_FRONTIER,
        DONE
    }

    // ── Resumable state ──────────────────────────────────────────────────────
    private Phase phase = Phase.DONE;
    private Queue<Vector3i> currentFrontier;
    private Queue<Vector3i> nextFrontier;
    private final Set<Long> visited;
    private Vector3i currentNode;
    private int neighborDx, neighborDy, neighborDz;
    private int acceptedCount;
    private int budgetRemaining;

    // ── Cached from last traverse() call ─────────────────────────────────────
    private Vector3i origin;
    private Queue<Vector3i> output;
    private EntityPlayer player;
    private MinerConfig config;
    private Block sampleBlock;
    private int sampleBlockMeta;
    private int cachedPlayerFloorX, cachedPlayerFloorY, cachedPlayerFloorZ;

    // ── Constants ────────────────────────────────────────────────────────────
    /** 6-axis neighbor offsets. */
    private static final int[][] NEIGHBOR_OFFSETS = { { 1, 0, 0 }, { -1, 0, 0 }, { 0, 1, 0 }, { 0, -1, 0 }, { 0, 0, 1 },
        { 0, 0, -1 } };

    public ResumableChainTraverser() {
        this.visited = Config.usePrimitiveVisitedSet ? null : ConcurrentHashMap.newKeySet(256);
    }

    /**
     * Runs (or resumes) the BFS traversal. Each call processes up to
     * {@code config.smallRadius} &times; 6 positions or the configured budget,
     * then returns. The caller must call this repeatedly in a loop until
     * the output queue stops growing.
     *
     * @param origin the center block position
     * @param output the queue to receive discovered block positions
     * @param player the mining player
     * @param config the per-player miner config
     */
    @Override
    public void traverse(Vector3i orig, Queue<Vector3i> out, EntityPlayer pl, MinerConfig cfg) {
        // ── Lazy init on first call or reset on new origin ──
        if (phase == Phase.DONE || !orig.equals(origin)) {
            reset(orig, out, pl, cfg);
        }
        this.output = out;
        this.player = pl;
        this.config = cfg;
        this.budgetRemaining = Config.searchBudgetPerYield > 0 ? Config.searchBudgetPerYield : Integer.MAX_VALUE;

        int added = 0;
        int maxAdds = Math.min(cfg.blockLimit - acceptedCount, cfg.blockLimit);

        while (added < maxAdds && budgetRemaining > 0 && phase != Phase.DONE) {
            switch (phase) {
                case PROCESS_FRONTIER:
                    added += processFrontier(maxAdds - added);
                    break;
                case GENERATE_NEIGHBORS:
                    generateNeighbors();
                    break;
                case ROTATE_FRONTIER:
                    rotateFrontier();
                    break;
                default:
                    break;
            }
        }
    }

    /** Returns true if the BFS has exhausted all reachable blocks. */
    public boolean isDone() {
        return phase == Phase.DONE;
    }

    /** Returns the total number of positions accepted so far. */
    public int getAcceptedCount() {
        return acceptedCount;
    }

    /** Resets all state for a new traversal from the given origin. */
    public void reset(Vector3i orig, Queue<Vector3i> out, EntityPlayer pl, MinerConfig cfg) {
        this.origin = orig;
        this.output = out;
        this.player = pl;
        this.config = cfg;
        this.currentFrontier = new ConcurrentLinkedQueue<>();
        this.nextFrontier = new ConcurrentLinkedQueue<>();
        if (visited instanceof ConcurrentHashMap.KeySetView) {
            ((ConcurrentHashMap.KeySetView<Long, Boolean>) visited).clear();
        }
        this.currentFrontier.add(orig);
        this.phase = Phase.PROCESS_FRONTIER;
        this.currentNode = null;
        this.neighborDx = this.neighborDy = this.neighborDz = 0;
        this.acceptedCount = 0;
        this.budgetRemaining = 0;

        // Cache player/sample info
        if (pl != null && pl.worldObj != null) {
            this.sampleBlock = pl.worldObj.getBlock(orig.x, orig.y, orig.z);
            this.sampleBlockMeta = pl.worldObj.getBlockMetadata(orig.x, orig.y, orig.z);
            this.cachedPlayerFloorX = (int) Math.floor(pl.posX);
            this.cachedPlayerFloorY = (int) Math.floor(pl.posY);
            this.cachedPlayerFloorZ = (int) Math.floor(pl.posZ);
        }

        // Add origin to output
        long key = BasePositionFounder.encodePos(orig.x, orig.y, orig.z);
        if (visited != null) {
            visited.add(key);
        }
        out.add(orig);
        acceptedCount = 1;
    }

    // ── Phase implementations ────────────────────────────────────────────────

    private int processFrontier(int maxAdds) {
        int added = 0;
        while (added < maxAdds && budgetRemaining > 0) {
            currentNode = currentFrontier.poll();
            if (currentNode == null) {
                phase = Phase.ROTATE_FRONTIER;
                return added;
            }
            // Check if this node is a valid target (already visited-checked before
            // adding to frontier, but re-verify for safety)
            if (canAccept(currentNode)) {
                output.add(currentNode);
                added++;
                acceptedCount++;
            }
            phase = Phase.GENERATE_NEIGHBORS;
            neighborDx = 0;
            neighborDy = 0;
            neighborDz = -1; // will be incremented to 0 on first iteration
            // Only process one node per phase transition to keep budget granular
            generateNeighbors();
            if (budgetRemaining <= 0) return added;
            phase = Phase.PROCESS_FRONTIER;
            budgetRemaining--;
        }
        return added;
    }

    private void generateNeighbors() {
        if (currentNode == null) {
            phase = Phase.PROCESS_FRONTIER;
            return;
        }
        int radius = config.smallRadius;
        while (neighborDx <= radius) {
            while (neighborDy <= radius) {
                neighborDz++;
                if (neighborDz > radius) {
                    neighborDz = -radius;
                    neighborDy++;
                    continue;
                }
                if (neighborDx == 0 && neighborDy == 0 && neighborDz == 0) continue;

                int cx = currentNode.x + neighborDx;
                int cy = currentNode.y + neighborDy;
                int cz = currentNode.z + neighborDz;
                budgetRemaining--;

                // Radius bound check
                if (Math.abs(cx - origin.x) > config.bigRadius || Math.abs(cy - origin.y) > config.bigRadius
                    || Math.abs(cz - origin.z) > config.bigRadius) {
                    if (budgetRemaining <= 0) return;
                    continue;
                }

                // Visited check
                long key = BasePositionFounder.encodePos(cx, cy, cz);
                if (visited != null) {
                    if (!visited.add(key)) {
                        if (budgetRemaining <= 0) return;
                        continue;
                    }
                }

                // Can-accept check
                Vector3i candidate = new Vector3i(cx, cy, cz);
                if (canAccept(candidate)) {
                    nextFrontier.add(candidate);
                }

                if (budgetRemaining <= 0) return;
            }
            neighborDy = -radius;
            neighborDx++;
            if (neighborDx > radius) {
                // Finished all neighbors for this node
                phase = Phase.PROCESS_FRONTIER;
                return;
            }
            if (budgetRemaining <= 0) return;
        }
    }

    private void rotateFrontier() {
        Queue<Vector3i> tmp = currentFrontier;
        currentFrontier = nextFrontier;
        nextFrontier = tmp;
        if (currentFrontier.isEmpty()) {
            phase = Phase.DONE;
        } else {
            phase = Phase.PROCESS_FRONTIER;
        }
        budgetRemaining--;
    }

    // ── Block matching ───────────────────────────────────────────────────────

    private boolean canAccept(Vector3i pos) {
        if (player == null || player.worldObj == null) return false;
        if (!player.worldObj.blockExists(pos.x, pos.y, pos.z)) return false;
        Block block = player.worldObj.getBlock(pos.x, pos.y, pos.z);
        if (block.equals(Blocks.air) || block.getMaterial()
            .isLiquid() || block.equals(Blocks.bedrock)) return false;
        // Skip player's feet position
        if (pos.x == cachedPlayerFloorX && pos.y == (cachedPlayerFloorY - 1) && pos.z == cachedPlayerFloorZ)
            return false;
        // Must be same block type as origin
        int meta = player.worldObj.getBlockMetadata(pos.x, pos.y, pos.z);
        if (!DeterminingIdentical.identical(sampleBlock, sampleBlockMeta, null, block, meta, pos, player)) return false;
        // Harvest check
        if (player.capabilities.isCreativeMode) return true;
        return block.canHarvestBlock(player, meta);
    }
}
