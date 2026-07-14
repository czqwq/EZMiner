package com.czqwq.EZMiner.chain.execution;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;
import net.minecraftforge.common.ForgeHooks;
import net.minecraftforge.event.world.BlockEvent;

/** Runs the Forge pre-break sequence skipped by EZMiner's fast harvest paths. */
final class FastHarvestPrecheck {

    private FastHarvestPrecheck() {}

    /**
     * Fires the canonical break event, then invokes the held item's pre-break callback.
     *
     * @return the non-canceled break event, or {@code null} when the break must stop
     */
    static BlockEvent.BreakEvent run(World world, EntityPlayerMP player, int x, int y, int z) {
        BlockEvent.BreakEvent event = ForgeHooks
            .onBlockBreakEvent(world, player.theItemInWorldManager.getGameType(), player, x, y, z);
        if (event.isCanceled()) return null;

        ItemStack stack = player.getCurrentEquippedItem();
        if (stack != null && stack.getItem()
            .onBlockStartBreak(stack, x, y, z, player)) {
            return null;
        }
        return event;
    }
}
