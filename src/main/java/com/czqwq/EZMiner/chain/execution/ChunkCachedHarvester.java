package com.czqwq.EZMiner.chain.execution;

import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;

import org.joml.Vector3i;

/**
 * Stateful per-tick-batch chunk-cached block harvester.
 *
 * <p>
 * Maintains a cache of the current chunk and {@link ExtendedBlockStorage} across
 * multiple {@link #harvestNext} calls within a single server tick's batch of
 * chain-mining operations. When positions are already locality-grouped (which BFS
 * results naturally are), this eliminates 2 redundant
 * {@code world.getChunkFromChunkCoords} LongHashMap lookups and one
 * {@code Chunk.getBlockStorageArray} call per block compared to the individual
 * {@link BlockHarvestActionExecutor#execute} path.
 *
 * <p>
 * <strong>Usage pattern:</strong>
 * 
 * <pre>
 * {@code
 * ChunkCachedHarvester harvester = new ChunkCachedHarvester();
 * for each position in this tick's batch:
 *     if (!preCheck(pos)) break;
 *     boolean ok = harvester.harvestNext(pos, player);
 *     if (ok) { count++; markHarvested(); }
 * harvester.flushRemaining();
 * }
 * </pre>
 *
 * <p>
 * On chunk switch, the previous chunk's height map is updated and the chunk is
 * marked dirty. Call {@link #flushRemaining()} after the last position to flush
 * the final chunk.
 *
 * <p>
 * <strong>Hodgepodge & EndlessIDs:</strong> uses
 * {@link ExtendedBlockStorage#func_150818_a} (EndlessIDs-safe) and bypasses
 * {@link World#setBlock} entirely, so no Hodgepodge chunk-loading or tick-index
 * mixins are affected.
 */
public class ChunkCachedHarvester {

    private int lastChunkX = Integer.MIN_VALUE;
    private int lastChunkZ = Integer.MIN_VALUE;
    private int lastY4 = -1;
    private Chunk currentChunk;
    private ExtendedBlockStorage currentEbs;
    private boolean[] touchedColumns;

    /**
     * Harvest a single block at {@code pos} using chunk-cached lookups.
     *
     * <p>
     * Caller is responsible for: tool-can-continue check, VP discovery, exhaustion,
     * and stop-requested flag. This method only handles the block-level harvest
     * operations (tool damage, onBlockHarvested, set-to-air, drops, XP).
     *
     * @param pos    world coordinates of the block to harvest
     * @param player the mining player
     * @return {@code true} if the block was successfully removed
     */
    public boolean harvestNext(Vector3i pos, EntityPlayerMP player) {
        World world = player.worldObj;
        if (world == null) return false;

        int x = pos.x, y = pos.y, z = pos.z;
        int cx = x >> 4, cz = z >> 4, y4 = y >> 4;

        // ── Resolve chunk/EBS (only on chunk/sub-chunk change) ──
        if (cx != lastChunkX || cz != lastChunkZ) {
            flushCurrentChunk();
            currentChunk = world.getChunkFromChunkCoords(cx, cz);
            lastChunkX = cx;
            lastChunkZ = cz;
            lastY4 = -1;
            currentEbs = null;
            touchedColumns = new boolean[256];
        }
        if (currentChunk == null) return false;

        if (y4 != lastY4 || currentEbs == null) {
            currentEbs = ChunkBlockWriteHelper.getEbs(currentChunk, y);
            lastY4 = y4;
        }
        if (currentEbs == null) return false;

        int lx = x & 15, ly = y & 15, lz = z & 15;
        Block block = currentEbs.getBlockByExtId(lx, ly, lz);
        if (block == null || block == Blocks.air) return false;

        int meta = currentEbs.getExtBlockMetadata(lx, ly, lz);

        // TE blocks → vanilla path (does its own chunk lookup)
        if (block.hasTileEntity(meta)) {
            boolean ok = player.theItemInWorldManager.tryHarvestBlock(x, y, z);
            if (ok) {
                touchedColumns[(lx) | (lz << 4)] = true;
            }
            return ok;
        }

        boolean canHarvest = block.canHarvestBlock(player, meta);
        boolean isCreative = player.capabilities.isCreativeMode;

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
        boolean removed = ChunkBlockWriteHelper.writeAirToEbs(currentChunk, currentEbs, x, y, z);

        if (removed) {
            block.onBlockDestroyedByPlayer(world, x, y, z, meta);
            // Notify clients of the block change. Without this the client-side
            // preview renderer still sees the old ore blocks and draws outlines
            // over already-mined air. Sends S23PacketBlockChange to all players
            // watching this chunk (including the mining player).
            world.markBlockForUpdate(x, y, z);
        }

        // ── Drops ──
        if (removed && canHarvest) {
            block.harvestBlock(world, player, x, y, z, meta);
        }

        // ── XP ──
        if (removed) {
            block.dropXpOnBlockBreak(world, x, y, z, 0);
        }

        if (removed) {
            touchedColumns[(lx) | (lz << 4)] = true;
        }
        return removed;
    }

    /**
     * Flush height map update and dirty-mark for the currently cached chunk.
     * Must be called after the last {@link #harvestNext} call in a batch and
     * whenever the chunk changes (called automatically by {@link #harvestNext}
     * on chunk switch).
     */
    public void flushRemaining() {
        flushCurrentChunk();
    }

    private void flushCurrentChunk() {
        if (currentChunk != null && touchedColumns != null) {
            ChunkBlockWriteHelper.updateHeightMap(currentChunk, touchedColumns);
            ChunkBlockWriteHelper.markChunkModified(currentChunk);
        }
    }
}
