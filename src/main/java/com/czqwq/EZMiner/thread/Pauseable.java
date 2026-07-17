package com.czqwq.EZMiner.thread;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.LockSupport;

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
     * Work budget per yield cycle. 0 or negative = disabled (no budget cap).
     * When positive, each {@link #consumeBudget()} call decrements a counter;
     * when the counter reaches zero the thread yields via {@link #waitUntil()}
     * and the counter resets.
     */
    protected volatile int workBudget = 0;
    private int budgetRemaining = 0;

    public Pauseable() {
        super("EZMiner-Pauseable");
    }

    /**
     * Sets the per-yield work budget. 0 or negative disables budget-based yielding.
     * Call this before starting the thread.
     */
    public void setWorkBudget(int budget) {
        this.workBudget = Math.max(0, budget);
        this.budgetRemaining = this.workBudget;
    }

    /**
     * Consumes one unit of the work budget. When the budget is exhausted the
     * thread calls {@link #waitUntil()} (which parks if paused) and resets.
     *
     * @return true if work should continue, false if the thread was interrupted
     */
    public boolean consumeBudget() {
        if (workBudget <= 0) return true;
        if (--budgetRemaining <= 0) {
            budgetRemaining = workBudget;
            waitUntil();
            if (Thread.currentThread()
                .isInterrupted()) return false;
        }
        return true;
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

    /** Block while paused. Uses parkNanos to yield CPU instead of busy-spinning. */
    public void waitUntil() {
        while (paused.get()) {
            if (Thread.currentThread()
                .isInterrupted()) return;
            LockSupport.parkNanos(1_000_000); // 1ms park, yields CPU
        }
    }

    @Override
    public void run() {
        run1();
        stopped.set(true);
    }

    public void run1() {}

    @Override
    public synchronized void start() {
        started.set(true);
        super.start();
    }
}
