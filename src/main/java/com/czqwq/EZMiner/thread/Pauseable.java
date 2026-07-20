package com.czqwq.EZMiner.thread;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.LockSupport;

import com.czqwq.EZMiner.Config;
import com.czqwq.EZMiner.EZMiner;

/**
 * A thread that can be paused and resumed in sync with game ticks.
 */
public class Pauseable extends Thread {

    public AtomicBoolean started = new AtomicBoolean(false);
    public AtomicBoolean stopped = new AtomicBoolean(false);
    public AtomicBoolean paused = new AtomicBoolean(false);
    public AtomicBoolean resumed = new AtomicBoolean(false);
    public int errorCount = 0;

    /**
     * Work budget per yield cycle. 0 or negative = check on every call (legacy
     * per-position behavior). When positive, each {@link #consumeBudget()} call
     * decrements a counter; the pause/interrupt check runs only when the counter
     * reaches zero, then the counter resets.
     */
    protected volatile int workBudget = 0;
    /** Only ever touched by the founder thread itself (see {@link #consumeBudget()}). */
    private int budgetRemaining = 0;

    /**
     * Absolute nanosecond deadline for cooperative yield.
     * When {@link Config#enableBudgetDeadline} is {@code true} and this deadline
     * is exceeded, {@link #waitUntil()} returns even if the thread hasn't been
     * unpaused — providing a safety net against lost unpark signals.
     * Default: {@link Long#MAX_VALUE} (never expires).
     */
    private volatile long deadlineNanos = Long.MAX_VALUE;

    public Pauseable() {
        super("EZMiner-Pauseable");
    }

    /**
     * Sets the per-yield work budget. 0 or negative = pause/interrupt check on
     * every {@link #consumeBudget()} call. Call this before starting the thread.
     */
    public void setWorkBudget(int budget) {
        this.workBudget = Math.max(0, budget);
        this.budgetRemaining = this.workBudget;
    }

    /**
     * Consumes one unit of the work budget and performs the cooperative
     * pause/interrupt check. With {@code workBudget <= 0} (the default) the check
     * runs on every call — the legacy per-position {@code waitUntil()} contract
     * that keeps world reads inside the server-tick window and makes
     * {@code interrupt()} take effect promptly. With a positive budget the check
     * runs every {@code workBudget} calls.
     *
     * <p>
     * Founder-thread only: calls from {@code SearchWorkerPool} workers return
     * {@code true} immediately. Workers are bounded by {@code invokeAll} batch
     * boundaries and must never park on the founder's pause flag — the pool is
     * shared by all players, and {@code budgetRemaining} is deliberately not
     * thread-safe.
     * </p>
     *
     * @return true if work should continue, false if the thread was interrupted
     */
    public boolean consumeBudget() {
        if (Thread.currentThread() != this) {
            // Worker threads: no budget, no parking — but check the pause flag
            // so that tick-end pause() can stop in-flight invokeAll batches early.
            // This is a read-only check on an AtomicBoolean; no park, no lock.
            // Without this, workers inside an invokeAll batch (up to an entire
            // shell layer or 8×124 neighbours) cannot be interrupted until the
            // batch completes, potentially reading the world after the tick ends.
            return !paused.get();
        }
        if (workBudget > 0 && --budgetRemaining > 0) return true;
        budgetRemaining = workBudget;
        waitUntil();
        return !Thread.currentThread()
            .isInterrupted();
    }

    public void pause() {
        if (!started.get()) throw new RuntimeException("Thread not started");
        if (stopped.get()) {
            EZMiner.LOG.error("Thread already stopped! Cannot pause.");
            errorCount++;
            if (errorCount > 10) throw new RuntimeException("Attempted operation on stopped thread 10+ times");
            return;
        }
        paused.set(true);
        resumed.set(false);
    }

    public void unPause() {
        if (!started.get()) throw new RuntimeException("Thread not started");
        if (stopped.get()) {
            EZMiner.LOG.error("Thread already stopped! Cannot resume.");
            errorCount++;
            if (errorCount > 10) throw new RuntimeException("Attempted operation on stopped thread 10+ times");
            return;
        }
        paused.set(false);
        resumed.set(true);
        LockSupport.unpark(this); // immediate wake from parkNanos in waitUntil()
    }

    /**
     * Sets an absolute nanosecond deadline after which {@link #waitUntil()} will return
     * even if the thread hasn't been explicitly unpaused. This is a safety net against
     * lost unpark signals — when {@link Config#enableBudgetDeadline} is {@code false}
     * (default), the deadline is never set and this has no effect.
     *
     * @param nanos absolute deadline in nanoseconds ({@link System#nanoTime()} units)
     */
    public void setDeadlineNanos(long nanos) {
        if (Config.enableBudgetDeadline) {
            this.deadlineNanos = nanos;
        }
    }

    /** Block while paused. Uses parkNanos to yield CPU instead of busy-spinning. */
    public void waitUntil() {
        while (paused.get()) {
            if (Thread.currentThread()
                .isInterrupted()) return;
            // Deadline safety net: yield if the deadline has passed (defense against
            // lost unpark signals). No-op when deadlineNanos == Long.MAX_VALUE.
            long remaining = deadlineNanos - System.nanoTime();
            if (remaining <= 0) return;
            LockSupport.parkNanos(Math.min(1_000_000, remaining)); // 1ms park, yields CPU
        }
    }

    @Override
    public void run() {
        try {
            run1();
        } finally {
            // Always mark stopped, even if run1 throws — otherwise isStopped()
            // stays false forever and the operator waits on a dead founder.
            stopped.set(true);
        }
    }

    public void run1() {}

    @Override
    public synchronized void start() {
        started.set(true);
        super.start();
    }
}
