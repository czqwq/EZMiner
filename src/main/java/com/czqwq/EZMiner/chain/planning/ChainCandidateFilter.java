package com.czqwq.EZMiner.chain.planning;

import net.minecraft.entity.player.EntityPlayer;

import org.joml.Vector3i;

public interface ChainCandidateFilter {

    boolean allow(Vector3i pos, EntityPlayer player);
}
