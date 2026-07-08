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

    /** One probe cycle. Sends {@link PacketMinesweeperMark} if a new mine is found. */
    public void tick(EntityPlayerMP player, UUID playerUUID) {
        // ── Detect game-end transitions: if a LootGames game was active last probe but no
        // game is in StageWaiting now, the game just ended — clear all stale marks so
        // the next game's scan starts fresh.
        boolean gameActive = bridge.isAnyGameActive(player.worldObj);
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
    }
}
