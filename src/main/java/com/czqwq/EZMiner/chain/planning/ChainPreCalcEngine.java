package com.czqwq.EZMiner.chain.planning;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;

import org.joml.Vector3i;

import com.czqwq.EZMiner.EZMiner;
import com.czqwq.EZMiner.chain.network.PacketCachedBlockSync;
import com.czqwq.EZMiner.chain.planning.ChainPreCalcCache.CachedEntry;
import com.czqwq.EZMiner.core.MinerConfig;
import com.czqwq.EZMiner.core.MinerModeState;
import com.czqwq.EZMiner.core.founder.BasePositionFounder;
import com.czqwq.EZMiner.core.founder.DeterminingIdentical;

import codechicken.lib.raytracer.RayTracer;

/**
 * Server-thread BFS engine for pre-calculating chain-mining block positions.
 *
 * <p>
 * <strong>Decoupled from {@code Manager}:</strong> this class owns the BFS state
 * and execution logic.
 *
 * <p>
 * <strong>Hodgepodge compatibility:</strong> the BFS runs directly on the server
 * thread, processing at most {@link #CHECKS_PER_TICK} candidates per tick.
 */
public final class ChainPreCalcEngine {

    private final ArrayDeque<Vector3i> frontier = new ArrayDeque<>(256);
    private final HashSet<Long> visited = new HashSet<>(256);
    private final List<Vector3i> results = new ArrayList<>(256);

    private Vector3i center;
    private Block sampleBlock;
    /**
     * Cached raw block ID for fast integer comparison in normal chain mode.
     * Set in {@link #start} alongside {@link #sampleBlock}.
     */
    private int sampleBlockId;
    private int sampleMeta;
    private TileEntity sampleTE;
    /**
     * Canonical class for fuzzy-mode matching and cache-key computation.
     */
    private Class<?> fuzzyTypeClass;

    private int hash = -1;
    private boolean inProgress;
    private int cooldown;

    private static final int CHECKS_PER_TICK = 2048;

    // ── Public API ──

    public boolean isInProgress() {
        return inProgress;
    }

    public void tick(EntityPlayerMP player, MinerConfig pConfig, MinerModeState modeState) {
        if (!modeState.isCachedChainMode()) {
            stop(player);
            return;
        }
        if (player == null || player.worldObj == null || player.isDead) return;

        if (cooldown > 0) {
            cooldown--;
            return;
        }

        if (inProgress) {
            tickBfs(player, pConfig, modeState);
            return;
        }

        start(player, pConfig, modeState);
    }

    public void stop(EntityPlayerMP player) {
        clearState();
        if (player != null) {
            ChainPreCalcCache.remove(player.getUniqueID());
            EZMiner.network.network
                .sendTo(new PacketCachedBlockSync(java.util.Collections.<Vector3i>emptyList(), 0, 0, 0, 0), player);
        }
    }

    public void cleanup() {
        clearState();
    }

    // ── Internal state machine ──

    private void clearState() {
        frontier.clear();
        visited.clear();
        results.clear();
        center = null;
        sampleBlock = null;
        sampleBlockId = 0;
        sampleTE = null;
        fuzzyTypeClass = null;
        inProgress = false;
        hash = -1;
        cooldown = 0;
    }

    private void start(EntityPlayerMP player, MinerConfig pConfig, MinerModeState modeState) {
        DeterminingIdentical.checkCompatibility();

        World world = player.worldObj;
        MovingObjectPosition mop = RayTracer.reTrace(world, player);
        if (mop == null || mop.typeOfHit != MovingObjectPosition.MovingObjectType.BLOCK) return;

        int bx = mop.blockX, by = mop.blockY, bz = mop.blockZ;
        if (!world.blockExists(bx, by, bz)) return;
        Block block = world.getBlock(bx, by, bz);
        if (block.isAir(world, bx, by, bz)) return;
        int meta = world.getBlockMetadata(bx, by, bz);
        int dimension = world.provider.dimensionId;

        int newHash = ChainPreCalcCache.computeHash(new Vector3i(bx, by, bz), block, meta, dimension);
        if (hash == newHash) {
            cooldown = 10;
            return;
        }

        frontier.clear();
        visited.clear();
        results.clear();
        hash = newHash;

        center = new Vector3i(bx, by, bz);
        sampleBlock = block;
        sampleBlockId = Block.getIdFromBlock(block);
        sampleMeta = meta;
        sampleTE = world.getTileEntity(bx, by, bz);
        fuzzyTypeClass = block.getClass();

        frontier.addLast(center);
        long centerKey = BasePositionFounder.encodePos(bx, by, bz);
        visited.add(centerKey);
        results.add(new Vector3i(bx, by, bz));
        inProgress = true;
    }

    private void tickBfs(EntityPlayerMP player, MinerConfig pConfig, MinerModeState modeState) {
        if (!inProgress || center == null) return;
        if (player == null || player.worldObj == null || player.isDead) {
            stop(player);
            return;
        }

        World world = player.worldObj;
        int checksThisTick = 0;
        int sr = pConfig.smallRadius;
        int bigR = pConfig.bigRadius;
        boolean fuzzy = modeState.chainMode == 3;
        int cachedPlayerX = (int) Math.floor(player.posX);
        int cachedPlayerY = (int) Math.floor(player.posY);
        int cachedPlayerZ = (int) Math.floor(player.posZ);

        try {
            // ── Sub-chunk cache: avoid repeated World→ChunkProvider→Chunk→EBS lookups ──
            // Uses ExtendedBlockStorage.getBlockByExtId() which is safely patched by
            // EndlessIDs to resolve extended block IDs (b2High, b3 arrays).
            int lastChunkX = Integer.MIN_VALUE;
            int lastChunkZ = Integer.MIN_VALUE;
            int lastY4 = -1;
            Chunk currentChunk = null;
            ExtendedBlockStorage currentEbs = null;

            while (!frontier.isEmpty() && checksThisTick < CHECKS_PER_TICK && results.size() < pConfig.blockLimit) {

                Vector3i current = frontier.pollFirst();
                if (current == null) break;

                for (int dx = -sr; dx <= sr; dx++) {
                    int cx = current.x + dx;
                    if (Math.abs(cx - center.x) > bigR) continue;
                    for (int dy = -sr; dy <= sr; dy++) {
                        int cy = current.y + dy;
                        if (Math.abs(cy - center.y) > bigR) continue;
                        for (int dz = -sr; dz <= sr; dz++) {
                            if (dx == 0 && dy == 0 && dz == 0) continue;
                            int cz = current.z + dz;
                            if (Math.abs(cz - center.z) > bigR) continue;

                            long key = BasePositionFounder.encodePos(cx, cy, cz);
                            if (visited.contains(key)) continue;

                            checksThisTick++;

                            // ── Chunk + EBS cache (avoids LongHashMap lookup per block) ──
                            int chunkX = cx >> 4;
                            int chunkZ = cz >> 4;
                            int y4 = cy >> 4;

                            // When chunk loading is disabled, skip positions in unloaded chunks
                            // without adding to visited so they can be retried after the player
                            // moves and the chunk loads naturally.
                            if (!com.czqwq.EZMiner.Config.enableChainChunkLoading && !world.blockExists(cx, cy, cz)) {
                                continue;
                            }

                            if (chunkX != lastChunkX || chunkZ != lastChunkZ) {
                                currentChunk = world.getChunkFromChunkCoords(chunkX, chunkZ);
                                lastChunkX = chunkX;
                                lastChunkZ = chunkZ;
                                lastY4 = -1; // force EBS refresh
                            }
                            if (y4 != lastY4 || currentEbs == null) {
                                currentEbs = currentChunk != null ? currentChunk.getBlockStorageArray()[y4] : null;
                                lastY4 = y4;
                            }

                            // getBlockByExtId is safe with EndlessIDs (they @Overwrite it
                            // to resolve b2High/b3 extended block IDs correctly).
                            // If the chunk is not loaded, skip without adding to visited so
                            // the position can be retried on future ticks when the chunk loads.
                            Block nbBlock = currentEbs != null ? currentEbs.getBlockByExtId(cx & 15, cy & 15, cz & 15)
                                : null;
                            if (nbBlock == null) continue;

                            // Position is now confirmed reachable — mark visited
                            visited.add(key);

                            if (nbBlock.isAir(world, cx, cy, cz) || nbBlock.getMaterial()
                                .isLiquid() || nbBlock == Blocks.bedrock) continue;

                            if (cx == cachedPlayerX && cy == (cachedPlayerY - 1) && cz == cachedPlayerZ) continue;

                            // ── Match logic (Bandit-style: block identity only) ──
                            boolean match;
                            if (fuzzy) {
                                Class<?> sampleClass = fuzzyTypeClass != null ? fuzzyTypeClass : sampleBlock.getClass();
                                Class<?> nbClass = nbBlock.getClass();
                                match = sampleClass.isAssignableFrom(nbClass) || nbClass.isAssignableFrom(sampleClass);
                            } else {
                                // Hodgepodge's MixinBlock_FastLookup makes getIdFromBlock a fast
                                // array lookup — we trade the raw-array read for EndlessIDs safety
                                // while keeping the Chunk-cache speedup.
                                match = sampleBlockId == Block.getIdFromBlock(nbBlock);
                            }
                            if (!match) continue;

                            Vector3i result = new Vector3i(cx, cy, cz);
                            results.add(result);
                            frontier.addLast(result);

                            if (results.size() >= pConfig.blockLimit) break;
                        }
                        if (checksThisTick >= CHECKS_PER_TICK || results.size() >= pConfig.blockLimit) break;
                    }
                    if (checksThisTick >= CHECKS_PER_TICK || results.size() >= pConfig.blockLimit) break;
                }
            }

            boolean done = frontier.isEmpty() || results.size() >= pConfig.blockLimit;

            int dimension = world.provider.dimensionId;
            EZMiner.network.network
                .sendTo(new PacketCachedBlockSync(results, center.x, center.y, center.z, dimension), player);

            if (done) {
                inProgress = false;
                frontier.clear();
                visited.clear();

                int typeHash;
                String fuzzyTypeName = null;
                if (fuzzy && fuzzyTypeClass != null) {
                    typeHash = ChainPreCalcCache.computeTypeHash(fuzzyTypeClass, sampleMeta, dimension);
                    fuzzyTypeName = fuzzyTypeClass.getName();
                } else {
                    // Bandit-style: hash by block ID only, no meta
                    typeHash = ChainPreCalcCache.computeBlockIdHash(Block.getIdFromBlock(sampleBlock), dimension);
                }
                CachedEntry entry = new CachedEntry(
                    hash,
                    typeHash,
                    fuzzyTypeName,
                    new ArrayList<>(results),
                    dimension,
                    center.x,
                    center.y,
                    center.z);
                ChainPreCalcCache.put(player.getUniqueID(), entry);
                cooldown = 20;
            }
        } catch (Exception e) {
            EZMiner.LOG.error("PreCalc BFS error", e);
            stop(player);
        }
    }
}
