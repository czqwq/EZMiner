package com.czqwq.EZMiner.thread;

import java.util.concurrent.atomic.AtomicBoolean;

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

    public Pauseable() {
        super("EZMiner-Pauseable");
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
    }

    /** Block while paused. Respects thread interrupt. */
    public void waitUntil() {
        while (paused.get()) {
            if (Thread.currentThread()
                .isInterrupted()) return;
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
