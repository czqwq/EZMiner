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

    /**
     * Earliest time at which a freshly reset (not generated) board may be opened by the assistant.
     *
     * <p>
     * A detonation ends with LootGames switching back to {@code StageWaiting} and nulling the
     * board; only a player's left click re-generates it. Waiting one {@link #DEAL_SETTLE_MS} after
     * the board transition keeps EZMiner's {@code generateBoard} call from racing the reset
     * packets LootGames is still sending to the client.
     * </p>
     */
    private long openBoardAllowedAtMs = 0L;

    /**
     * Retry cadence when the auto-open call is unavailable or failed. Prevents a broken reflection
     * handle from retrying on every server tick.
     */
    private static final long AUTO_OPEN_RETRY_MS = 1000L;

    /**
     * Boards further away than this are never auto-opened: the helper already mutates every loaded
     * board's marks, but opening a board is a real game action, so it stays local to the player.
     */
    private static final double AUTO_OPEN_MAX_DISTANCE_SQ = 64.0 * 64.0;

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
        long now = System.currentTimeMillis();

        // ── A board set that differs from the previous tick means a freshly dealt board (or a
        // completed level, a new attempt, a new game, a board reset by a detonation, or the game
        // ending). Three things are needed:
        // • settle briefly before probing/opening, so EZMiner never races the deal's own packets;
        // • re-push the authoritative board state for a few ticks. An auto-solved level ends
        // immediately after the deal, and the level-end sync ("not generated") can then land
        // after the next deal's board packet — the client kept rendering an unopened board
        // while the server's board was generated, and since the server never calls
        // generateBoard() again in that state, the board stayed uninteractable forever.
        // • delay the auto-open hook by the same settle time.
        String signature = LootGamesMinesweeperBridge.boardSignature(views);
        if (!signature.equals(lastBoardSignature)) {
            lastBoardSignature = signature;
            if (!views.isEmpty()) {
                // This also restarts the probe timer, exactly like the old "no board seen" path
                // did: a freshly dealt board deserves a prompt probe, just not an immediate one.
                nextDetectAtMs = now + DEAL_SETTLE_MS;
                openBoardAllowedAtMs = now + DEAL_SETTLE_MS;
                clientResyncTicks = CLIENT_RESYNC_TICKS;
            }
        }
        if (clientResyncTicks > 0) {
            clientResyncTicks--;
            for (BoardView view : views) {
                if (view.generated) bridge.syncClient(view);
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
            openBoardAllowedAtMs = 0L;
            return;
        }

        // ── Auto-open a board that LootGames reset. A detonation ends with StageExploding
        // switching back to StageWaiting and calling MSBoard.resetBoard(), which only nulls the
        // grid: the next board is generated inside StageWaiting.onClick, i.e. by a player's left
        // click. Without performing that click ourselves the bridge only sees a board with no
        // bombs, so the assistant would show READY and do nothing even while the key is held.
        if (now >= openBoardAllowedAtMs) {
            boolean opened = false;
            boolean attempted = false;
            for (BoardView view : views) {
                if (view.generated) continue;
                if (player.getDistanceSq(view.originX + 0.5, view.originY + 0.5, view.originZ + 0.5)
                    > AUTO_OPEN_MAX_DISTANCE_SQ) continue;
                attempted = true;
                opened |= bridge.openBoard(player, view);
            }
            if (opened) {
                // The cached snapshot predates the open: re-scan on the next tick and let the fresh
                // board settle before the first flag.
                boardViewsKnown = false;
                nextDetectAtMs = now + DEAL_SETTLE_MS;
                openBoardAllowedAtMs = now + DEAL_SETTLE_MS;
                clientResyncTicks = CLIENT_RESYNC_TICKS;
                // The remembered marks belong to the previous board; drop them now so the client
                // never draws the old boxes over the fresh board.
                if (bridge.invalidateStaleMarks(views, marks) > 0) syncMarks(player);
                return;
            }
            if (attempted) {
                // The reflected call failed (or the hook is missing): retry at a bounded cadence
                // instead of on every server tick.
                openBoardAllowedAtMs = now + AUTO_OPEN_RETRY_MS;
            }
        }

        // ── Only a generated board carries bombs; a reset board is waiting for the click above.
        List<BoardView> generated = new ArrayList<>(views.size());
        for (BoardView view : views) {
            if (view.generated) generated.add(view);
        }

        // ── Re-validate the remembered flags against the live board. This is the half that used to
        // leave the orange wireframes "residing" and "drifting": completing a level re-creates the
        // board with a new size, which moves getBoardOrigin() by one block, so a stale world
        // position lands one cell off the next board. A flag the player right-clicked away, a bomb
        // revealed by an explosion, or a flag on a board that was just reset is caught by the same
        // pass.
        int removed = bridge.invalidateStaleMarks(views, marks);
        if (removed > 0 || resyncRequested) {
            // A key-press resync republishes even when nothing was dropped: the client clears its
            // own list on key release.
            syncMarks(player);
        }

        if (generated.isEmpty()) return;
        if (now < nextDetectAtMs) return;
        long cooldownMs = (long) (Math.max(0.1, Config.minesweeperProbeCooldownSeconds) * 1000.0);
        nextDetectAtMs = now + cooldownMs;
        boolean boardChanged = false;

        // ── Phase 1: flag exactly one bomb per probe.
        Mark marked = bridge.flagNearestBomb(player, generated, marks);
        if (marked != null) {
            EZMiner.network.network.sendTo(new PacketMinesweeperMark(marked.x, marked.y, marked.z, cooldownMs), player);
            boardChanged = true;
        }

        // ── Phase 2: only once every bomb carries a flag, open the whole safe side of the board in
        // one go. Until then nothing is revealed, so the flagging phase stays visible and can be
        // followed cell by cell. The reveal normally completes the level (which resets the board),
        // so there is nothing left to do afterwards.
        for (BoardView view : generated) {
            if (!bridge.areAllBombsFlagged(view)) continue;
            bridge.revealAllSafeCells(player, view);
            boardChanged = true;
            break;
        }

        // Push the authoritative state after anything changed, which also repairs a client that was
        // left believing the board is still unopened (see syncClient). Skipped once the board is
        // gone: the level-end sync must stand then.
        if (boardChanged) {
            for (BoardView view : generated) {
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
        openBoardAllowedAtMs = 0L;
    }
}
