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
import com.czqwq.EZMiner.chain.network.PacketSudokuClear;
import com.czqwq.EZMiner.chain.network.PacketSudokuFill;

/**
 * Encapsulates the per-player Sudoku assistant special-mode state and fill logic.
 *
 * <p>
 * Follows the same pattern as {@link MinesweeperModeHandler}: each probe cycle finds
 * one unfilled/incorrect cell on a nearby LootGames Sudoku board and fills the correct
 * answer, then sends a {@link PacketSudokuFill} to the client for HUD / rendering.
 */
public class SudokuModeHandler {

    private final LootGamesSudokuBridge bridge = new LootGamesSudokuBridge();
    private final Set<String> filledCells = new HashSet<>();
    /** World positions of all cells filled in this session; used to re-send on key re-press. */
    private final List<Vector3i> filledPositions = new ArrayList<>();
    private long nextFillAtMs = 0L;
    /**
     * Tracks whether any LootGames Sudoku game was in {@code StageWaiting} during the
     * previous probe cycle. Used to detect game-end transitions.
     */
    private boolean wasGameActive = false;
    /** Last known board fingerprint; used to detect board regeneration after a level-up. */
    private String lastBoardFingerprint = null;

    /**
     * Runs one probe cycle. Finds the nearest unfilled/incorrect cell, fills the
     * correct answer, and sends a {@link PacketSudokuFill} to the client.
     *
     * @param player     the owning player
     * @param playerUUID the player's UUID
     */
    public void tick(EntityPlayerMP player, UUID playerUUID) {
        // Detect game-end transitions (stage changed away from StageWaiting).
        boolean gameActive = bridge.isAnyGameActive(player.worldObj);
        if (wasGameActive && !gameActive && !filledCells.isEmpty()) {
            reset();
            EZMiner.network.network.sendTo(new PacketSudokuClear(), player);
        }
        wasGameActive = gameActive;

        if (!gameActive) return;

        // Detect board regeneration (e.g. after a level-up a new board is generated
        // while staying in StageWaiting). If the fingerprint changed, clear stale keys
        // so the new board's cells are not incorrectly skipped.
        String fingerprint = bridge.getBoardFingerprint(player.worldObj);
        if (fingerprint != null && lastBoardFingerprint != null && !fingerprint.equals(lastBoardFingerprint)) {
            filledCells.clear();
            filledPositions.clear();
            EZMiner.network.network.sendTo(new PacketSudokuClear(), player);
        }
        if (fingerprint != null) {
            lastBoardFingerprint = fingerprint;
        }

        long now = System.currentTimeMillis();
        if (now < nextFillAtMs) return;
        long cooldownMs = (long) (Math.max(0.1, Config.sudokuProbeCooldownSeconds) * 1000.0);
        nextFillAtMs = now + cooldownMs;
        Vector3i filledPos = bridge.detectAndFillNearestCell(player, filledCells);
        if (filledPos != null) {
            filledPositions.add(filledPos);
            EZMiner.network.network
                .sendTo(new PacketSudokuFill(filledPos.x, filledPos.y, filledPos.z, cooldownMs), player);
        }
    }

    /**
     * Re-sends all previously-filled cell positions to {@code target}.
     */
    public void resendFills(EntityPlayerMP target) {
        if (filledPositions.isEmpty()) return;
        long remainingMs = Math.max(0L, nextFillAtMs - System.currentTimeMillis());
        for (Vector3i pos : filledPositions) {
            EZMiner.network.network.sendTo(new PacketSudokuFill(pos.x, pos.y, pos.z, remainingMs), target);
        }
    }

    /**
     * Returns whether the next probe is due.
     */
    public boolean isReady() {
        return System.currentTimeMillis() >= nextFillAtMs;
    }

    /**
     * Resets all Sudoku assistant state.
     */
    public void reset() {
        nextFillAtMs = 0L;
        filledCells.clear();
        filledPositions.clear();
        wasGameActive = false;
        lastBoardFingerprint = null;
    }
}
