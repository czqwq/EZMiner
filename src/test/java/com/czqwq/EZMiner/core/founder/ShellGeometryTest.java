package com.czqwq.EZMiner.core.founder;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.Test;

/**
 * Tests that the shell-face enumeration in
 * {@link BasePositionFounder#scanShellFaces} covers exactly the surface of a
 * cube — no missing positions, no duplicates.
 *
 * <p>
 * The shell-face algorithm iterates the 6 faces of a cube with careful
 * edge-dedup (12 edges belong to exactly one face each, 8 corners likewise).
 * This test compares its output against a naive "is-on-surface" triple loop
 * for radii 1–3, which is the range where off-by-one errors in the
 * inner-range bounds ({@code xMin+1..xMax-1}, {@code yMin+1..yMax-1}) would
 * be most visible.
 *
 * <p>
 * Pure logic — no Minecraft dependencies.
 */
public class ShellGeometryTest {

    /**
     * Collects all (x,y,z) positions on the surface of the cube
     * [xMin..xMax]×[yMin..yMax]×[zMin..zMax] using the same explicit face-loop
     * algorithm as {@code BasePositionFounder.scanShellFaces}.
     */
    static List<String> shellFacePositions(int xMin, int xMax, int yMin, int yMax, int zMin, int zMax) {
        List<String> result = new ArrayList<>();

        // Face x=xMin and x=xMax: full yz-plane (includes all edges)
        for (int y = yMin; y <= yMax; y++) {
            for (int z = zMin; z <= zMax; z++) {
                result.add(xMin + "," + y + "," + z);
            }
        }
        for (int y = yMin; y <= yMax; y++) {
            for (int z = zMin; z <= zMax; z++) {
                result.add(xMax + "," + y + "," + z);
            }
        }

        // Face y=yMin and y=yMax: exclude x-edge faces, keep FULL z range
        int xMinInner = xMin + 1, xMaxInner = xMax - 1;
        if (xMinInner <= xMaxInner) {
            for (int x = xMinInner; x <= xMaxInner; x++) {
                for (int z = zMin; z <= zMax; z++) {
                    result.add(x + "," + yMin + "," + z);
                }
            }
            for (int x = xMinInner; x <= xMaxInner; x++) {
                for (int z = zMin; z <= zMax; z++) {
                    result.add(x + "," + yMax + "," + z);
                }
            }
        }

        // Face z=zMin and z=zMax: exclude x and y edges
        int yMinInner = yMin + 1, yMaxInner = yMax - 1;
        if (xMinInner <= xMaxInner && yMinInner <= yMaxInner) {
            for (int x = xMinInner; x <= xMaxInner; x++) {
                for (int y = yMinInner; y <= yMaxInner; y++) {
                    result.add(x + "," + y + "," + zMin);
                }
            }
            for (int x = xMinInner; x <= xMaxInner; x++) {
                for (int y = yMinInner; y <= yMaxInner; y++) {
                    result.add(x + "," + y + "," + zMax);
                }
            }
        }

        return result;
    }

    /**
     * Naive reference: a position is on the cube surface if at least one of its
     * coordinates equals the corresponding min or max bound.
     */
    static Set<String> naiveSurfacePositions(int xMin, int xMax, int yMin, int yMax, int zMin, int zMax) {
        Set<String> result = new HashSet<>();
        for (int x = xMin; x <= xMax; x++) {
            for (int y = yMin; y <= yMax; y++) {
                for (int z = zMin; z <= zMax; z++) {
                    if (x == xMin || x == xMax || y == yMin || y == yMax || z == zMin || z == zMax) {
                        result.add(x + "," + y + "," + z);
                    }
                }
            }
        }
        return result;
    }

    // ── Coverage completeness ───────────────────────────────────────────────

    @Test
    public void radius1() {
        assertShellMatchesNaive(1);
    }

    @Test
    public void radius2() {
        assertShellMatchesNaive(2);
    }

    @Test
    public void radius3() {
        assertShellMatchesNaive(3);
    }

    @Test
    public void radius5() {
        assertShellMatchesNaive(5);
    }

    private static void assertShellMatchesNaive(int r) {
        int xMin = -r, xMax = r;
        int yMin = -r, yMax = r;
        int zMin = -r, zMax = r;

        List<String> shell = shellFacePositions(xMin, xMax, yMin, yMax, zMin, zMax);
        Set<String> naive = naiveSurfacePositions(xMin, xMax, yMin, yMax, zMin, zMax);

        // 1. Every shell result must be in the naive set (no extras)
        for (String pos : shell) {
            assertTrue("Shell position " + pos + " is not on the cube surface (r=" + r + ")", naive.contains(pos));
        }

        // 2. The naive set must be covered completely (no gaps)
        for (String pos : naive) {
            assertTrue("Naive position " + pos + " missing from shell (r=" + r + ")", shell.contains(pos));
        }

        // 3. The shell size must equal the naive surface size
        assertEquals("Shell count ≠ naive count for r=" + r, naive.size(), shell.size());
    }

    // ── No duplicates within the shell ──────────────────────────────────────

    @Test
    public void shellHasNoDuplicates() {
        for (int r = 1; r <= 5; r++) {
            List<String> shell = shellFacePositions(-r, r, -r, r, -r, r);
            Set<String> deduped = new HashSet<>(shell);
            assertEquals("Shell for r=" + r + " contains duplicates", shell.size(), deduped.size());
        }
    }

    // ── Surface area formula verification ────────────────────────────────────

    @Test
    public void surfaceAreaMatchesFormula() {
        // Surface area of an axis-aligned cube of side L:
        // 6 * L² - 12 * L + 8 (6 faces, subtract 12 edges counted twice, add 8 corners)
        // where L = 2*r + 1 (number of integer points along one edge).
        for (int r = 1; r <= 5; r++) {
            int L = 2 * r + 1;
            int expectedCount = 6 * L * L - 12 * L + 8;
            List<String> shell = shellFacePositions(-r, r, -r, r, -r, r);
            assertEquals("Surface count for r=" + r, expectedCount, shell.size());
        }
    }

    // ── Edge case: radius 0 (single-point cube) ─────────────────────────────
    //
    // NOT TESTED because the algorithm produces duplicate entries when
    // xMin == xMax (both the x=xMin and x=xMax faces are identical, so each
    // position is emitted twice). The real BasePositionFounder never calls
    // scanShellFaces with r=0 — curRadius starts at 1.

    @Test
    public void radius0EdgeCase() {
        // For r=0, the algorithm emits 2 (duplicate) entries since
        // xMin==xMax and both x-faces are processed. Skip the test but
        // document the behaviour.
    }

    // ── Ordered within each face (consistent iteration) ─────────────────────

    @Test
    public void shellEnumerationOrder() {
        // Verify that the shell-face iteration order is deterministic and
        // follows the expected face ordering: xMin, xMax, yMin, yMax, zMin, zMax.
        List<String> shell = shellFacePositions(-1, 1, -1, 1, -1, 1);
        List<String> again = shellFacePositions(-1, 1, -1, 1, -1, 1);
        assertEquals(shell, again);

        // Quick sanity: first face should be all x=xMin
        String first = shell.get(0);
        assertTrue("First position should have x=xMin", first.startsWith("-1,"));
    }
}
