package com.czqwq.EZMiner.mixin.interfaces;

import net.minecraftforge.event.world.BlockEvent;

/**
 * Accessor interface for the fast-harvest method added to
 * {@code ItemInWorldManager} via {@code MixinItemInWorldManager}.
 * <p>
 * Callers should cast {@code player.theItemInWorldManager} to this interface
 * to invoke the fast path without reflection.
 */
public interface IEZMinerItemInWorldManager {

    /**
     * Harvest a block with reduced overhead compared to
     * {@link net.minecraft.server.management.ItemInWorldManager#tryHarvestBlock(int, int, int)}.
     * <p>
     * <strong>Differences from the vanilla path:</strong>
     * <ul>
     * <li>The caller pre-fires {@code BlockEvent.BreakEvent}; this method does not fire it again.</li>
     * <li>No {@code playAuxSFX} sound/particle packet per block.</li>
     * <li>After the caller's event gate, this body uses only 2 world queries
     * (getBlock + getBlockMetadata) instead of the full vanilla sequence.</li>
     * <li>{@code setBlock(x,y,z, air, 0, 2)} with flag=2 — client notification only,
     * skipping 6 neighbor-change notifications and light recalculation.</li>
     * </ul>
     * <p>
     * <strong>Caller is responsible for:</strong>
     * <ul>
     * <li>Firing the canonical Forge break hook and aborting if it is canceled.</li>
     * <li>Invoking the held item's {@code onBlockStartBreak} callback and aborting
     * if the item handles the break.</li>
     * <li>Only calling this for blocks that do <em>not</em> have a
     * {@link net.minecraft.tileentity.TileEntity} — use the vanilla
     * {@code tryHarvestBlock} path for TE blocks.</li>
     * <li>Sending a chunk-resync or multi-block-change packet after the batch
     * (instead of relying on per-block {@code S23PacketBlockChange}).</li>
     * <li>Ensuring the player, world, and tool are in a valid state before calling.</li>
     * </ul>
     *
     * @param x          block X coordinate
     * @param y          block Y coordinate
     * @param z          block Z coordinate
     * @param canHarvest whether the player's tool can harvest this block
     *                   (call {@code block.canHarvestBlock(player, meta)} before)
     * @param breakEvent the non-canceled {@code BreakEvent} fired for this exact
     *                   block, used for its event-modified XP value
     * @return {@code true} if the block was successfully removed
     */
    boolean ezminer$tryHarvestBlockFast(int x, int y, int z, boolean canHarvest, BlockEvent.BreakEvent breakEvent);
}
