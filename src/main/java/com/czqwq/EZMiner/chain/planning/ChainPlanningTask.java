package com.czqwq.EZMiner.chain.planning;

/**
 * Runtime planning task handle.
 *
 * <p>
 * Planning tasks are compute-only producers: they enqueue candidate positions
 * and never mutate world state.
 */
public interface ChainPlanningTask {

    void schedule();

    void interrupt();

    boolean isStopped();
}
