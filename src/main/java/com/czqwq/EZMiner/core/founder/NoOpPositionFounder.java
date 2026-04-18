package com.czqwq.EZMiner.core.founder;

import java.util.concurrent.LinkedBlockingQueue;

import net.minecraft.entity.player.EntityPlayer;

import org.joml.Vector3i;

import com.czqwq.EZMiner.core.MinerConfig;

/**
 * No-op founder used by modes that do not perform block collection.
 */
public class NoOpPositionFounder extends BasePositionFounder {

    public NoOpPositionFounder(Vector3i center, LinkedBlockingQueue<Vector3i> results, EntityPlayer player,
        MinerConfig minerConfig) {
        super(center, results, player, minerConfig);
        positions.clear();
        foundedPositions.clear();
        curCount = 0;
    }

    @Override
    public void run1() {}
}
