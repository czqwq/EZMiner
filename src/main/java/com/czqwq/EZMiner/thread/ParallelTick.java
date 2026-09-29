package com.czqwq.EZMiner.thread;

import java.util.ArrayList;
import java.util.concurrent.locks.ReentrantLock;

import com.czqwq.EZMiner.EZMiner;

/**
 * Manages pauseable search threads, synchronising their execution with server ticks.
 * Pre-tick tasks run during the server tick; normal tasks run freely every client tick.
 */
public class ParallelTick {

    public ArrayList<Pauseable> preTickTasks = new ArrayList<>();
    public ArrayList<Pauseable> normalTasks = new ArrayList<>();

    public void processPreTickTasks(boolean shouldRun) {
        // removeIf only when there is something to clean: the old stream+collect allocated a
        // pipeline and a temporary list on every server tick end, even with no tasks at all.
        if (!shouldRun && !preTickTasks.isEmpty()) {
            preTickTasks.removeIf(t -> t.stopped.get());
        }
        for (Pauseable task : preTickTasks) {
            if (!task.started.get()) {
                if (shouldRun) task.start();
            } else {
                if (shouldRun) task.unPause();
                else task.pause();
            }
        }
        processNormalTasks();
    }

    public final ReentrantLock normalTaskLock = new ReentrantLock();

    public void processNormalTasks() {
        // Called on every server tick end (via processPreTickTasks) and every client tick start;
        // with no preview task registered there is nothing to do, so skip the lock and the
        // per-call temporary list entirely.
        if (normalTasks.isEmpty()) return;
        if (normalTaskLock.isLocked()) {
            EZMiner.LOG.warn("Normal task lock is blocked.");
            return;
        }
        normalTaskLock.lock();
        try {
            // In-place compaction: preserves order and allocates nothing, unlike the previous
            // "collect stopped tasks into a temp list, then removeAll" pass.
            int w = 0;
            for (int i = 0; i < normalTasks.size(); i++) {
                Pauseable task = normalTasks.get(i);
                if (!task.started.get()) task.start();
                if (!task.stopped.get()) normalTasks.set(w++, task);
            }
            if (w < normalTasks.size()) normalTasks.subList(w, normalTasks.size())
                .clear();
        } finally {
            normalTaskLock.unlock();
        }
    }

    public void addPreServerTickTask(Pauseable task) {
        task.setDaemon(true);
        preTickTasks.add(task);
    }

    public void addNormalTask(Pauseable task) {
        normalTaskLock.lock();
        try {
            task.setDaemon(true);
            normalTasks.add(task);
        } finally {
            normalTaskLock.unlock();
        }
    }

    /**
     * Drops every registered task without unpausing it.
     *
     * <p>
     * Called on server start/stop: this singleton lives for the whole JVM, so a world reload
     * would otherwise carry a still-started founder into the next world, where the tick-START
     * unpause loop could resume it against the previous world (and, after
     * {@code SearchWorkerPool.stop()}, dispatch into a null pool).
     * </p>
     */
    public void clearAllTasks() {
        preTickTasks.clear();
        normalTaskLock.lock();
        try {
            normalTasks.clear();
        } finally {
            normalTaskLock.unlock();
        }
    }
}
