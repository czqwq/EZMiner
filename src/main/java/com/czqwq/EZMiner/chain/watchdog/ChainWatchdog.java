package com.czqwq.EZMiner.chain.watchdog;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.server.MinecraftServer;

import com.czqwq.EZMiner.Config;

import cpw.mods.fml.common.FMLCommonHandler;

/**
 * Tick-based watchdog for chain operations.
 *
 * <p>
 * EZMiner already has a wall-clock idle timeout in {@code BaseOperator} (configurable
 * via {@code chainIdleTimeoutSeconds}/{@code chainIdleCountdownSeconds}), but wall-clock
 * time can be unreliable under server lag — 50 wall-clock seconds may only be 10 ticks
 * if the server TPS is very low.
 * </p>
 *
 * <p>
 * This watchdog adds a tick-based timeout that is immune to server lag:
 * if no block is harvested for {@link Config#chainWatchdogTimeoutTicks} ticks,
 * the chain is force-cancelled regardless of wall-clock time.
 * </p>
 *
 * <p>
 * Gated behind {@link Config#enableChainWatchdog} — when disabled, all methods
 * are no-ops.
 * </p>
 *
 * <p>
 * Tick-based watchdog for chain operations.
 * </p>
 */
public final class ChainWatchdog {

    /** Per-player map: UUID → last-progress server tick. */
    private static final Map<UUID, Long> LAST_PROGRESS_TICK = new ConcurrentHashMap<>();

    private ChainWatchdog() {}

    /**
     * Records that a chain operation started for the given player.
     * Called from {@code BaseOperator.registry()}.
     */
    public static void markChainStarted(UUID playerUUID) {
        if (!Config.enableChainWatchdog) return;
        LAST_PROGRESS_TICK.put(playerUUID, currentServerTick());
    }

    /**
     * Records that the chain made progress (a block was harvested).
     * Called from {@code BaseOperator.markHarvested()}.
     */
    public static void recordProgress(UUID playerUUID) {
        if (!Config.enableChainWatchdog) return;
        LAST_PROGRESS_TICK.put(playerUUID, currentServerTick());
    }

    /**
     * Checks whether the chain for the given player has exceeded the tick-based
     * timeout without progress.
     *
     * @param playerUUID the player's UUID
     * @return {@code true} if the watchdog has fired (chain should be cancelled)
     */
    public static boolean hasTimedOut(UUID playerUUID) {
        if (!Config.enableChainWatchdog) return false;
        Long last = LAST_PROGRESS_TICK.get(playerUUID);
        if (last == null) return false;
        long current = currentServerTick();
        return (current - last) >= Config.chainWatchdogTimeoutTicks;
    }

    /**
     * Removes the player from watchdog tracking. Called when the chain ends.
     */
    public static void remove(UUID playerUUID) {
        LAST_PROGRESS_TICK.remove(playerUUID);
    }

    private static long currentServerTick() {
        try {
            MinecraftServer server = FMLCommonHandler.instance()
                .getMinecraftServerInstance();
            if (server != null) {
                return server.getTickCounter();
            }
        } catch (Exception ignored) {}
        // Fallback: use system time / 50ms as an approximate tick counter.
        return System.currentTimeMillis() / 50;
    }
}
