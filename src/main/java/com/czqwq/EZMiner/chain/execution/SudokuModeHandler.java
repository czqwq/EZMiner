package com.czqwq.EZMiner.chain.execution;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.entity.player.EntityPlayerMP;

import org.joml.Vector3i;

import com.czqwq.EZMiner.Config;
import com.czqwq.EZMiner.EZMiner;
import com.czqwq.EZMiner.chain.execution.LootGamesSudokuBridge.BoardView;
import com.czqwq.EZMiner.chain.network.PacketSudokuClear;
import com.czqwq.EZMiner.chain.network.PacketSudokuFill;

/**
 * Per-player Sudoku assistant special-mode state and fill logic.
 *
 * <p>
 * Follows the same pattern as {@link MinesweeperModeHandler}: one throttled board snapshot per
 * 500&nbsp;ms feeds a per-tick re-validation of the remembered fills, so the assistant's memo can
 * never outlive the board state it describes.
 * </p>
 */
public class SudokuModeHandler {

    private final LootGamesSudokuBridge bridge = new LootGamesSudokuBridge();

    /**
     * Cells EZMiner filled and still believes are filled, keyed by
     * {@code dimension:worldX:worldY:worldZ} and valued by the cell's world position.
     *
     * <p>
     * Every entry is re-validated against the live board on each tick: a wrong submission makes
     * LootGames call {@code SudokuBoard.resetPlayer()} while keeping the puzzle and solution, so
     * an entry can silently describe an already-cleared cell — and if it were trusted, that cell
     * would never be refilled and the board could never be completed again.
     * </p>
     */
    private final Map<String, Vector3i> filledCells = new LinkedHashMap<>();

    private long nextFillAtMs = 0L;

    /**
     * How long a "which boards are waiting, and what do they look like?" answer is trusted, in
     * milliseconds.
     *
     * <p>
     * {@code snapshotBoards} copies the entire {@code world.loadedTileEntityList} and reflects
     * over every element. On a GTNH server that list routinely holds tens of thousands of
     * entries, so the old per-tick scan (which happened twice: once for {@code isAnyGameActive}
     * and once for the fingerprint) cost 40 full traversals per second. The answer only feeds
     * mark validation and the probe cadence, so a 500&nbsp;ms TTL is far below the user-visible
     * probe cadence while removing the per-tick cost.
     * </p>
     */
    private static final long BOARD_VIEW_TTL_MS = 500L;

    /**
     * How long to leave a freshly dealt board alone before probing it. LootGames sends the board
     * to the client as part of the deal (and the level-end sync can still be in flight right
     * then), so acting instantly means racing those packets.
     */
    private static final long DEAL_SETTLE_MS = 500L;

    /** Cached, {@link #BOARD_VIEW_TTL_MS}-bounded answer of {@code bridge.snapshotBoards}. */
    private List<BoardView> boardViews = Collections.emptyList();
    private long boardViewsCheckedAtMs = 0L;
    /** False until the first scan, so the very first tick always scans. */
    private boolean boardViewsKnown = false;

    /**
     * Fingerprint of the board set seen last tick. It changes on a new deal, a completed level or
     * a new game — the cases where the whole memo belongs to a board that no longer exists.
     */
    private String lastBoardFingerprint = "";

    /**
     * Set by a chain-key re-press (see {@link #resendFills}); the next tick republishes the
     * validated fill list.
     */
    private boolean resyncPending = false;

    /** {@code bridge.snapshotBoards} behind the {@link #BOARD_VIEW_TTL_MS} cache. */
    private List<BoardView> boardViews(EntityPlayerMP player, boolean force) {
        long now = System.currentTimeMillis();
        if (!force && boardViewsKnown && now - boardViewsCheckedAtMs < BOARD_VIEW_TTL_MS) {
            return boardViews;
        }
        boardViews = bridge.snapshotBoards(player.worldObj);
        boardViewsCheckedAtMs = now;
        boardViewsKnown = true;
        return boardViews;
    }

    /** One probe cycle. Fills the nearest still-empty cell, sends {@link PacketSudokuFill}. */
    public void tick(EntityPlayerMP player, UUID playerUUID) {
        // A key re-press asks for a republish, but the packet handler may run on the netty thread
        // (MainThreadEnforcer is config-gated), so it only sets this flag: the world is read here,
        // on the server thread.
        boolean resyncRequested = resyncPending;
        resyncPending = false;

        List<BoardView> views = boardViews(player, resyncRequested);
        long now = System.currentTimeMillis();

        // ── A changed board set means a new deal / completed level / new game: the remembered
        // fills describe a board that no longer exists. The wrong-answer retry keeps the same
        // board identity on purpose (LootGames resets only the player grid), so it is caught by
        // the per-tick re-validation below instead.
        String fingerprint = LootGamesSudokuBridge.boardFingerprint(views);
        if (!fingerprint.equals(lastBoardFingerprint)) {
            lastBoardFingerprint = fingerprint;
            if (!views.isEmpty()) {
                // A freshly dealt board deserves a prompt fill, just not an immediate one.
                nextFillAtMs = now + DEAL_SETTLE_MS;
                if (!filledCells.isEmpty()) {
                    filledCells.clear();
                    syncFills(player);
                }
            }
        }

        // ── No board is waiting: the game ended, was destroyed, or is between levels. Every
        // remembered fill belongs to a board that no longer exists, so drop the whole set.
        if (views.isEmpty()) {
            if (!filledCells.isEmpty() || resyncRequested) {
                filledCells.clear();
                EZMiner.network.network.sendTo(new PacketSudokuClear(), player);
            }
            // The next game gets an immediate first fill instead of the tail of the previous
            // game's cooldown.
            nextFillAtMs = 0L;
            return;
        }

        // ── Re-validate the remembered fills against the live board. This is the half that used
        // to leave the assistant permanently stalled after a wrong submission: LootGames empties
        // the player grid (default clear_on_wrong_answer=ALL) while the puzzle/solution — and
        // therefore the board fingerprint — stay unchanged, so only a live-cell check can tell
        // that a remembered fill is gone.
        int removed = bridge.invalidateStaleFills(views, filledCells);
        if (removed > 0 || resyncRequested) {
            // A key-press resync republishes even when nothing was dropped: the client clears its
            // own list on key release.
            syncFills(player);
        }

        if (now < nextFillAtMs) return;
        long cooldownMs = (long) (Math.max(0.1, Config.sudokuProbeCooldownSeconds) * 1000.0);
        nextFillAtMs = now + cooldownMs;
        Vector3i filledPos = bridge.fillNearestCell(player, views, filledCells);
        if (filledPos != null) {
            EZMiner.network.network
                .sendTo(new PacketSudokuFill(filledPos.x, filledPos.y, filledPos.z, cooldownMs), player);
        }
    }

    /**
     * Replaces the client's fill list with the server's validated one. The clear is not optional:
     * the client renders exactly the positions it was last sent, and there is no "remove one
     * fill" packet to retire a stale entry with.
     */
    private void syncFills(EntityPlayerMP target) {
        EZMiner.network.network.sendTo(new PacketSudokuClear(), target);
        long remainingMs = Math.max(0L, nextFillAtMs - System.currentTimeMillis());
        for (Vector3i pos : filledCells.values()) {
            EZMiner.network.network.sendTo(new PacketSudokuFill(pos.x, pos.y, pos.z, remainingMs), target);
        }
    }

    /**
     * Requests that the filled-cell positions be republished (the player re-pressed the key in
     * Sudoku mode).
     *
     * <p>
     * The work itself is deferred to the next {@link #tick}: this is called from the packet
     * handler, which runs on the netty IO thread whenever {@code Config.enableMainThreadGuard} is
     * off, and the republish needs a world scan and a re-validation of the remembered fills.
     * </p>
     *
     * @param target the player whose client must be republished; kept so the existing
     *               {@link com.czqwq.EZMiner.core.Manager} call shape is unchanged — the consuming
     *               tick uses the same player
     */
    public void resendFills(EntityPlayerMP target) {
        resyncPending = true;
    }

    public boolean isReady() {
        return System.currentTimeMillis() >= nextFillAtMs;
    }

    /** Full reset on session cleanup. */
    public void reset() {
        nextFillAtMs = 0L;
        filledCells.clear();
        boardViews = Collections.emptyList();
        boardViewsKnown = false;
        lastBoardFingerprint = "";
        resyncPending = false;
    }
}
