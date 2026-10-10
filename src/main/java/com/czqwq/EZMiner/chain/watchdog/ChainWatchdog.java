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
     * Arms the watchdog for the given player.
     *
     * <p>
     * <strong>Not called from {@code registry()} any more.</strong> Arming at chain start made the
     * timeout fire while the founder was still legitimately searching (a big-radius blast/ore/log
     * sweep, or a chunk-load stall), because the check sits before the empty-queue early return in
     * {@code BaseOperator}: the shipped default is 100 ticks (5 s), so any chain whose first
     * candidate took longer than that was force-cancelled even though it was making progress.
     * The timer is now armed by the first real harvest ({@link #recordProgress}) and by the planner
     * when it enqueues candidates, so "no progress" means exactly that.
     * </p>
     *
     * <p>
     * Retained (and still safe to call) for callers that want to pre-arm a timer explicitly.
     * </p>
     */
    public static void markChainStarted(UUID playerUUID) {
        if (!Config.enableChainWatchdog) return;
        long tick = currentServerTick();
        if (tick < 0) return; // server unavailable: do not seed a bogus base
        LAST_PROGRESS_TICK.put(playerUUID, tick);
    }

    /**
     * Records that the chain made progress (a block was harvested, or the planner enqueued new
     * candidates). Called from {@code BaseOperator.markHarvested()} and from the enqueue path.
     */
    public static void recordProgress(UUID playerUUID) {
        if (!Config.enableChainWatchdog) return;
        long tick = currentServerTick();
        if (tick < 0) return;
        LAST_PROGRESS_TICK.put(playerUUID, tick);
    }

    /**
     * Checks whether the chain for the given player has exceeded the tick-based
     * timeout without progress.
     *
     * <p>
     * A player with no recorded progress is <strong>not</strong> timed out: the timer only starts at
     * the first harvest (see {@link #markChainStarted}), so "not yet armed" must not be read as
     * "stalled since tick 0".
     * </p>
     *
     * @param playerUUID the player's UUID
     * @return {@code true} if the watchdog has fired (chain should be cancelled)
     */
    public static boolean hasTimedOut(UUID playerUUID) {
        if (!Config.enableChainWatchdog) return false;
        Long last = LAST_PROGRESS_TICK.get(playerUUID);
        if (last == null) return false;
        long current = currentServerTick();
        // A negative tick means "server instance unavailable"; comparing it against a real tick
        // would produce a huge delta and a spurious timeout, which is why both writers refuse to
        // store one and this read refuses to compare against one.
        if (current < 0) return false;
        return (current - last) >= Config.chainWatchdogTimeoutTicks;
    }

    /**
     * Removes the player from watchdog tracking. Called when the chain ends.
     */
    public static void remove(UUID playerUUID) {
        LAST_PROGRESS_TICK.remove(playerUUID);
    }

    /**
     * Current server tick, or {@code -1} when no server instance is available.
     *
     * <p>
     * The old body fell back to {@code System.currentTimeMillis() / 50} (≈3.5e10), a completely
     * different base from {@code MinecraftServer.getTickCounter()} (a few thousand). Mixing the two
     * made {@code current - last} explode, so a transiently unavailable server caused a spurious
     * timeout and a cancelled chain. Returning a sentinel instead lets every caller refuse to
     * compare rather than compare nonsense.
     * </p>
     */
    private static long currentServerTick() {
        try {
            MinecraftServer server = FMLCommonHandler.instance()
                .getMinecraftServerInstance();
            if (server != null) {
                return server.getTickCounter();
            }
        } catch (Exception ignored) {}
        return -1L;
    }
}
