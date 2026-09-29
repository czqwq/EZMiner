package com.czqwq.EZMiner.core.founder;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;

import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraftforge.oredict.OreDictionary;

import org.joml.Vector3i;

import com.czqwq.EZMiner.compat.WitcheryVampireBridge;
import com.czqwq.EZMiner.core.MinerConfig;

/** Blast mode – logging: breaks only wood and leaf blocks. */
public class LogFounder extends BasePositionFounder {

    /**
     * Per-block-type cache for the OreDict wood/leaf check.
     * Avoids creating a new {@link ItemStack} and scanning OreDict entries on every
     * candidate block; the result for each unique {@link Block} instance is computed
     * once and reused for all subsequent occurrences.
     */
    private static final ConcurrentHashMap<Block, Boolean> woodLeafCache = new ConcurrentHashMap<>();

    public LogFounder(Vector3i center, LinkedBlockingQueue<Vector3i> results, EntityPlayer player,
        MinerConfig minerConfig) {
        super(center, results, player, minerConfig);
        setName("EZMiner-BlastLog");
    }

    @Override
    public void run1() {
        // Shell expansion, decomposed so each shell pays only for the positions it adds. The
        // previous implementation walked the full (2R+1)x(2H+1)x(2R+1) box for every shell with the
        // "already scanned" skip test INSIDE the innermost loop, and grew highRadius to 4*curRadius;
        // at the old default radius of 1024 that was ~6.8e13 innermost iterations / ~3.4e10 world
        // lookups, so the mode effectively never finished (on the client preview path too). It also
        // had no player/world guard.
        //
        // For shell r the new positions are exactly:
        // (i) the new y-levels ([-high, -prevHigh-1] and [prevHigh+1, high]) over the full x/z
        // extent of the current shell;
        // (ii) the two new x columns (x = ±r) at the previously covered y-levels;
        // (iii) the two new z columns (z = ±r) at x in [-r+1, r-1] and the previously covered
        // y-levels.
        // Those three sets are disjoint and their union is shell(r) \ shell(r-1) — verified by hand
        // for r = 1 (72 + 6 + 2 = 80 = 3x9x3 - 1) and r = 2 (200 + 90 + 54 = 344 = 5x17x5 - 3x9x3).
        // Because every stop path returns immediately, the paused/limit-reached case allocates O(1)
        // rather than a per-shell position list.
        if (player == null || player.isDead || player.worldObj == null) return;

        final int radius = Math.max(0, minerConfig.logBigRadius);

        if (checkCanAdd(center)) {
            addResult(center);
            if (curCount.get() >= minerConfig.logBlockLimit) return;
        }
        waitUntil();
        if (Thread.currentThread()
            .isInterrupted()) return;

        int prevRadius = 0;
        int prevHighRadius = 0;
        for (int curRadius = 1; curRadius <= radius; curRadius++) {
            final int highRadius = Math.min(4 * curRadius, Math.max(4 * radius, 64));
            final int r = curRadius;
            final int prevHigh = prevHighRadius;

            // (i) New y-levels over the full x/z extent of the current shell. Emitted as two bands
            // so the already-covered middle never has to be skipped.
            if (highRadius > prevHigh) {
                if (emit(-r, r, -highRadius, -prevHigh - 1, -r, r)) return;
                if (emit(-r, r, prevHigh + 1, highRadius, -r, r)) return;
            }

            // (ii) + (iii) The new x and z columns at the previously covered y-levels.
            if (curRadius > prevRadius) {
                if (emit(-r, -r, -prevHigh, prevHigh, -r, r)) return;
                if (emit(r, r, -prevHigh, prevHigh, -r, r)) return;
                if (emit(-r + 1, r - 1, -prevHigh, prevHigh, -r, -r)) return;
                if (emit(-r + 1, r - 1, -prevHigh, prevHigh, r, r)) return;
            }

            prevRadius = curRadius;
            prevHighRadius = highRadius;
        }
    }

    /**
     * Walks one closed box of positions and feeds each to {@link #checkCanAdd}/{@link #addResult}.
     *
     * <p>
     * The box bounds are passed explicitly rather than derived from each other: the caller's slabs
     * are a thin y-band, two single x columns and two single z columns, which cannot be expressed as
     * "the same range in x and z".
     * </p>
     *
     * @param xMin,xMax inclusive x range, relative to {@link #center}
     * @param yMin,yMax inclusive y range, relative to {@link #center}
     * @param zMin,zMax inclusive z range, relative to {@link #center}
     * @return {@code true} if the caller must stop (block limit reached, tick-end pause, or
     *         thread interrupt)
     */
    private boolean emit(int xMin, int xMax, int yMin, int yMax, int zMin, int zMax) {
        for (int y = yMin; y <= yMax; y++) {
            for (int z = zMin; z <= zMax; z++) {
                for (int x = xMin; x <= xMax; x++) {
                    Vector3i pos = new Vector3i(center.x + x, center.y + y, center.z + z);
                    if (checkCanAdd(pos)) addResult(pos);
                    if (curCount.get() >= minerConfig.logBlockLimit) return true;
                    waitUntil();
                    if (Thread.currentThread()
                        .isInterrupted()) return true;
                }
            }
        }
        return false;
    }

    @Override
    public boolean checkCanAdd(Vector3i pos) {
        if (isVisited(pos.x, pos.y, pos.z)) return false;
        if (player.worldObj == null) return false;
        if (!player.worldObj.blockExists(pos.x, pos.y, pos.z)) return false;
        Block block = player.worldObj.getBlock(pos.x, pos.y, pos.z);
        if (block.equals(Blocks.air) || block.getMaterial()
            .isLiquid() || block.equals(Blocks.bedrock)) return false;
        int blockMeta = player.worldObj.getBlockMetadata(pos.x, pos.y, pos.z);
        if (pos.x == cachedPlayerFloorX && pos.y == (cachedPlayerFloorY - 1) && pos.z == cachedPlayerFloorZ)
            return false;
        if (minerConfig.logFuzzyEnabled) {
            // Class-based fuzzy matching (same as FuzzyChainPositionFounder).
            // Matches blocks of the same class regardless of metadata
            // (e.g. different log orientations share the same BlockLog class).
            if (sampleBlock == null || !sampleBlock.getClass()
                .equals(block.getClass())) return false;
        } else {
            // Original OreDict-based wood/leaf detection
            if (!woodLeafCache.computeIfAbsent(block, LogFounder::isWoodOrLeaf)) return false;
        }
        if (DeterminingIdentical.isUnbreakable(player, block, pos.x, pos.y, pos.z)) return false;
        if (skipHarvestCheck) return true;
        if (player.capabilities.isCreativeMode) return true;
        return block.canHarvestBlock(player, blockMeta) || WitcheryVampireBridge.canHarvestWithBareHands(player);
    }

    /** OreDict check – called at most once per unique {@link Block} type. */
    private static boolean isWoodOrLeaf(Block block) {
        int[] oreIDs = OreDictionary.getOreIDs(new ItemStack(block));
        for (int oreID : oreIDs) {
            String name = OreDictionary.getOreName(oreID);
            if (name.equals("logWood") || name.equals("treeLeaves")) return true;
        }
        return false;
    }
}
