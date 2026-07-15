package com.czqwq.EZMiner.chain.execution;

import java.util.List;
import java.util.Map;

import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;
import net.minecraftforge.event.world.BlockEvent;

import org.joml.Vector3i;

import com.czqwq.EZMiner.compat.TinkersConstructLevelingBridge;
import com.czqwq.EZMiner.mixin.interfaces.IEZMinerItemInWorldManager;

/**
 * Main-thread world mutation executor.
 *
 * <p>
 * Two code paths:
 * <ol>
 * <li><b>TE blocks</b> — vanilla {@code ItemInWorldManager.tryHarvestBlock} for
 * proper TE cleanup, BreakEvent, and neighbor notifications.</li>
 * <li><b>Non-TE blocks (fast path)</b> — chunk-cached batch execution that resolves
 * chunk and EBS references once per chunk, then calls
 * {@code ExtendedBlockStorage.func_150818_a} directly to set blocks to air,
 * bypassing per-block chunk lookups, light updates, and individual
 * {@code S23PacketBlockChange} packets.</li>
 * </ol>
 *
 * <p>
 * The fast path groups positions by chunk, processes all non-TE blocks in each
 * chunk with a single chunk reference, then updates height maps and marks chunks
 * dirty once per chunk. This eliminates 2 redundant {@code getChunkFromChunkCoords}
 * calls per block compared to the individual-execute path.
 *
 * @see ChunkBlockWriteHelper
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

        // Fast path: skip playAuxSFX, excess getBlock calls, and neighbor
        // notifications (setBlock flag=2 instead of flag=3). The per-block
        // BreakEvent fires only when Config.fireBreakEvent is enabled.
        BlockEvent.BreakEvent event = ChainBreakEventHelper.fireIfEnabled(world, player, x, y, z);
        if (event != null && event.isCanceled()) return false;

        IEZMinerItemInWorldManager fastMgr = (IEZMinerItemInWorldManager) player.theItemInWorldManager;
        boolean canHarvest = block.canHarvestBlock(player, meta);
        return fastMgr.ezminer$tryHarvestBlockFast(x, y, z, canHarvest, event);
    }

    /**
     * Batch-execute block harvests with chunk-cached lookups.
     *
     * <p>
     * Groups positions by chunk, then for each chunk processes all non-TE blocks
     * using pre-resolved chunk and EBS references. TE blocks still go through the
     * vanilla path. After all blocks in a chunk are processed, height maps are
     * updated and the chunk is marked dirty.
     *
     * @param positions block positions to harvest (non-empty, already validated)
     * @param player    the mining player
     * @return number of blocks successfully harvested
     */
    public int executeBatch(List<Vector3i> positions, EntityPlayerMP player) {
        World world = player.worldObj;
        if (world == null || positions.isEmpty()) return 0;

        boolean isCreative = player.capabilities.isCreativeMode;
        int harvested = 0;

        // Sort positions so we only switch chunks when the chunk actually changes
        // (positions from BFS are often already locality-grouped).
        Map<Long, ChunkBlockWriteHelper.ChunkBatch> batches = ChunkBlockWriteHelper.groupByChunk(positions, world);

        for (ChunkBlockWriteHelper.ChunkBatch batch : batches.values()) {
            Chunk chunk = batch.chunk;

            for (Vector3i pos : batch.positions) {
                int x = pos.x, y = pos.y, z = pos.z;
                int lx = x & 15, ly = y & 15, lz = z & 15;

                ExtendedBlockStorage ebs = ChunkBlockWriteHelper.getEbs(chunk, y);
                if (ebs == null) continue;

                Block block = ebs.getBlockByExtId(lx, ly, lz);
                if (block == null || block == Blocks.air) continue;

                int meta = ebs.getExtBlockMetadata(lx, ly, lz);

                // TE blocks must use vanilla path
                if (block.hasTileEntity(meta)) {
                    if (player.theItemInWorldManager.tryHarvestBlock(x, y, z)) {
                        harvested++;
                    }
                    continue;
                }

                // ── Optional per-block Forge BreakEvent (Config.fireBreakEvent) ──
                BlockEvent.BreakEvent breakEvent = ChainBreakEventHelper.fireIfEnabled(world, player, x, y, z);
                if (breakEvent != null && breakEvent.isCanceled()) continue;

                // ── TiC compat: fire ActiveToolMod.beforeBlockBreak (IguanaTweaks tool
                // XP, autosmelt, …). true = a hook consumed the block itself — mirror
                // vanilla and skip our own harvest steps. ──
                if (TinkersConstructLevelingBridge.fireBeforeBlockBreak(player, x, y, z)) {
                    harvested++;
                    continue;
                }

                boolean canHarvest = block.canHarvestBlock(player, meta);

                // ── Tool damage (survival only) ──
                if (!isCreative) {
                    ItemStack stack = player.getCurrentEquippedItem();
                    if (stack != null) {
                        stack.func_150999_a(world, block, x, y, z, player);
                        if (stack.stackSize == 0) {
                            player.destroyCurrentEquippedItem();
                        }
                    }
                }

                // ── Harvest callbacks ──
                block.onBlockHarvested(world, x, y, z, meta, player);

                // ── Direct EBS write to air ──
                // Uses func_150818_a which is EndlessIDs-safe (they @Overwrite it
                // to handle extended block IDs correctly).
                boolean removed = ChunkBlockWriteHelper.writeAirToEbs(chunk, ebs, x, y, z);

                if (removed) {
                    block.onBlockDestroyedByPlayer(world, x, y, z, meta);
                    // Notify clients so preview outlines disappear for mined blocks
                    world.markBlockForUpdate(x, y, z);
                }

                // ── Drops ──
                if (removed && canHarvest) {
                    block.harvestBlock(world, player, x, y, z, meta);
                }

                // ── XP ──
                if (removed) {
                    if (breakEvent != null) {
                        XPDropHandler.handlePreComputedXP(world, block, x, y, z, breakEvent.getExpToDrop(), player);
                    } else {
                        XPDropHandler.handleBlockXP(world, block, meta, x, y, z, player);
                    }
                }

                if (removed) {
                    harvested++;
                }
            }

            // ── Post-chunk batch: height maps + dirty flag ──
            ChunkBlockWriteHelper.updateHeightMap(chunk, batch.touchedColumns);
            ChunkBlockWriteHelper.markChunkModified(chunk);
        }

        return harvested;
    }

    /**
     * Same as {@link #execute(Vector3i, EntityPlayerMP)} but with pre-resolved
     * block and metadata to avoid redundant world queries. The caller must ensure
     * that {@code block} and {@code meta} match the world at (x, y, z).
     */
    public boolean executeWithPreResolved(Vector3i pos, EntityPlayerMP player, Block block, int meta) {
        final int x = pos.x, y = pos.y, z = pos.z;
        World world = player.worldObj;
        if (world == null || block == null) return false;

        if (block.hasTileEntity(meta)) {
            return player.theItemInWorldManager.tryHarvestBlock(x, y, z);
        }

        IEZMinerItemInWorldManager fastMgr = (IEZMinerItemInWorldManager) player.theItemInWorldManager;
        boolean canHarvest = block.canHarvestBlock(player, meta);

        BlockEvent.BreakEvent event = ChainBreakEventHelper.fireIfEnabled(world, player, x, y, z);
        if (event != null && event.isCanceled()) return false;

        return fastMgr.ezminer$tryHarvestBlockFast(x, y, z, canHarvest, event);
    }
}
