package com.czqwq.EZMiner.chain.execution;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.ChatComponentTranslation;

import com.czqwq.EZMiner.Config;
import com.czqwq.EZMiner.EZMiner;
import com.czqwq.EZMiner.chain.network.PacketProspectState;
import com.czqwq.EZMiner.utils.MessageUtils;

/**
 * Per-player virtual prospecting special-mode state.
 *
 * <p>
 * While the chain key is held, one ore vein is probed per countdown interval
 * (wall-clock, same pattern as {@link MinesweeperModeHandler}). The scan walks a
 * spiral over GTNH's ore-vein grid (vein centers every 3 chunks, matching
 * {@code Utils.mapToCenterOreChunkCoord} in VisualProspecting) instead of
 * probing every chunk — so every probe lands on a vein center chunk and hits
 * immediately. Discovered ore veins are sent to the client via
 * VisualProspecting, which adds its waypoints ("discovery").
 *
 * <p>
 * The scan re-centers on the player when they move more than 3 chunks from the
 * current center (instead of waiting for the full disc to complete), and the
 * disc radius is capped at the player's view distance (7 chunks = 15x15
 * chunks), so probed veins are always close to the player. Cells already
 * probed stay probed across re-centers, so moving back and forth never
 * re-scans the same ground.
 */
public class ProspectModeHandler {

    /** Chunk spacing between GTNH ore-vein centers (3x3 veins, no gap). */
    private static final int VEIN_GRID = 3;
    /** Re-center the scan when the player moves more than this many chunks from the center. */
    private static final int RECENTER_DIST_CHUNKS = 3;

    private final VisualProspectingBridge bridge = new VisualProspectingBridge();
    private long nextProbeAtMs = 0L;
    private int lastDimension = Integer.MIN_VALUE;
    private boolean centered = false;
    private int centerChunkX = 0;
    private int centerChunkZ = 0;
    /** Spiral cursor: current Chebyshev ring of grid cells around the center. */
    private int ring = 0;
    /** Spiral cursor: current edge of the ring (0 top, 1 left, 2 bottom, 3 right). */
    private int edge = 0;
    /** Spiral cursor: position along the current edge. */
    private int posInEdge = 0;
    /**
     * Vein-center chunks already probed in this session. Persists across
     * re-centering so overlapping discs are never re-probed (a player walking
     * back and forth would otherwise re-scan the same cells); cleared only on
     * dimension change or {@link #reset()}.
     */
    private final Set<Long> probedChunks = new HashSet<>();
    /** Veins already reported to the player; persists across re-centering, cleared only on reset(). */
    private final Set<Long> discoveredVeinKeys = new HashSet<>();

    /** One probe cycle: after the countdown elapses, probes the next vein-center chunk. */
    public void tick(EntityPlayerMP player, UUID playerUUID) {
        if (!VisualProspectingBridge.isVpAvailable()) return;
        if (player.dimension != lastDimension) {
            lastDimension = player.dimension;
            resetScan();
        }
        // Follow the player: re-center as soon as they move away from the scan
        // center, so the scan keeps probing veins around the current position.
        recenterIfNeeded(player);

        long now = System.currentTimeMillis();
        if (now < nextProbeAtMs) return;
        long intervalMs = (long) (Math.max(0.1, Config.prospectProbeIntervalSeconds) * 1000.0);
        nextProbeAtMs = now + intervalMs;

        if (!centered) centerOn(player);

        // Fast-forward over already-probed vein centers. Bounded: each iteration
        // either probes a fresh cell and returns, or advances the cursor;
        // re-centering clears the set, so the cursor always lands on a fresh
        // cell within one disc's worth of cells.
        int maxRing = maxCellRing();
        int budget = (2 * maxRing + 1) * (2 * maxRing + 1) + 1;
        while (budget-- > 0) {
            int cx = candidateChunkX();
            int cz = candidateChunkZ();
            long chunkKey = ((long) cx << 32) | (cz & 0xFFFFFFFFL);
            if (probedChunks.add(chunkKey)) {
                VisualProspectingBridge.VeinHit hit = bridge
                    .prospectChunkAndNotify(player, cx * 16 + 8, cz * 16 + 8, discoveredVeinKeys);
                if (hit != null) {
                    int distance = (int) Math.round(Math.hypot(player.posX - hit.blockX, player.posZ - hit.blockZ));
                    MessageUtils.serverSendPlayerMessage(
                        new ChatComponentTranslation(
                            "ezminer.message.special.prospect.found",
                            hit.veinName,
                            hit.blockX,
                            hit.blockZ,
                            distance),
                        playerUUID);
                }
                EZMiner.network.network.sendTo(new PacketProspectState(intervalMs), player);
                return; // one probe per countdown interval
            }
            advanceCursor(player);
        }
    }

    /**
     * Re-centers the scan on the player when they have moved more than
     * {@value #RECENTER_DIST_CHUNKS} chunks away from the current center.
     */
    private void recenterIfNeeded(EntityPlayerMP player) {
        if (!centered) return;
        int dx = player.chunkCoordX - centerChunkX;
        int dz = player.chunkCoordZ - centerChunkZ;
        if (Math.abs(dx) > RECENTER_DIST_CHUNKS || Math.abs(dz) > RECENTER_DIST_CHUNKS) {
            centerOn(player);
        }
    }

    /**
     * Re-centers the spiral on the player's chunk. The probed set is preserved
     * so cells already scanned (including overlap with the previous disc) are
     * skipped — see {@link #probedChunks}.
     */
    private void centerOn(EntityPlayerMP player) {
        centerChunkX = player.chunkCoordX;
        centerChunkZ = player.chunkCoordZ;
        ring = 0;
        edge = 0;
        posInEdge = 0;
        centered = true;
    }

    private void resetScan() {
        centered = false;
        probedChunks.clear(); // chunk coords are meaningless across dimensions
    }

    /** Maximum spiral ring of grid cells; keeps every probed vein within the scan radius. */
    private int maxCellRing() {
        return Math.max(1, Config.prospectMaxScanRadiusChunks / VEIN_GRID);
    }

    /** Snaps a chunk coordinate to the nearest vein-center chunk (chunkCoord ≡ 1 mod 3). */
    private static int gridSnap(int chunkCoord) {
        return chunkCoord - Math.floorMod(chunkCoord, VEIN_GRID) + 1;
    }

    /** Cursor cell offset X for the current spiral position (ring 0 = center cell). */
    private int cellOffsetX() {
        if (ring == 0) return 0;
        switch (edge) {
            case 0: // top edge, moving left
                return ring - posInEdge;
            case 1: // left edge
                return -ring;
            case 2: // bottom edge, moving right
                return -ring + posInEdge;
            default: // right edge
                return ring;
        }
    }

    /** Cursor cell offset Z for the current spiral position (ring 0 = center cell). */
    private int cellOffsetZ() {
        if (ring == 0) return 0;
        switch (edge) {
            case 0: // top edge
                return -ring;
            case 1: // left edge, moving down
                return -ring + posInEdge;
            case 2: // bottom edge
                return ring;
            default: // right edge, moving up
                return ring - posInEdge;
        }
    }

    /** Candidate vein-center chunk X for the current spiral cell. */
    private int candidateChunkX() {
        return gridSnap(centerChunkX) + cellOffsetX() * VEIN_GRID;
    }

    /** Candidate vein-center chunk Z for the current spiral cell. */
    private int candidateChunkZ() {
        return gridSnap(centerChunkZ) + cellOffsetZ() * VEIN_GRID;
    }

    /** Advances the spiral cursor; re-centers on the player past the max ring. */
    private void advanceCursor(EntityPlayerMP player) {
        posInEdge++;
        int edgeLen = edge == 0 ? 2 * ring + 1 : 2 * ring;
        if (posInEdge < edgeLen) return;
        edge++;
        posInEdge = 0;
        if (edge < 4) return;
        edge = 0;
        ring++;
        if (ring > maxCellRing()) centerOn(player);
    }

    public boolean isReady() {
        return System.currentTimeMillis() >= nextProbeAtMs;
    }

    /** Full reset on session cleanup. Cooldown retained across mode switches. */
    public void reset() {
        nextProbeAtMs = 0L;
        lastDimension = Integer.MIN_VALUE;
        resetScan();
        discoveredVeinKeys.clear();
    }
}
