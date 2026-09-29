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
import com.czqwq.EZMiner.chain.network.PacketMinesweeperClear;
import com.czqwq.EZMiner.chain.network.PacketMinesweeperMark;

/**
 * Per-player minesweeper special-mode state and probe logic.
 */
public class MinesweeperModeHandler {

    private final LootGamesMinesweeperBridge bridge = new LootGamesMinesweeperBridge();
    private final Set<String> detectedBombs = new HashSet<>();
    private final List<Vector3i> detectedPositions = new ArrayList<>();
    private long nextDetectAtMs = 0L;
    /** True when a LootGames game was active last probe cycle — for game-end cleanup. */
    private boolean wasGameActive = false;

    /**
     * How long an "is a board active?" answer is trusted, in milliseconds.
     *
     * <p>
     * {@code isAnyGameActive} copies the entire {@code world.loadedTileEntityList} and reflects
     * over every element. On a GTNH server that list routinely holds tens of thousands of
     * entries, and the old code called it once per server tick <em>before</em> consulting the
     * probe cooldown, so holding the chain key in minesweeper mode cost 20 full traversals per
     * second even with no LootGames board anywhere. The answer only gates start/stop/cleanup,
     * so a 500 ms TTL is far below the user-visible probe cadence while removing the per-tick
     * cost.
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

    /** One probe cycle. Sends {@link PacketMinesweeperMark} if a new mine is found. */
    public void tick(EntityPlayerMP player, UUID playerUUID) {
        // ── Detect game-end transitions: if a LootGames game was active last probe but no
        // game is in StageWaiting now, the game just ended — clear all stale marks so
        // the next game's scan starts fresh.
        boolean gameActive = isGameActiveCached(player);
        if (wasGameActive && !gameActive && !detectedBombs.isEmpty()) {
            reset();
            EZMiner.network.network.sendTo(new PacketMinesweeperClear(), player);
        }
        wasGameActive = gameActive;

        if (!gameActive) return;

        long now = System.currentTimeMillis();
        if (now < nextDetectAtMs) return;
        long cooldownMs = (long) (Math.max(0.1, Config.minesweeperProbeCooldownSeconds) * 1000.0);
        nextDetectAtMs = now + cooldownMs;
        Vector3i flaggedPos = bridge.detectNearestBomb(player, detectedBombs);
        if (flaggedPos != null) {
            detectedPositions.add(flaggedPos);
            EZMiner.network.network
                .sendTo(new PacketMinesweeperMark(flaggedPos.x, flaggedPos.y, flaggedPos.z, cooldownMs), player);
        }
    }

    public boolean isReady() {
        return System.currentTimeMillis() >= nextDetectAtMs;
    }

    /** Re-send all flagged positions (player re-pressed key in minesweeper mode). */
    public void resendMarks(EntityPlayerMP target) {
        if (detectedPositions.isEmpty()) return;
        long remainingMs = Math.max(0L, nextDetectAtMs - System.currentTimeMillis());
        for (Vector3i pos : detectedPositions) {
            EZMiner.network.network.sendTo(new PacketMinesweeperMark(pos.x, pos.y, pos.z, remainingMs), target);
        }
    }

    /** Full reset on session cleanup. Cooldown preserved across mode switches. */
    public void reset() {
        nextDetectAtMs = 0L;
        detectedBombs.clear();
        detectedPositions.clear();
        wasGameActive = false;
        gameActiveKnown = false;
        gameActiveCached = false;
    }
}
