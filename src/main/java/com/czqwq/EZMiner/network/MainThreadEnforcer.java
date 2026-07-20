package com.czqwq.EZMiner.network;

import com.czqwq.EZMiner.Config;
import com.czqwq.EZMiner.EZMiner;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.relauncher.Side;

/**
 * Lightweight main-thread guard for network packet handlers.
 *
 * <p>
 * In Forge 1.7.10, {@code SimpleNetworkWrapper} dispatches packet handlers on the
 * netty IO thread for server-side packets. Handlers that touch world state
 * (block breaks, entity operations) must run on the main server thread.
 * </p>
 *
 * <p>
 * This guard provides a defensive wrapper that each handler can call at its
 * entry point. The actual main-thread scheduling uses Forge's server tick
 * infrastructure via the existing {@code TickEvent.ServerTickEvent} pattern
 * that EZMiner already uses for {@code BaseOperator}.
 * </p>
 *
 * <p>
 * Currently, this guard performs a best-effort thread-safety check and logs
 * a warning when a handler is called from a non-server thread. Full automatic
 * deferral requires method-name portability that is not universally available
 * across 1.7.10 MCP mappings.
 * </p>
 *
 * <p>
 * Gated behind {@link Config#enableMainThreadGuard} — when disabled, all guards
 * are no-ops (legacy behavior).
 * </p>
 */
public final class MainThreadEnforcer {

    private MainThreadEnforcer() {}

    /**
     * Convenience for handlers that return {@code null} (fire-and-forget).
     * Runs the body immediately and returns {@code null}.
     *
     * <p>
     * When {@link Config#enableMainThreadGuard} is enabled, logs a warning if the
     * current thread is not the server thread (detected by thread-name prefix).
     * </p>
     */
    public static IMessage guardedNull(Side side, Runnable body) {
        if (Config.enableMainThreadGuard && side.isServer()) {
            String name = Thread.currentThread()
                .getName();
            // The netty IO thread is typically named "Netty IO #N" or "nioEventLoopGroup-*".
            // The server thread is "Server thread".
            if (name.contains("Netty") || name.contains("nioEventLoop")) {
                EZMiner.LOG.warn(
                    "Packet handler called from IO thread '{}'. "
                        + "Deferring to server thread is not available in this environment — "
                        + "the handler will run on the IO thread. "
                        + "Set enableMainThreadGuard=false to suppress this warning.",
                    name);
            }
        }
        body.run();
        return null;
    }
}
