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

import com.czqwq.EZMiner.compat.GT5ToolDurabilityBridge;
import com.czqwq.EZMiner.compat.NaturaSaguaroCompat;
import com.czqwq.EZMiner.compat.RemovedByPlayerBridge;
import com.czqwq.EZMiner.compat.ShearsHarvestBridge;
import com.czqwq.EZMiner.compat.TinkersConstructLevelingBridge;
import com.czqwq.EZMiner.compat.WitcheryVampireBridge;
import com.czqwq.EZMiner.core.founder.DeterminingIdentical;
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
        return execute(pos, player, null);
    }

    /**
     * Per-block fast harvest with an optional neighbour-notification sink.
     *
     * @param removedSink when non-null, successfully removed non-TE blocks are
     *                    appended (see {@link ChunkCachedHarvester#harvestNext}).
     *                    {@code null} preserves the zero-overhead fast path.
     */
    public boolean execute(Vector3i pos, EntityPlayerMP player, List<ChunkBlockWriteHelper.RemovedBlock> removedSink) {
        final int x = pos.x, y = pos.y, z = pos.z;
        World world = player.worldObj;
        if (world == null) return false;

        Block block = world.getBlock(x, y, z);
        if (block == null || block.isAir(world, x, y, z)) return false;
        if (DeterminingIdentical.isUnbreakable(player, block, x, y, z)) return false;

        // ── Protection gate, ABOVE the TE/override early returns ──
        // Placed here deliberately: it must also cover the two vanilla escape branches below
        // (TE carriers and blocks that override removedByPlayer), and vanilla's
        // ItemInWorldManager.tryHarvestBlock does NOT consult canMineBlock — vanilla only checks it
        // on the dig packet, so for an automated batch removal EZMiner's check is the only gate.
        if (!world.canMineBlock(player, x, y, z)) return false;

        int meta = world.getBlockMetadata(x, y, z);

        // Blocks with tile entities must go through the vanilla path so that
        // BreakEvent fires, TE cleanup runs, and neighbor notifications reach
        // adjacent redstone/logic blocks. GT ore containers that carry a companion
        // TileEntity (legacy BlockOresAbstract / BlockOresAbstractLegacy) must also
        // stay on the vanilla path so breakBlock/removeTileEntity always run — never
        // reducing them to a stale "air + leftover metadata/TE" which renders as
        // "name.0".
        if (block.hasTileEntity(meta) || DeterminingIdentical.isGTTileEntityCarrier(block)) {
            return player.theItemInWorldManager.tryHarvestBlock(x, y, z);
        }

        // Fast path: skip playAuxSFX, excess getBlock calls, and neighbor
        // notifications (setBlock flag=2 instead of flag=3).
        //
        // No BreakEvent is pre-fired here. The mixin's own protection gate
        // (ChainBreakEventHelper.canBreakAt, called at the top of
        // ezminer$tryHarvestBlockFast) fires it when Config.fireBreakEvent is on or a
        // protection mod needs it, and the mixin computes XP itself when no event is handed in.
        // Pre-firing here as well produced TWO BreakEvents per block (the old code fired one and
        // passed it in, and the mixin fired a second inside canBreakAt).
        IEZMinerItemInWorldManager fastMgr = (IEZMinerItemInWorldManager) player.theItemInWorldManager;
        boolean canHarvest = block.canHarvestBlock(player, meta)
            || WitcheryVampireBridge.canHarvestWithBareHands(player);
        boolean removed = fastMgr.ezminer$tryHarvestBlockFast(x, y, z, canHarvest, null);
        if (removed) {
            // Tree felling: flag adjacent leaves for vanilla decay (O(1), wood only).
            ChunkBlockWriteHelper.flagNeighbouringLeavesForDecay(world, x, y, z, block);
            if (removedSink != null) {
                removedSink.add(new ChunkBlockWriteHelper.RemovedBlock(x, y, z, block));
            }
        }
        return removed;
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
                if (DeterminingIdentical.isUnbreakable(player, block, x, y, z)) continue;

                // Protection gate, ABOVE the branches: it must also guard the TE-carrier and
                // removedByPlayer-override branches below, which go through vanilla
                // tryHarvestBlock — and that does not consult canMineBlock (vanilla only checks it
                // on the dig packet). For an automated batch removal this is the only gate.
                if (!world.canMineBlock(player, x, y, z)) continue;

                int meta = ebs.getExtBlockMetadata(lx, ly, lz);

                // TE blocks must use vanilla path (incl. TE-carrying GT ore containers
                // so breakBlock/TE cleanup always runs — see isGTTileEntityCarrier).
                if (block.hasTileEntity(meta) || DeterminingIdentical.isGTTileEntityCarrier(block)) {
                    if (player.theItemInWorldManager.tryHarvestBlock(x, y, z)) {
                        harvested++;
                    }
                    continue;
                }

                // Blocks that declare their own Block.removedByPlayer would have that side effect
                // skipped by the direct EBS write, so they take the vanilla escape hatch the
                // executors already use for TE carriers (this also runs breakBlock/TE cleanup).
                if (RemovedByPlayerBridge.overridesRemovedByPlayer(block)) {
                    if (player.theItemInWorldManager.tryHarvestBlock(x, y, z)) {
                        harvested++;
                    }
                    continue;
                }

                // ── Forge BreakEvent for the fast path only ──
                // This point is reached only by blocks that take the direct EBS write. The TE
                // carrier and removedByPlayer-override branches above continue before this line,
                // because both go through vanilla tryHarvestBlock, which fires the BreakEvent
                // itself.
                //
                // Protection is NOT handled here: world.canMineBlock is consulted once per block
                // near the top of the loop (above the branches), so it covers all three cases.
                BlockEvent.BreakEvent breakEvent = ChainBreakEventHelper
                    .fireIfEnabledOrProtected(world, player, x, y, z);
                if (breakEvent != null && breakEvent.isCanceled()) continue;

                // ── TiC compat: fire ActiveToolMod.beforeBlockBreak (IguanaTweaks tool
                // XP, autosmelt, …). true = a hook consumed the block itself — mirror
                // vanilla and skip our own harvest steps. The position is pre-registered
                // so Manager's EntityJoinWorldEvent interceptor can attribute the
                // synchronous smelt-drop spawn to this player. ──
                TinkersConstructLevelingBridge.markBeforeBlockBreak(player, x, y, z);
                boolean consumed;
                try {
                    consumed = TinkersConstructLevelingBridge.fireBeforeBlockBreak(player, x, y, z);
                } finally {
                    TinkersConstructLevelingBridge.clearBlockBreak(x, y, z);
                }
                if (consumed) {
                    harvested++;
                    continue;
                }

                // ── Vanilla shear hook: shears on leaves/grass/vines drop the block
                // item itself, injected into the drop collector via HarvestDropsEvent.
                // Cheap: the IShearable instanceof short-circuits for non-shearable
                // blocks. The block is not consumed — continue with the normal
                // removal below (getDrops may still add saplings etc.). ──
                ShearsHarvestBridge.fireIfShears(player, x, y, z, block);

                boolean canHarvest = block.canHarvestBlock(player, meta)
                    || WitcheryVampireBridge.canHarvestWithBareHands(player);

                // ── GT5 tool durability pre-check: skip blocks that would break the tool ──
                if (!isCreative && !GT5ToolDurabilityBridge.hasEnoughDurability(player, block, world, x, y, z)) {
                    continue;
                }

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
                    // Tree felling: flag adjacent leaves for vanilla decay.
                    ChunkBlockWriteHelper.flagNeighbouringLeavesForDecay(world, x, y, z, block);
                    // Notify clients so preview outlines disappear for mined blocks
                    world.markBlockForUpdate(x, y, z);
                }

                // ── Drops ──
                // Saguaro non-zero metas drop an unregistered ItemStack; the compat
                // intercepts and replaces the drops (returns true = handled).
                if (removed && canHarvest && !NaturaSaguaroCompat.maybeHarvest(world, player, x, y, z, block, meta)) {
                    block.harvestBlock(world, player, x, y, z, meta);
                }

                // ── XP ──
                if (removed && !isCreative) {
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
        if (DeterminingIdentical.isUnbreakable(player, block, x, y, z)) return false;

        // Protection gate, ABOVE the TE/override branch (see execute() for the vanilla-chain
        // reason). Has no in-tree caller today; kept consistent with the other three paths so it
        // cannot become a bypass if something starts calling it.
        if (!world.canMineBlock(player, x, y, z)) return false;

        if (block.hasTileEntity(meta) || DeterminingIdentical.isGTTileEntityCarrier(block)) {
            return player.theItemInWorldManager.tryHarvestBlock(x, y, z);
        }

        IEZMinerItemInWorldManager fastMgr = (IEZMinerItemInWorldManager) player.theItemInWorldManager;
        boolean canHarvest = block.canHarvestBlock(player, meta)
            || WitcheryVampireBridge.canHarvestWithBareHands(player);

        // No BreakEvent is pre-fired here either: the mixin's canBreakAt fires it exactly once and
        // computes XP itself when none is handed in. Pre-firing here as well produced TWO events per
        // block whenever Config.fireBreakEvent was on (this call plus the one inside canBreakAt).
        return fastMgr.ezminer$tryHarvestBlockFast(x, y, z, canHarvest, null);
    }
}
