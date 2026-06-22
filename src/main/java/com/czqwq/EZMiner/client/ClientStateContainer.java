package com.czqwq.EZMiner.client;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.joml.Vector3i;

import com.czqwq.EZMiner.chain.state.ChainClientState;
import com.czqwq.EZMiner.core.MinerModeState;

/** Holds client-side view of the player's current mining mode and chain state. */
public class ClientStateContainer {

    public MinerModeState minerModeState = new MinerModeState();
    public ChainClientState chainClientState = new ChainClientState();
    /** Number of blocks mined in the current chain operation. Updated by runtime sync packets. */
    public volatile int chainedBlockCount = 0;
    /** Elapsed time in milliseconds of the current chain operation. Updated by runtime sync packets. */
    public volatile long chainElapsedMs = 0L;
    /** Number of blocks currently rendered in preview on the client. */
    public volatile int previewRenderedCount = 0;

    /**
     * World positions of minesweeper bombs that have been flagged in the current session.
     * Written by the Netty IO thread (packet handler), read by the render thread; thread-safe via
     * {@link CopyOnWriteArrayList}.
     */
    public final List<Vector3i> minesweeperFlaggedPositions = new CopyOnWriteArrayList<>();

    /**
     * Monotonically increasing counter bumped whenever {@link #minesweeperFlaggedPositions} changes.
     * {@code volatile} so the render thread always sees the latest value.
     */
    public volatile int minesweeperFlaggedVersion = 0;

    /**
     * Client-side timestamp (epoch ms) when the minesweeper probe cooldown expires.
     * Updated each time a {@link com.czqwq.EZMiner.chain.network.PacketMinesweeperMark} is received.
     * The HUD computes {@code remaining = max(0, minesweeperNextProbeClientMs - now)}.
     */
    public volatile long minesweeperNextProbeClientMs = 0L;
    /**
     * Client-side timestamp (epoch ms) when the Sudoku probe cooldown expires.
     * Updated each time a {@link com.czqwq.EZMiner.chain.network.PacketSudokuFill} is received.
     * Independent from {@link #minesweeperNextProbeClientMs} so the two modes don't interfere.
     */
    public volatile long sudokuNextProbeClientMs = 0L;

    /** Adds a newly-flagged mine position and bumps the version counter. */
    public void addMinesweeperMark(Vector3i pos) {
        minesweeperFlaggedPositions.add(pos);
        minesweeperFlaggedVersion++;
    }

    /** Clears all flagged mine positions and bumps the version counter. */
    public void clearMinesweeperMarks() {
        minesweeperFlaggedPositions.clear();
        minesweeperFlaggedVersion++;
    }

    // ── Sudoku assistant state ──────────────────────────────────────────────

    /**
     * World positions (board origins) of Sudoku boards where cells were filled
     * in the current session. Written by the Netty IO thread, read by the render
     * thread; thread-safe via {@link CopyOnWriteArrayList}.
     */
    public final List<Vector3i> sudokuFilledPositions = new CopyOnWriteArrayList<>();

    /** Monotonically increasing counter bumped whenever {@link #sudokuFilledPositions} changes. */
    public volatile int sudokuFilledVersion = 0;

    /** Adds a newly-filled Sudoku cell position and bumps the version counter. */
    public void addSudokuFill(Vector3i pos) {
        sudokuFilledPositions.add(pos);
        sudokuFilledVersion++;
    }

    /** Clears all Sudoku filled-cell positions and bumps the version counter. */
    public void clearSudokuFills() {
        sudokuFilledPositions.clear();
        sudokuFilledVersion++;
    }
}
