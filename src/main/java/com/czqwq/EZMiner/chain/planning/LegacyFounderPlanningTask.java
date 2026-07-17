package com.czqwq.EZMiner.chain.planning;

import java.util.Objects;

import com.czqwq.EZMiner.EZMiner;
import com.czqwq.EZMiner.core.founder.BasePositionFounder;

/**
 * Planning-task adapter that encapsulates legacy founder search threads behind
 * chain-planning runtime abstractions.
 */
public class LegacyFounderPlanningTask implements ChainPlanningTask {

    private final BasePositionFounder founder;

    public LegacyFounderPlanningTask(BasePositionFounder founder) {
        this.founder = Objects.requireNonNull(founder, "founder");
    }

    @Override
    public void schedule() {
        EZMiner.parallelTick.addPreServerTickTask(founder);
    }

    @Override
    public void interrupt() {
        founder.interrupt();
    }

    @Override
    public boolean isStopped() {
        return founder.stopped.get();
    }
}
