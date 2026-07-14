package com.czqwq.EZMiner.mixin.early;

import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.server.management.ItemInWorldManager;
import net.minecraft.world.World;
import net.minecraftforge.event.world.BlockEvent;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

import com.czqwq.EZMiner.chain.execution.XPDropHandler;
import com.czqwq.EZMiner.mixin.interfaces.IEZMinerItemInWorldManager;

/**
 * Adds a fast-harvest path to {@code ItemInWorldManager} that skips sound/particle
 * packets, excess world queries, and neighbor notifications during chain mining.
 * Callers fire the Forge {@code BreakEvent} first and pass it into this method so
 * addon semantics and event-provided XP are retained.
 * <p>
 * Hodgepodge compatibility: this mixin targets {@code ItemInWorldManager}, which
 * has no Hodgepodge mixins. Callers invoke {@code ForgeHooks.onBlockBreakEvent}
 * before entering this method, so Hodgepodge hooks and other Forge listeners still
 * observe the canonical break event. Tile-entity blocks remain on the vanilla path.
 */
@Mixin(ItemInWorldManager.class)
public abstract class MixinItemInWorldManager implements IEZMinerItemInWorldManager {

    @Shadow
    public World theWorld;

    @Shadow
    public EntityPlayerMP thisPlayerMP;

    @Shadow
    public abstract boolean isCreative();

    /**
     * Fast block harvest after the caller has fired the per-block Forge event.
     * Omits vanilla sound effects and neighbor notifications.
     *
     * @see IEZMinerItemInWorldManager#ezminer$tryHarvestBlockFast(int, int, int, boolean, BlockEvent.BreakEvent)
     */
    @Unique
    @Override
    public boolean ezminer$tryHarvestBlockFast(int x, int y, int z, boolean canHarvest,
        BlockEvent.BreakEvent breakEvent) {
        // ── Tool damage (only in survival) ──
        if (!isCreative()) {
            ItemStack stack = thisPlayerMP.getCurrentEquippedItem();
            if (stack != null) {
                // notify the item that it was used to break a block
                stack.func_150999_a(theWorld, theWorld.getBlock(x, y, z), x, y, z, thisPlayerMP);
                if (stack.stackSize == 0) {
                    thisPlayerMP.destroyCurrentEquippedItem();
                }
            }
        }

        // ── Harvest callbacks ──
        Block block = theWorld.getBlock(x, y, z);
        int meta = theWorld.getBlockMetadata(x, y, z);
        block.onBlockHarvested(theWorld, x, y, z, meta, thisPlayerMP);

        // ── Remove the block ──
        // flag=2: send client update (flag & 2) but skip neighbor notification (flag & 1).
        // This avoids 6 onNeighborBlockChange calls per block. For ore veins embedded
        // in solid stone, these notifications are wasted work. TE blocks must use the
        // vanilla tryHarvestBlock path instead.
        boolean removed = theWorld.setBlock(x, y, z, Blocks.air, 0, 2);

        if (removed) {
            block.onBlockDestroyedByPlayer(theWorld, x, y, z, meta);
        }

        // ── Drops ──
        if (removed && canHarvest) {
            block.harvestBlock(theWorld, thisPlayerMP, x, y, z, meta);
        }

        // ── XP ──
        if (removed) {
            XPDropHandler.handlePreComputedXP(theWorld, block, x, y, z, breakEvent.getExpToDrop(), thisPlayerMP);
        }

        return removed;
    }
}
