package com.czqwq.EZMiner.chain.execution;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import net.minecraft.entity.player.EntityPlayerMP;

import org.joml.Vector3i;

import com.czqwq.EZMiner.Config;
import com.czqwq.EZMiner.EZMiner;
import com.czqwq.EZMiner.chain.network.PacketMinesweeperMark;

/**
 * Encapsulates the per-player minesweeper special-mode state and probe logic.
 *
 * <p>
 * Extracted from {@link com.czqwq.EZMiner.core.Manager} to give the minesweeper
 * feature a clear owner with a single responsibility: detecting and flagging mines
 * for one player.
 */
public class MinesweeperModeHandler {

    private final LootGamesMinesweeperBridge bridge = new LootGamesMinesweeperBridge();
    private final Set<String> detectedBombs = new HashSet<>();
    /** World positions of all mines flagged in this session; used to re-send on key re-press. */
    private final List<Vector3i> detectedPositions = new ArrayList<>();
    private long nextDetectAtMs = 0L;

    /**
     * Runs one probe cycle. Sends a {@link PacketMinesweeperMark} packet if a new mine
     * is found. Must be called from the server thread.
     *
     * @param player     the owning player
     * @param playerUUID the player's UUID (used for packet targeting)
     */
    public void tick(EntityPlayerMP player, UUID playerUUID) {
        long now = System.currentTimeMillis();
        if (now < nextDetectAtMs) return;
        long cooldownMs = Math.max(1L, (long) Config.minesweeperProbeCooldownSeconds) * 1000L;
        nextDetectAtMs = now + cooldownMs;
        Vector3i flaggedPos = bridge.detectNearestBomb(player, detectedBombs);
        if (flaggedPos != null) {
            detectedPositions.add(flaggedPos);
            EZMiner.network.network
                .sendTo(new PacketMinesweeperMark(flaggedPos.x, flaggedPos.y, flaggedPos.z, cooldownMs), player);
        }
    }

    /**
     * Returns whether the next probe is due (i.e. the cooldown has elapsed).
     */
    public boolean isReady() {
        return System.currentTimeMillis() >= nextDetectAtMs;
    }

    /**
     * Re-sends all previously-flagged mine positions to {@code target}.
     *
     * <p>
     * Called when the player re-presses the chain key in minesweeper mode so that the
     * client's flagged-mine list is repopulated without requiring the server to re-probe.
     */
    public void resendMarks(EntityPlayerMP target) {
        if (detectedPositions.isEmpty()) return;
        long remainingMs = Math.max(0L, nextDetectAtMs - System.currentTimeMillis());
        for (Vector3i pos : detectedPositions) {
            EZMiner.network.network.sendTo(new PacketMinesweeperMark(pos.x, pos.y, pos.z, remainingMs), target);
        }
    }

    /**
     * Resets all minesweeper state. Called on session cleanup (player logout, respawn, etc.).
     *
     * <p>
     * The cooldown timer and detected-bomb set are intentionally preserved across mode
     * switches (only cleared here) so that quickly switching away and back cannot bypass
     * the configured probe interval.
     */
    public void reset() {
        nextDetectAtMs = 0L;
        detectedBombs.clear();
        detectedPositions.clear();
    }
}
