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

    /**
     * Sets the generation-gated event bus for founder→operator decoupling.
     * Default implementation is a no-op; override in tasks that wrap a founder.
     */
    default void setEventBus(SearchEventBus bus, int generation) {
        // no-op for tasks without a founder
    }
}
