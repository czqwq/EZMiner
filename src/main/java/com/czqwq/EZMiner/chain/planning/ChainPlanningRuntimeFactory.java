package com.czqwq.EZMiner.chain.planning;

import java.util.concurrent.LinkedBlockingQueue;

import net.minecraft.entity.player.EntityPlayer;

import org.joml.Vector3i;

import com.czqwq.EZMiner.core.MinerConfig;
import com.czqwq.EZMiner.core.MinerModeState;
import com.czqwq.EZMiner.core.founder.BasePositionFounder;

/**
 * Runtime factory placeholder for mode-specific planner assembly.
 */
public class ChainPlanningRuntimeFactory {

    private final LegacyFounderPlanningFactory legacyFounderPlanningFactory = new LegacyFounderPlanningFactory();

    public ChainPlanner create(ChainPlanningStrategy strategy) {
        return new ChainPlanner(strategy);
    }

    public BasePositionFounder createLegacyFounder(MinerModeState modeState, Vector3i center,
        LinkedBlockingQueue<Vector3i> results, EntityPlayer player, MinerConfig config) {
        return legacyFounderPlanningFactory.createFounder(modeState, center, results, player, config);
    }
}
