package com.czqwq.EZMiner.core.founder;

import java.util.concurrent.LinkedBlockingQueue;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.common.IPlantable;

import org.joml.Vector3i;

import com.czqwq.EZMiner.Config;
import com.czqwq.EZMiner.chain.execution.PlantingModeHandler;
import com.czqwq.EZMiner.core.MinerConfig;

/**
 * Planting mode preview finder.
 *
 * <p>
 * Scans the same Chebyshev shells as {@link PlantingModeHandler#handlePlant}
 * around the aimed soil block, applying the shared
 * {@link PlantingModeHandler#isPlantablePosition} predicate — so the client
 * preview shows exactly the area that will be planted. The shell radius and
 * candidate cap come from {@link Config#plantRadius} /
 * {@link Config#plantMaxCount} (client-side, synced from the server), not the
 * preview config.
 *
 * <p>
 * Runs single-threaded (the area is at most a 12-block shell ≈ 1-2k cells) and
 * yields via the standard {@code consumeBudget()} cooperative pause; when the
 * held item is not plantable the search produces nothing.
 */
public class PlantingPositionFounder extends BasePositionFounder {

    /** Resolved once in the constructor (render thread); immutable afterwards. */
    private final IPlantable plantable;

    public PlantingPositionFounder(Vector3i center, LinkedBlockingQueue<Vector3i> results, EntityPlayer player,
        MinerConfig minerConfig) {
        super(center, results, player, minerConfig);
        setName("EZMiner-PlantSearch");
        this.plantable = PlantingModeHandler.plantableFrom(player.getHeldItem());
    }

    @Override
    protected void doSingleThreadedSearch() {
        int maxRadius = Math.max(0, Config.plantRadius);
        int maxCount = Config.plantMaxCount;
        for (int r = 0; r <= maxRadius; r++) {
            if (player == null || player.isDead || player.worldObj == null) return;
            for (int dx = -r; dx <= r; dx++) {
                for (int dy = -r; dy <= r; dy++) {
                    for (int dz = -r; dz <= r; dz++) {
                        if (Math.max(Math.max(Math.abs(dx), Math.abs(dy)), Math.abs(dz)) != r) continue;
                        if (curCount.get() >= maxCount) return;
                        if (!consumeBudget()) return; // cooperative pause, same as chain search
                        int x = center.x + dx;
                        int y = center.y + dy;
                        int z = center.z + dz;
                        if (isVisited(x, y, z)) continue;
                        Vector3i pos = new Vector3i(x, y, z);
                        if (checkCanAdd(pos)) {
                            // Preview the plant position above the soil — this is the
                            // very cell the predicate verified as air (isAirBlock on
                            // y+1), so soil buried under non-air (underground dirt
                            // below stone) is never matched. Same visual semantics as
                            // a chain preview marking the affected blocks.
                            addResult(new Vector3i(pos.x, pos.y + 1, pos.z));
                        }
                    }
                }
            }
        }
    }

    /** The plant-area scan is small — force the single-threaded path. */
    @Override
    protected void doMultiThreadedSearch() {
        doSingleThreadedSearch();
    }

    @Override
    protected boolean checkCanAddImpl(Vector3i pos) {
        if (plantable == null || player.worldObj == null) return false;
        // Do not plant under the player's feet (mirrors the base-class filter).
        if (pos.x == cachedPlayerFloorX && pos.y == (cachedPlayerFloorY - 1) && pos.z == cachedPlayerFloorZ) {
            return false;
        }
        // Exact predicate: area check + canSustainPlant, so the preview shows
        // only soil that actually accepts the held plantable (not stone/logs/…).
        return PlantingModeHandler.isPreviewPlantablePosition(player.worldObj, pos.x, pos.y, pos.z, plantable);
    }
}
