package com.czqwq.EZMiner.chain.planning;

import java.util.Queue;

import net.minecraft.entity.player.EntityPlayer;

import org.joml.Vector3i;

import com.czqwq.EZMiner.core.MinerConfig;

public interface ChainPlanningStrategy {

    void plan(Vector3i origin, Queue<Vector3i> output, EntityPlayer player, MinerConfig config);
}
