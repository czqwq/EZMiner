package com.czqwq.EZMiner.chain.execution;

import net.minecraft.entity.player.EntityPlayerMP;

import org.joml.Vector3i;

/**
 * Main-thread world mutation executor.
 */
public class BlockHarvestActionExecutor implements ChainActionExecutor {

    @Override
    public boolean execute(Vector3i pos, EntityPlayerMP player) {
        return player.theItemInWorldManager.tryHarvestBlock(pos.x, pos.y, pos.z);
    }
}
