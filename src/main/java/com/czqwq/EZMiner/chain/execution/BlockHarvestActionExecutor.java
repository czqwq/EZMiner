package com.czqwq.EZMiner.chain.execution;

import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.world.World;

import org.joml.Vector3i;

import com.czqwq.EZMiner.mixin.interfaces.IEZMinerItemInWorldManager;

/**
 * Main-thread world mutation executor.
 * <p>
 * Uses the vanilla {@code ItemInWorldManager.tryHarvestBlock} path for blocks
 * that have a {@link net.minecraft.tileentity.TileEntity} (to ensure proper TE
 * cleanup and {@code BreakEvent} firing), and a fast-harvest mixin path for all
 * other blocks — skipping per-block event firing, sound packets, and neighbor
 * notifications.
 */
public class BlockHarvestActionExecutor implements ChainActionExecutor {

    @Override
    public boolean execute(Vector3i pos, EntityPlayerMP player) {
        final int x = pos.x, y = pos.y, z = pos.z;
        World world = player.worldObj;
        if (world == null) return false;

        Block block = world.getBlock(x, y, z);
        if (block == null || block.isAir(world, x, y, z)) return false;

        int meta = world.getBlockMetadata(x, y, z);

        // Blocks with tile entities must go through the vanilla path so that
        // BreakEvent fires, TE cleanup runs, and neighbor notifications reach
        // adjacent redstone/logic blocks.
        if (block.hasTileEntity(meta)) {
            return player.theItemInWorldManager.tryHarvestBlock(x, y, z);
        }

        // Fast path: skip BreakEvent, playAuxSFX, excess getBlock calls, and
        // neighbor notifications (setBlock flag=2 instead of flag=3).
        IEZMinerItemInWorldManager fastMgr = (IEZMinerItemInWorldManager) player.theItemInWorldManager;
        boolean canHarvest = block.canHarvestBlock(player, meta);
        return fastMgr.ezminer$tryHarvestBlockFast(x, y, z, canHarvest, null);
    }
}
