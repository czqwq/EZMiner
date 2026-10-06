package com.czqwq.EZMiner.chain.execution;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import net.minecraft.entity.player.EntityPlayerMP;

import com.czqwq.EZMiner.Config;
import com.czqwq.EZMiner.EZMiner;
import com.czqwq.EZMiner.chain.execution.LootGamesMinesweeperBridge.BoardView;
import com.czqwq.EZMiner.chain.execution.LootGamesMinesweeperBridge.Mark;
import com.czqwq.EZMiner.chain.network.PacketMinesweeperClear;
import com.czqwq.EZMiner.chain.network.PacketMinesweeperMark;

/**
 * Per-player minesweeper special-mode state and probe logic.
 */
public class MinesweeperModeHandler {

    private final LootGamesMinesweeperBridge bridge = new LootGamesMinesweeperBridge();

    /**
     * Bombs EZMiner has flagged on the current board. Every entry is re-validated against the live
     * board on each tick so a marker wireframe can never outlive the flag it describes.
     */
    private final List<Mark> marks = new ArrayList<>();

    private long nextDetectAtMs = 0L;

    /**
     * How long a "which boards are active, and what is their geometry?" answer is trusted, in
     * milliseconds.
     *
     * <p>
     * {@code snapshotBoards} copies the entire {@code world.loadedTileEntityList} and reflects over
     * every element. On a GTNH server that list routinely holds tens of thousands of entries, so a
     * per-tick scan would cost ~20 full traversals per second even with no LootGames board
     * anywhere. The answer only feeds the mark validation and the probe cadence, so a 500 ms TTL is
     * far below the user-visible probe cadence while removing the per-tick cost.
     * </p>
     */
    private static final long BOARD_VIEW_TTL_MS = 500L;

    /** Cached, {@link #BOARD_VIEW_TTL_MS}-bounded answer of {@code bridge.snapshotBoards}. */
    private List<BoardView> boardViews = Collections.emptyList();
    private long boardViewsCheckedAtMs = 0L;
    /** False until the first scan, so the very first tick always scans. */
    private boolean boardViewsKnown = false;

    /**
     * Set by a chain-key re-press (see {@link #resendMarks}); the next tick republishes the
     * validated flag list.
     */
    private boolean resyncPending = false;

    /**
     * How long to leave a freshly dealt board alone before probing it. LootGames sends the board to
     * the client as part of the deal (and the level-end sync can still be in flight right then), so
     * acting instantly means racing those packets.
     */
    private static final long DEAL_SETTLE_MS = 500L;

    /** Number of consecutive ticks a freshly dealt board gets its authoritative state re-pushed. */
    private static final int CLIENT_RESYNC_TICKS = 3;

    /** Signature of the board set seen last tick; see {@link LootGamesMinesweeperBridge#boardSignature}. */
    private String lastBoardSignature = "";

    /** Ticks left of the post-deal client re-sync window (see {@link #DEAL_SETTLE_MS}). */
    private int clientResyncTicks = 0;

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

    /** One probe cycle. Sends {@link PacketMinesweeperMark} if a new mine is found. */
    public void tick(EntityPlayerMP player, UUID playerUUID) {
        // A key re-press asks for a republish, but the packet handler may run on the netty thread
        // (MainThreadEnforcer is config-gated), so it only sets this flag: the world is read here,
        // on the server thread.
        boolean resyncRequested = resyncPending;
        resyncPending = false;

        List<BoardView> views = boardViews(player, resyncRequested);

        // ── A board set that differs from the previous tick means a freshly dealt board (or a
        // completed level, a new attempt, a new game, or the game ending). Two things are needed:
        // • settle briefly before probing, so EZMiner never races the deal's own packets;
        // • re-push the authoritative board state for a few ticks. An auto-solved level ends
        // immediately after the deal, and the level-end sync ("not generated") can then land
        // after the next deal's board packet — the client kept rendering an unopened board
        // while the server's board was generated, and since the server never calls
        // generateBoard() again in that state, the board stayed uninteractable forever.
        String signature = LootGamesMinesweeperBridge.boardSignature(views);
        if (!signature.equals(lastBoardSignature)) {
            lastBoardSignature = signature;
            if (!views.isEmpty()) {
                // This also restarts the probe timer, exactly like the old "no board seen" path
                // did: a freshly dealt board deserves a prompt probe, just not an immediate one.
                nextDetectAtMs = System.currentTimeMillis() + DEAL_SETTLE_MS;
                clientResyncTicks = CLIENT_RESYNC_TICKS;
            }
        }
        if (clientResyncTicks > 0) {
            clientResyncTicks--;
            for (BoardView view : views) {
                bridge.syncClient(view);
            }
        }

        // ── No board is waiting for clicks: the game ended, was destroyed, or is between levels
        // (LootGames nulls the board while a completed level is re-created). Every remembered flag
        // belongs to a board that no longer exists, so drop the whole set.
        if (views.isEmpty()) {
            if (resyncRequested || !marks.isEmpty()) {
                marks.clear();
                EZMiner.network.network.sendTo(new PacketMinesweeperClear(), player);
            }
            // The next game gets an immediate first probe instead of the tail of the previous
            // game's cooldown.
            nextDetectAtMs = 0L;
            return;
        }

        // ── Re-validate the remembered flags against the live board. This is the half that used to
        // leave the orange wireframes "residing" and "drifting": completing a level re-creates the
        // board with a new size, which moves getBoardOrigin() by one block, so a stale world
        // position lands one cell off the next board. A flag the player right-clicked away, or a
        // bomb revealed by an explosion, is caught by the same pass.
        int removed = bridge.invalidateStaleMarks(views, marks);
        if (removed > 0 || resyncRequested) {
            // A key-press resync republishes even when nothing was dropped: the client clears its
            // own list on key release.
            syncMarks(player);
        }

        long now = System.currentTimeMillis();
        if (now < nextDetectAtMs) return;
        long cooldownMs = (long) (Math.max(0.1, Config.minesweeperProbeCooldownSeconds) * 1000.0);
        nextDetectAtMs = now + cooldownMs;
        boolean boardChanged = false;

        // ── Phase 1: flag exactly one bomb per probe.
        Mark marked = bridge.flagNearestBomb(player, views, marks);
        if (marked != null) {
            EZMiner.network.network.sendTo(new PacketMinesweeperMark(marked.x, marked.y, marked.z, cooldownMs), player);
            boardChanged = true;
        }

        // ── Phase 2: only once every bomb carries a flag, open the whole safe side of the board in
        // one go. Until then nothing is revealed, so the flagging phase stays visible and can be
        // followed cell by cell. The reveal normally completes the level (which resets the board),
        // so there is nothing left to do afterwards.
        for (BoardView view : views) {
            if (!bridge.areAllBombsFlagged(view)) continue;
            bridge.revealAllSafeCells(player, view);
            boardChanged = true;
            break;
        }

        // Push the authoritative state after anything changed, which also repairs a client that was
        // left believing the board is still unopened (see syncClient). Skipped once the board is
        // gone: the level-end sync must stand then.
        if (boardChanged) {
            for (BoardView view : views) {
                if (bridge.isStillGenerated(view)) bridge.syncClient(view);
            }
        }
    }

    /**
     * Replaces the client's flag list with the server's validated one. The clear is not optional:
     * the client renders exactly the positions it was last sent, and there is no "remove one mark"
     * packet to retire a stale entry with.
     */
    private void syncMarks(EntityPlayerMP target) {
        EZMiner.network.network.sendTo(new PacketMinesweeperClear(), target);
        long remainingMs = Math.max(0L, nextDetectAtMs - System.currentTimeMillis());
        for (Mark mark : marks) {
            EZMiner.network.network.sendTo(new PacketMinesweeperMark(mark.x, mark.y, mark.z, remainingMs), target);
        }
    }

    public boolean isReady() {
        return System.currentTimeMillis() >= nextDetectAtMs;
    }

    /**
     * Requests that the flagged positions be republished (the player re-pressed the key in
     * minesweeper mode).
     *
     * <p>
     * The work itself is deferred to the next {@link #tick}: this is called from the packet
     * handler, which runs on the netty IO thread whenever {@code Config.enableMainThreadGuard} is
     * off, and the republish needs a world scan. The tick then re-validates the remembered flags
     * against a fresh board snapshot before they are drawn again — probes only run while the key is
     * held, so the board may have been completed or reset while it was released, and a plain resend
     * would resurrect a wireframe that is no longer sitting on a flagged bomb.
     * </p>
     *
     * @param target the player whose client must be republished; kept so the existing
     *               {@link com.czqwq.EZMiner.core.Manager} call shape is unchanged — the consuming tick
     *               uses the same player
     */
    public void resendMarks(EntityPlayerMP target) {
        resyncPending = true;
    }

    /** Full reset on session cleanup. Cooldown preserved across mode switches. */
    public void reset() {
        nextDetectAtMs = 0L;
        marks.clear();
        boardViews = Collections.emptyList();
        boardViewsKnown = false;
        resyncPending = false;
        lastBoardSignature = "";
        clientResyncTicks = 0;
    }
}
