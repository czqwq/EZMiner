package com.czqwq.EZMiner.chain.planning;

import java.util.List;
import java.util.Queue;

import org.joml.Vector3i;

/**
 * A {@link ChainPlanningTask} that feeds pre-calculated positions on-demand
 * into the operator's work queue without running any background search.
 *
 * <p>
 * <strong>Lazy feed:</strong> instead of dumping the entire pre-calculated
 * list into the queue upfront (which allocates a large backing array inside
 * {@code LinkedBlockingQueue} and prevents early cancellation from being
 * observed), positions are transferred in small per-tick batches via
 * {@link #feedTo(Queue, int)}. This keeps memory pressure low and lets
 * cancellation (via {@link #interrupt()}) take effect within one tick.
 *
 * <p>
 * <strong>Decoupling:</strong> this task has zero knowledge of founders,
 * world access, or mining logic. It is given a plain {@code List<Vector3i>}
 * at construction time — it simply advances a cursor across the list.
 *
 * <p>
 * Used by cached chain sub-modes where the block search has already been
 * performed during the pre-calculation phase.
 */
public class CachedPositionsPlanningTask implements ChainPlanningTask {

    private final List<Vector3i> positions;
    private int cursor;
    private volatile boolean stopped;

    /**
     * @param positions the pre-calculated positions (ownership is shared — caller
     *                  must not modify the list after construction)
     */
    public CachedPositionsPlanningTask(List<Vector3i> positions) {
        this.positions = positions;
    }

    /**
     * No-op in the cached path — positions are fed lazily via {@link #feedTo(Queue, int)}
     * each tick from {@code BaseOperator.operatorTask()}.
     */
    @Override
    public void schedule() {
        // positions are transferred on-demand; nothing to do here
    }

    /**
     * Transfers at most {@code maxCount} positions from the pre-calculated list
     * into {@code queue}. Returns the number of positions actually transferred.
     *
     * @param queue    the operator's work queue
     * @param maxCount maximum positions to transfer this call
     * @return how many positions were actually fed (0 means exhausted or stopped)
     */
    public int feedTo(Queue<Vector3i> queue, int maxCount) {
        int fed = 0;
        while (fed < maxCount && cursor < positions.size() && !stopped) {
            queue.add(positions.get(cursor++));
            fed++;
        }
        return fed;
    }

    /**
     * Returns the total number of positions in the pre-calculated list.
     */
    public int totalSize() {
        return positions.size();
    }

    /**
     * Returns the number of positions that have already been consumed.
     */
    public int consumedCount() {
        return cursor;
    }

    @Override
    public void interrupt() {
        stopped = true;
    }

    @Override
    public boolean isStopped() {
        return stopped || cursor >= positions.size();
    }
}
