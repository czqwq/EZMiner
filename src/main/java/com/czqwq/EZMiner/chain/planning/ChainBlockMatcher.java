package com.czqwq.EZMiner.chain.planning;

import net.minecraft.entity.player.EntityPlayer;

import org.joml.Vector3i;

public interface ChainBlockMatcher {

    boolean matches(Vector3i samplePos, Vector3i targetPos, EntityPlayer player);
}
