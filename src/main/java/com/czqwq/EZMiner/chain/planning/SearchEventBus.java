package com.czqwq.EZMiner.chain.planning;

import java.util.Collection;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

import org.joml.Vector3i;

/**
 * Lock-free position bus that decouples founder (producer) from operator (consumer).
 *
 * <p>
 * Replaces {@code LinkedBlockingQueue} with a {@link ConcurrentLinkedQueue} for
 * lock-free {@code offer}/{@code poll} operations. A generation counter enables
 * clean cancellation: when the operator cancels a chain, it increments the generation,
 * and all in-flight founder publishes are silently dropped — no stale positions leak
 * into the next chain.
 * </p>
 *
 * <p>
 * Usage:
 * <ul>
 * <li>Founder threads call {@link #publish(Vector3i, int)} with the current
 * generation — if the generation matches, the position is enqueued.</li>
 * <li>The operator calls {@link #incrementGeneration()} when cancelling a chain,
 * then {@link #clear()} to drain residual positions.</li>
 * <li>The operator drains positions via {@link #poll()} or {@link #drainTo(Collection, int)}.</li>
 * </ul>
 * </p>
 */
public class SearchEventBus {

    private final ConcurrentLinkedQueue<Vector3i> queue = new ConcurrentLinkedQueue<>();
    private final AtomicInteger generation = new AtomicInteger(0);

    /**
     * Publishes a position to the bus. Only succeeds if {@code expectedGen} matches
     * the current generation — stale positions from cancelled chains are dropped.
     *
     * @param pos         the block position
     * @param expectedGen the generation expected by the publisher
     * @return true if the position was enqueued, false if dropped (generation mismatch)
     */
    public boolean publish(Vector3i pos, int expectedGen) {
        if (generation.get() != expectedGen) return false;
        queue.offer(pos);
        return true;
    }

    /**
     * Publishes without generation check. For callers that don't need cancellation
     * gating (e.g. cached chain mode where positions are pre-computed).
     */
    public void publishUnsafe(Vector3i pos) {
        queue.offer(pos);
    }

    /** Polls the next position (non-blocking). Returns null if the queue is empty. */
    public Vector3i poll() {
        return queue.poll();
    }

    /** Drains up to {@code maxElements} positions into the target collection. */
    public int drainTo(Collection<? super Vector3i> target, int maxElements) {
        int count = 0;
        Vector3i pos;
        while (count < maxElements && (pos = queue.poll()) != null) {
            target.add(pos);
            count++;
        }
        return count;
    }

    /** Returns true if the queue is empty. */
    public boolean isEmpty() {
        return queue.isEmpty();
    }

    /** Returns the number of queued positions (O(n) — use for diagnostics only). */
    public int size() {
        return queue.size();
    }

    /** Clears all queued positions. */
    public void clear() {
        queue.clear();
    }

    /** Returns the current generation. */
    public int getGeneration() {
        return generation.get();
    }

    /**
     * Increments the generation counter, invalidating all in-flight publisher
     * operations. Call when cancelling a chain operation.
     *
     * @return the new generation value
     */
    public int incrementGeneration() {
        return generation.incrementAndGet();
    }
}
