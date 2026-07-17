package com.czqwq.EZMiner.utils;

import java.util.Arrays;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Primitive {@code long}-keyed open-addressing hash set for visited-position tracking.
 *
 * <p>
 * Replaces {@code ConcurrentHashMap.newKeySet()} to avoid boxing every {@code long}
 * coordinate key into a {@code Long} object. Uses linear probing with a 0.5 load
 * factor and a {@link ReadWriteLock} for thread safety — reads (contains) are
 * concurrent, writes (add) are exclusive.
 * </p>
 *
 * <p>
 * The sentinel value 0 is reserved for the empty slot. Callers must ensure that
 * valid keys are never 0 (EZMiner's {@code encodePos} guarantees this via the
 * +30M coordinate bias).
 * </p>
 */
public class LongOpenHashSet {

    private static final long EMPTY = 0L;
    private static final float LOAD_FACTOR = 0.5f;

    private long[] keys;
    private int size;
    private int mask;
    private final ReadWriteLock lock = new ReentrantReadWriteLock();

    /**
     * Creates a set with capacity for at least {@code expectedSize} entries.
     * The actual capacity is the next power of two above
     * {@code expectedSize / LOAD_FACTOR}.
     */
    public LongOpenHashSet(int expectedSize) {
        int cap = Math.max(16, roundUpPowerOfTwo((int) (expectedSize / LOAD_FACTOR)));
        this.keys = new long[cap];
        this.mask = cap - 1;
        Arrays.fill(keys, EMPTY);
    }

    /**
     * Checks whether the set contains the given key. Lock-free read — may
     * briefly miss a concurrent addition but never returns false positives.
     */
    public boolean contains(long key) {
        if (key == EMPTY) throw new IllegalArgumentException("key must not be 0");
        lock.readLock()
            .lock();
        try {
            int idx = hash(key) & mask;
            while (true) {
                long k = keys[idx];
                if (k == key) return true;
                if (k == EMPTY) return false;
                idx = (idx + 1) & mask;
            }
        } finally {
            lock.readLock()
                .unlock();
        }
    }

    /**
     * Adds a key to the set. Returns true if the key was added (not already present).
     * Thread-safe via exclusive write lock.
     */
    public boolean add(long key) {
        if (key == EMPTY) throw new IllegalArgumentException("key must not be 0");
        lock.writeLock()
            .lock();
        try {
            if (size >= keys.length * LOAD_FACTOR) {
                rehash();
            }
            int idx = hash(key) & mask;
            while (true) {
                long k = keys[idx];
                if (k == key) return false; // already present
                if (k == EMPTY) {
                    keys[idx] = key;
                    size++;
                    return true;
                }
                idx = (idx + 1) & mask;
            }
        } finally {
            lock.writeLock()
                .unlock();
        }
    }

    public int size() {
        lock.readLock()
            .lock();
        try {
            return size;
        } finally {
            lock.readLock()
                .unlock();
        }
    }

    public boolean isEmpty() {
        return size() == 0;
    }

    private void rehash() {
        long[] old = keys;
        int newCap = old.length * 2;
        keys = new long[newCap];
        mask = newCap - 1;
        Arrays.fill(keys, EMPTY);
        size = 0;
        for (long k : old) {
            if (k != EMPTY) {
                addRebuilding(k);
            }
        }
    }

    /** Unchecked add during rehash (lock already held). */
    private void addRebuilding(long key) {
        int idx = hash(key) & mask;
        while (keys[idx] != EMPTY) {
            idx = (idx + 1) & mask;
        }
        keys[idx] = key;
        size++;
    }

    private static int hash(long key) {
        // Murmur3-style finalizer for good distribution
        long h = key ^ (key >>> 33);
        h *= 0xff51afd7ed558ccdL;
        h ^= (h >>> 33);
        h *= 0xc4ceb9fe1a85ec53L;
        h ^= (h >>> 33);
        return (int) h;
    }

    private static int roundUpPowerOfTwo(int n) {
        if (n <= 0) return 16;
        n--;
        n |= n >> 1;
        n |= n >> 2;
        n |= n >> 4;
        n |= n >> 8;
        n |= n >> 16;
        return n + 1;
    }
}
