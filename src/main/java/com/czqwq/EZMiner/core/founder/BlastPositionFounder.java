package com.czqwq.EZMiner.core.founder;

import java.util.concurrent.LinkedBlockingQueue;

import net.minecraft.entity.player.EntityPlayer;

import org.joml.Vector3i;

import com.czqwq.EZMiner.core.MinerConfig;

/** Blast mode – no filter, breaks all harvestable blocks in radius. */
public class BlastPositionFounder extends BasePositionFounder {

    public BlastPositionFounder(Vector3i center, LinkedBlockingQueue<Vector3i> results, EntityPlayer player,
        MinerConfig minerConfig) {
        super(center, results, player, minerConfig);
        setName("EZMiner-BlastAll");
    }
    // Inherits run1() and checkCanAdd() from BasePositionFounder unchanged.
}
