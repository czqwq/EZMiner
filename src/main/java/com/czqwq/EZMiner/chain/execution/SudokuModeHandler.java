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
 * Per-player Sudoku assistant special-mode state and fill logic.
 * Follows the same pattern as {@link MinesweeperModeHandler}.
 */
public class SudokuModeHandler {

    private final LootGamesSudokuBridge bridge = new LootGamesSudokuBridge();
    private final Set<String> filledCells = new HashSet<>();
    private final List<Vector3i> filledPositions = new ArrayList<>();
    private long nextFillAtMs = 0L;
    /** Detects game-end transitions (StageWaiting → other stage). */
    private boolean wasGameActive = false;
    /** Detects board regeneration after level-up. */
    private String lastBoardFingerprint = null;

    /**
     * How long an "is a board active?" answer is trusted, in milliseconds.
     *
     * <p>
     * {@code isAnyGameActive} copies the entire {@code world.loadedTileEntityList} and reflects
     * over every element. {@code tick} used to call it (and {@code getBoardFingerprint}, which
     * does the same traversal again) on <em>every</em> server tick before consulting the fill
     * cooldown — 40 full list traversals per second while the chain key was held, even with no
     * LootGames board in the world. A 500 ms TTL removes the per-tick cost; the answer only
     * gates start/stop/cleanup, and the fingerprint check still runs once per TTL window.
     * </p>
     */
    private static final long GAME_ACTIVE_TTL_MS = 500L;

    /** Cached, {@link #GAME_ACTIVE_TTL_MS}-bounded answer of {@code bridge.isAnyGameActive}. */
    private boolean gameActiveCached = false;
    private long gameActiveCheckedAtMs = 0L;
    /** False until the first probe, so the very first tick always scans. */
    private boolean gameActiveKnown = false;

    /** {@code bridge.isAnyGameActive} behind the {@link #GAME_ACTIVE_TTL_MS} cache. */
    private boolean isGameActiveCached(EntityPlayerMP player) {
        long now = System.currentTimeMillis();
        if (gameActiveKnown && now - gameActiveCheckedAtMs < GAME_ACTIVE_TTL_MS) {
            return gameActiveCached;
        }
        gameActiveCached = bridge.isAnyGameActive(player.worldObj);
        gameActiveCheckedAtMs = now;
        gameActiveKnown = true;
        return gameActiveCached;
    }

    /** One probe cycle. Fills nearest incorrect cell, sends PacketSudokuFill. */
    public void tick(EntityPlayerMP player, UUID playerUUID) {
        // Detect game-end transitions (stage changed away from StageWaiting).
        boolean gameActive = isGameActiveCached(player);
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

    /** Re-send all filled positions (player re-pressed key in Sudoku mode). */
    public void resendFills(EntityPlayerMP target) {
        if (filledPositions.isEmpty()) return;
        long remainingMs = Math.max(0L, nextFillAtMs - System.currentTimeMillis());
        for (Vector3i pos : filledPositions) {
            EZMiner.network.network.sendTo(new PacketSudokuFill(pos.x, pos.y, pos.z, remainingMs), target);
        }
    }

    public boolean isReady() {
        return System.currentTimeMillis() >= nextFillAtMs;
    }

    /** Full reset on session cleanup. */
    public void reset() {
        nextFillAtMs = 0L;
        filledCells.clear();
        filledPositions.clear();
        wasGameActive = false;
        lastBoardFingerprint = null;
        gameActiveKnown = false;
        gameActiveCached = false;
    }
}
