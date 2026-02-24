package com.czqwq.EZMiner.thread;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

import com.czqwq.EZMiner.EZMiner;

/**
 * Manages pauseable search threads, synchronising their execution with server ticks.
 * Pre-tick tasks run during the server tick; normal tasks run freely every client tick.
 */
public class ParallelTick {

    public ArrayList<Pauseable> preTickTasks = new ArrayList<>();
    public ArrayList<Pauseable> normalTasks = new ArrayList<>();

    public void processPreTickTasks(boolean shouldRun) {
        if (!shouldRun) {
            List<Pauseable> done = preTickTasks.stream()
                .filter(t -> t.stopped.get())
                .collect(Collectors.toList());
            preTickTasks.removeAll(done);
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
        if (normalTaskLock.isLocked()) {
            EZMiner.LOG.warn("Normal task lock is blocked.");
            return;
        }
        normalTaskLock.lock();
        try {
            ArrayList<Pauseable> done = new ArrayList<>();
            for (Pauseable task : normalTasks) {
                if (!task.started.get()) task.start();
                if (task.stopped.get()) done.add(task);
            }
            normalTasks.removeAll(done);
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
}
