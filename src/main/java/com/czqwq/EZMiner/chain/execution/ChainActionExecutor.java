package com.czqwq.EZMiner.chain.execution;

import net.minecraft.entity.player.EntityPlayerMP;

import org.joml.Vector3i;

public interface ChainActionExecutor {

    boolean execute(Vector3i pos, EntityPlayerMP player);
}
