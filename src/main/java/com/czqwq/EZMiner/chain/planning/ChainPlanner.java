package com.czqwq.EZMiner.chain.planning;

import java.util.Queue;

import net.minecraft.entity.player.EntityPlayer;

import org.joml.Vector3i;

import com.czqwq.EZMiner.core.MinerConfig;

/**
 * Planner facade. Produces candidate positions without mutating world state.
 */
public class ChainPlanner {

    private final ChainPlanningStrategy strategy;

    public ChainPlanner(ChainPlanningStrategy strategy) {
        this.strategy = strategy;
    }

    public void plan(Vector3i origin, Queue<Vector3i> output, EntityPlayer player, MinerConfig config) {
        strategy.plan(origin, output, player, config);
    }
}
