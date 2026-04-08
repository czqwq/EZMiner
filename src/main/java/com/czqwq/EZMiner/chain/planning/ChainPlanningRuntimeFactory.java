package com.czqwq.EZMiner.chain.planning;

/**
 * Runtime factory placeholder for mode-specific planner assembly.
 */
public class ChainPlanningRuntimeFactory {

    public ChainPlanner create(ChainPlanningStrategy strategy) {
        return new ChainPlanner(strategy);
    }
}
