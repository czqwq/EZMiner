package com.czqwq.EZMiner.core.founder;

import static org.junit.Assert.*;

import java.util.HashSet;
import java.util.Set;

import org.junit.Test;

/**
 * Tests for the {@code encodePos} coordinate encoding used by
 * {@code BasePositionFounder} — injectivity, boundary values, and y-masking.
 *
 * <p>
 * The encoding implementation is replicated inline to avoid class-loading
 * {@code BasePositionFounder} (which pulls in Minecraft/Forge classes at
 * load time). The inline copy matches the real implementation exactly.
 *
 * <p>
 * Pure logic — no Minecraft dependencies.
 */
public class EncodePosTest {

    /**
     * Inline copy of {@code BasePositionFounder.encodePos(int,int,int)}.
     * Must match the real implementation exactly.
     */
    static long encodePos(int x, int y, int z) {
        return ((long) (x + 30_000_000) << 38) | ((long) (y & 0xFFF) << 26) | (long) (z + 30_000_000);
    }

    // ── Injectivity (单射性) ─────────────────────────────────────────────────

    @Test
    public void differentCoordsProduceDifferentKeys() {
        Set<Long> seen = new HashSet<>();
        int[] values = { -100, -1, 0, 1, 100, 256, 1000, 30_000_000, -30_000_000 };
        for (int x : values) {
            for (int y : values) {
                for (int z : values) {
                    long key = encodePos(x, y, z);
                    assertFalse("Collision at (" + x + "," + y + "," + z + ")", seen.contains(key));
                    seen.add(key);
                }
            }
        }
        assertEquals(9 * 9 * 9, seen.size());
    }

    @Test
    public void injectivityAcrossTypicalMinecraftRange() {
        Set<Long> seen = new HashSet<>();
        for (int x = -3000; x <= 3000; x += 1000) {
            for (int y = 0; y <= 255; y += 64) {
                for (int z = -3000; z <= 3000; z += 1000) {
                    long key = encodePos(x, y, z);
                    assertFalse("Collision at (" + x + "," + y + "," + z + ")", seen.contains(key));
                    seen.add(key);
                }
            }
        }
    }

    @Test
    public void injectivityAtWorldBorder() {
        long key1 = encodePos(29_999_999, 255, 29_999_999);
        long key2 = encodePos(29_999_998, 255, 29_999_999);
        long key3 = encodePos(-29_999_999, 0, -29_999_999);
        long key4 = encodePos(-29_999_998, 0, -29_999_999);
        assertNotEquals(key1, key2);
        assertNotEquals(key3, key4);
        assertNotEquals(key1, key3);
    }

    // ── Boundary values ─────────────────────────────────────────────────────

    @Test
    public void zeroCoordinates() {
        long key = encodePos(0, 0, 0);
        assertTrue(key >= 0);
    }

    @Test
    public void negativeCoordinatesDifferFromPositive() {
        long neg = encodePos(-100, 64, -100);
        long pos = encodePos(100, 64, 100);
        assertNotEquals(neg, pos);
    }

    @Test
    public void originBiasIsConsistent() {
        long key = encodePos(-30_000_000, 0, -30_000_000);
        assertEquals(0L, key);
    }

    // ── Y-axis masking (12 bits, 0–4095) ────────────────────────────────────

    @Test
    public void yAbove4095IsMasked() {
        long key4096 = encodePos(0, 4096, 0);
        long key0 = encodePos(0, 0, 0);
        assertEquals("y=4096 should mask to y=0", key0, key4096);
    }

    @Test
    public void yNegativeIsMaskedBy12Bits() {
        long keyNeg1 = encodePos(0, -1, 0);
        long key4095 = encodePos(0, 4095, 0);
        assertEquals("y=-1 should be equivalent to y=4095 via 12-bit mask", key4095, keyNeg1);
    }

    @Test
    public void yWithinMinecraftBuildHeight() {
        Set<Long> seen = new HashSet<>();
        for (int y = 0; y <= 255; y++) {
            long key = encodePos(0, y, 0);
            assertFalse("Collision at y=" + y, seen.contains(key));
            seen.add(key);
        }
        assertEquals(256, seen.size());
    }

    // ── X/Z beyond the 26-bit range ─────────────────────────────────────────

    @Test
    public void xAtMaxSafeRange() {
        long k1 = encodePos(30_000_000, 64, 0);
        long k2 = encodePos(0, 64, 30_000_000);
        assertNotEquals(k1, k2);
    }

    // ── Determinism ─────────────────────────────────────────────────────────

    @Test
    public void sameInputsProduceSameKey() {
        long k1 = encodePos(12345, 200, -6789);
        long k2 = encodePos(12345, 200, -6789);
        assertEquals(k1, k2);
    }
}
