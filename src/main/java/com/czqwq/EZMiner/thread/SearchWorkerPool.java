package com.czqwq.EZMiner.thread;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Shared daemon thread pool for parallel block-search workers. Created on server
 * start, shut down on server stop. Threads are named {@code EZMiner-SearchWorker-N}
 * so Hodgepodge logs at most one off-thread warning per worker.
 */
public final class SearchWorkerPool {

    private static final Logger LOG = LogManager.getLogger("EZMiner-SearchPool");
    private static ExecutorService executor;

    private SearchWorkerPool() {}

    public static ExecutorService get() {
        return executor;
    }

    /** Start the pool with {@code workerCount} daemon threads. Idempotent. */
    public static void start(int workerCount) {
        if (executor != null && !executor.isShutdown()) return;
        final int n = Math.max(1, workerCount);
        final AtomicInteger seq = new AtomicInteger(0);
        executor = Executors.newFixedThreadPool(n, r -> {
            Thread t = new Thread(r, "EZMiner-SearchWorker-" + seq.getAndIncrement());
            t.setDaemon(true);
            return t;
        });
        LOG.info("Search worker pool started with {} threads", n);
    }

    /** Shut down the pool immediately. */
    public static void stop() {
        if (executor == null) return;
        executor.shutdownNow();
        executor = null;
        LOG.info("Search worker pool shut down");
    }
}
