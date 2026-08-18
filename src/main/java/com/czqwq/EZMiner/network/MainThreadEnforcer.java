package com.czqwq.EZMiner.network;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

import com.czqwq.EZMiner.Config;
import com.czqwq.EZMiner.EZMiner;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.relauncher.Side;

/**
 * Main-thread guard for network packet handlers.
 *
 * <p>
 * In Forge 1.7.10, {@code SimpleNetworkWrapper} dispatches server-side packet
 * handlers on the netty IO thread. Handlers that touch world / per-player state
 * must run on the server thread. This guard defers such handlers into a small
 * FIFO queue that {@code PlayerManager.onServerTick} drains once per server
 * tick (on the server thread) — the same tick-synchronisation primitive the mod
 * already uses for {@code BaseOperator} / {@code ParallelTick}.
 *
 * <p>
 * Gated behind {@link Config#enableMainThreadGuard} — when disabled all guards
 * are no-ops (legacy behavior: handlers run immediately on the IO thread).
 */
public final class MainThreadEnforcer {

    private MainThreadEnforcer() {}

    /** FIFO of server-state-mutating handlers waiting for the next server tick. */
    private static final Queue<Runnable> DEFERRED = new ConcurrentLinkedQueue<>();
    /** Max handlers drained per server tick, so a burst can't stall the server thread too long. */
    private static final int MAX_DRAIN_PER_TICK = 1024;

    /** True when the current thread is a netty event-loop (IO) thread. */
    private static boolean isOnIoThread() {
        String name = Thread.currentThread()
            .getName();
        return name.contains("Netty") || name.contains("nioEventLoop");
    }

    /**
     * Convenience for handlers that return {@code null} (fire-and-forget).
     *
     * <p>
     * When {@link Config#enableMainThreadGuard} is enabled and the handler runs on
     * an IO thread, the body is deferred to the next server tick (returning
     * {@code null} immediately). On the server thread, or with the guard disabled,
     * the body runs immediately.
     */
    public static IMessage guardedNull(Side side, Runnable body) {
        if (Config.enableMainThreadGuard && side.isServer() && isOnIoThread()) {
            DEFERRED.offer(body);
            if (EZMiner.LOG.isDebugEnabled()) {
                EZMiner.LOG.debug(
                    "Deferred packet handler from IO thread '{}' to the next server tick.",
                    Thread.currentThread()
                        .getName());
            }
            return null;
        }
        body.run();
        return null;
    }

    /**
     * Runs up to {@link #MAX_DRAIN_PER_TICK} deferred handlers on the server thread.
     * Called once per server tick from {@code PlayerManager.onServerTick}.
     */
    public static void drainDeferred() {
        int drained = 0;
        Runnable task;
        while (drained < MAX_DRAIN_PER_TICK && (task = DEFERRED.poll()) != null) {
            try {
                task.run();
            } catch (Exception e) {
                EZMiner.LOG.error("Error while running a deferred server-thread packet handler", e);
            }
            drained++;
        }
    }
}
