package com.czqwq.EZMiner.core.founder;

import static org.junit.Assert.*;

import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.HashSet;
import java.util.PriorityQueue;
import java.util.Queue;
import java.util.Set;

import org.junit.Test;

/**
 * Verifies that the Dual-Frontier BFS and PriorityQueue BFS produce identical
 * block sets on small simulated ore veins where the connected component fits
 * within {@code blockLimit}.
 *
 * <p>
 * This test implements simplified versions of the two BFS algorithms from
 * {@link ChainPositionFounder}, operating on a pure in-memory grid with no
 * Minecraft dependencies. It mirrors the real code's:
 * <ul>
 * <li>{@code smallRadius} — neighbour search cube half-size</li>
 * <li>{@code bigRadius} — maximum expansion radius from center</li>
 * <li>same-type matching — only blocks matching the center block are expanded</li>
 * <li>visited-set guarding via {@code encodePos} / {@link HashSet}</li>
 * </ul>
 */
public class BfsConsistencyTest {

    /**
     * A simple 3D grid of "ore" blocks identified by integer type.
     * Type 0 = air (not traversable).
     */
    static class OreGrid {

        final int[][][] blocks; // [x][y][z] — type id, 0 = air
        final int originX, originY, originZ;
        final int sizeX, sizeY, sizeZ;

        OreGrid(int[][][] blocks, int ox, int oy, int oz) {
            this.blocks = blocks;
            this.originX = ox;
            this.originY = oy;
            this.originZ = oz;
            this.sizeX = blocks.length;
            this.sizeY = blocks[0].length;
            this.sizeZ = blocks[0][0].length;
        }

        boolean inBounds(int x, int y, int z) {
            return x >= 0 && x < sizeX && y >= 0 && y < sizeY && z >= 0 && z < sizeZ;
        }

        int typeAt(int x, int y, int z) {
            return blocks[x][y][z];
        }

        /** Build a flat grid with a connected blob of ore type 1 centered at (cx,cy,cz). */
        static OreGrid blob(int size, int cx, int cy, int cz, int blobRadius) {
            int[][][] grid = new int[size][size][size];
            for (int x = 0; x < size; x++) {
                for (int y = 0; y < size; y++) {
                    for (int z = 0; z < size; z++) {
                        int dx = x - cx, dy = y - cy, dz = z - cz;
                        if (dx * dx + dy * dy + dz * dz <= blobRadius * blobRadius) {
                            grid[x][y][z] = 1;
                        }
                    }
                }
            }
            return new OreGrid(grid, cx, cy, cz);
        }

        /** Build a cross-shaped vein for testing irregular connectivity. */
        static OreGrid cross(int size, int cx, int cy, int cz, int armLen) {
            int[][][] grid = new int[size][size][size];
            // X arm
            for (int dx = -armLen; dx <= armLen; dx++) {
                if (cx + dx >= 0 && cx + dx < size) grid[cx + dx][cy][cz] = 1;
            }
            // Y arm
            for (int dy = -armLen; dy <= armLen; dy++) {
                if (cy + dy >= 0 && cy + dy < size) grid[cx][cy + dy][cz] = 1;
            }
            // Z arm
            for (int dz = -armLen; dz <= armLen; dz++) {
                if (cz + dz >= 0 && cz + dz < size) grid[cx][cy][cz + dz] = 1;
            }
            return new OreGrid(grid, cx, cy, cz);
        }
    }

    /** Encodes (x,y,z) into a single long, matching the real encodePos scheme. */
    static long posKey(int x, int y, int z) {
        return ((long) (x + 30_000_000) << 38) | ((long) (y & 0xFFF) << 26) | (long) (z + 30_000_000);
    }

    // ── PriorityQueue BFS (mirrors ChainPositionFounder.doSingleThreadedSearchPriorityQueue) ──

    static Set<Long> bfsPriorityQueue(OreGrid grid, int smallRadius, int bigRadius, int blockLimit) {
        Set<Long> visited = new HashSet<>();
        Set<Long> result = new HashSet<>();
        int originType = grid.typeAt(grid.originX, grid.originY, grid.originZ);

        long originKey = posKey(grid.originX, grid.originY, grid.originZ);
        visited.add(originKey);
        result.add(originKey);

        PriorityQueue<long[]> frontier = new PriorityQueue<>(
            Comparator.comparingDouble(
                p -> distSq((int) p[0], (int) p[1], (int) p[2], grid.originX, grid.originY, grid.originZ)));
        frontier.add(new long[] { grid.originX, grid.originY, grid.originZ });

        while (!frontier.isEmpty() && result.size() < blockLimit) {
            long[] cur = frontier.poll();
            int cx = (int) cur[0], cy = (int) cur[1], cz = (int) cur[2];

            for (int dx = -smallRadius; dx <= smallRadius; dx++) {
                int nx = cx + dx;
                if (Math.abs(nx - grid.originX) > bigRadius) continue;
                for (int dy = -smallRadius; dy <= smallRadius; dy++) {
                    int ny = cy + dy;
                    if (Math.abs(ny - grid.originY) > bigRadius) continue;
                    for (int dz = -smallRadius; dz <= smallRadius; dz++) {
                        if (dx == 0 && dy == 0 && dz == 0) continue;
                        int nz = cz + dz;
                        if (Math.abs(nz - grid.originZ) > bigRadius) continue;
                        if (!grid.inBounds(nx, ny, nz)) continue;
                        if (grid.typeAt(nx, ny, nz) != originType) continue;

                        long nKey = posKey(nx, ny, nz);
                        if (visited.add(nKey)) {
                            result.add(nKey);
                            frontier.add(new long[] { nx, ny, nz });
                            if (result.size() >= blockLimit) return result;
                        }
                    }
                }
            }
        }
        return result;
    }

    // ── Dual-Frontier BFS (mirrors ChainPositionFounder.doSingleThreadedSearchDualFrontier) ──

    static Set<Long> bfsDualFrontier(OreGrid grid, int smallRadius, int bigRadius, int blockLimit) {
        Set<Long> visited = new HashSet<>();
        Set<Long> result = new HashSet<>();
        int originType = grid.typeAt(grid.originX, grid.originY, grid.originZ);

        long originKey = posKey(grid.originX, grid.originY, grid.originZ);
        visited.add(originKey);
        result.add(originKey);

        Queue<long[]> currentFrontier = new ArrayDeque<>();
        Queue<long[]> nextFrontier = new ArrayDeque<>();
        currentFrontier.add(new long[] { grid.originX, grid.originY, grid.originZ });

        while (result.size() < blockLimit) {
            long[] cur = currentFrontier.poll();
            if (cur == null) {
                // Rotate frontiers
                Queue<long[]> tmp = currentFrontier;
                currentFrontier = nextFrontier;
                nextFrontier = tmp;
                if (currentFrontier.isEmpty()) break;
                continue;
            }

            int cx = (int) cur[0], cy = (int) cur[1], cz = (int) cur[2];
            for (int dx = -smallRadius; dx <= smallRadius; dx++) {
                int nx = cx + dx;
                if (Math.abs(nx - grid.originX) > bigRadius) continue;
                for (int dy = -smallRadius; dy <= smallRadius; dy++) {
                    int ny = cy + dy;
                    if (Math.abs(ny - grid.originY) > bigRadius) continue;
                    for (int dz = -smallRadius; dz <= smallRadius; dz++) {
                        if (dx == 0 && dy == 0 && dz == 0) continue;
                        int nz = cz + dz;
                        if (Math.abs(nz - grid.originZ) > bigRadius) continue;
                        if (!grid.inBounds(nx, ny, nz)) continue;
                        if (grid.typeAt(nx, ny, nz) != originType) continue;

                        long nKey = posKey(nx, ny, nz);
                        if (visited.add(nKey)) {
                            result.add(nKey);
                            nextFrontier.add(new long[] { nx, ny, nz });
                            if (result.size() >= blockLimit) return result;
                        }
                    }
                }
            }
        }
        return result;
    }

    private static double distSq(int x1, int y1, int z1, int x2, int y2, int z2) {
        double dx = x1 - x2, dy = y1 - y2, dz = z1 - z2;
        return dx * dx + dy * dy + dz * dz;
    }

    // ── Test cases ──────────────────────────────────────────────────────────

    @Test
    public void blobVeinFitsWithinLimit() {
        OreGrid grid = OreGrid.blob(20, 10, 10, 10, 4);
        int smallRadius = 2, bigRadius = 8, blockLimit = 10000;

        Set<Long> pq = bfsPriorityQueue(grid, smallRadius, bigRadius, blockLimit);
        Set<Long> df = bfsDualFrontier(grid, smallRadius, bigRadius, blockLimit);

        assertEquals("Blob vein: set size mismatch", pq.size(), df.size());
        assertEquals("Blob vein: sets differ", pq, df);
    }

    @Test
    public void crossVeinFitsWithinLimit() {
        OreGrid grid = OreGrid.cross(30, 15, 15, 15, 8);
        int smallRadius = 2, bigRadius = 10, blockLimit = 10000;

        Set<Long> pq = bfsPriorityQueue(grid, smallRadius, bigRadius, blockLimit);
        Set<Long> df = bfsDualFrontier(grid, smallRadius, bigRadius, blockLimit);

        assertEquals("Cross vein: set size mismatch", pq.size(), df.size());
        assertEquals("Cross vein: sets differ", pq, df);
    }

    @Test
    public void smallVeinTightBlockLimit() {
        // Cross with armLen=5: 1 + 5*2 + 5*2 + 5*2 = 31 blocks; limit to 20
        OreGrid grid = OreGrid.cross(20, 10, 10, 10, 5);
        int smallRadius = 2, bigRadius = 8, blockLimit = 20;

        Set<Long> pq = bfsPriorityQueue(grid, smallRadius, bigRadius, blockLimit);
        Set<Long> df = bfsDualFrontier(grid, smallRadius, bigRadius, blockLimit);

        // With tight limit, both should have exactly blockLimit entries
        assertEquals("Tight limit: PQ block count", blockLimit, pq.size());
        assertEquals("Tight limit: DF block count", blockLimit, df.size());
        // Both must be subsets of the unlimited full vein
        Set<Long> full = bfsPriorityQueue(grid, smallRadius, bigRadius, 100000);
        assertTrue("PQ must be subset of full vein", full.containsAll(pq));
        assertTrue("DF must be subset of full vein", full.containsAll(df));
    }

    @Test
    public void singleBlockOrigin() {
        // Grid with only the origin block as ore
        int[][][] block = new int[5][5][5];
        block[2][2][2] = 1;
        OreGrid grid = new OreGrid(block, 2, 2, 2);

        Set<Long> pq = bfsPriorityQueue(grid, 2, 8, 1000);
        Set<Long> df = bfsDualFrontier(grid, 2, 8, 1000);

        assertEquals(1, pq.size());
        assertEquals(1, df.size());
        assertEquals(pq, df);
    }

    @Test
    public void twoBlockVein() {
        int[][][] block = new int[5][5][5];
        block[2][2][2] = 1;
        block[3][2][2] = 1; // adjacent
        OreGrid grid = new OreGrid(block, 2, 2, 2);

        Set<Long> pq = bfsPriorityQueue(grid, 2, 8, 1000);
        Set<Long> df = bfsDualFrontier(grid, 2, 8, 1000);

        assertEquals(2, pq.size());
        assertEquals(pq, df);
    }

    @Test
    public void isolatedBlockNotReachable() {
        // A disconnected ore block should not be found
        int[][][] block = new int[10][10][10];
        block[5][5][5] = 1; // origin
        block[8][5][5] = 1; // disconnected, far
        OreGrid grid = new OreGrid(block, 5, 5, 5);

        Set<Long> pq = bfsPriorityQueue(grid, 1, 8, 1000);
        Set<Long> df = bfsDualFrontier(grid, 1, 8, 1000);

        // With smallRadius=1, the far block shouldn't be reached
        assertEquals(1, pq.size());
        assertEquals(pq, df);
    }

    @Test
    public void differentSmallRadii() {
        // Test with various smallRadius values to catch neighbor-iteration asymmetry
        for (int sr = 1; sr <= 3; sr++) {
            OreGrid grid = OreGrid.blob(15, 7, 7, 7, 3);
            Set<Long> pq = bfsPriorityQueue(grid, sr, 8, 10000);
            Set<Long> df = bfsDualFrontier(grid, sr, 8, 10000);
            assertEquals("smallRadius=" + sr + ": size mismatch", pq.size(), df.size());
            assertEquals("smallRadius=" + sr + ": sets differ", pq, df);
        }
    }

    @Test
    public void bigRadiusBoundsEnforced() {
        // Vein extends beyond bigRadius — both should stop at the boundary
        OreGrid grid = OreGrid.blob(30, 15, 15, 15, 10);
        // bigRadius=3 restricts expansion tightly
        Set<Long> pq = bfsPriorityQueue(grid, 2, 3, 10000);
        Set<Long> df = bfsDualFrontier(grid, 2, 3, 10000);

        assertEquals("BigRadius bound: size mismatch", pq.size(), df.size());
        assertEquals("BigRadius bound: sets differ", pq, df);

        // All found blocks must be within bigRadius (Manhattan distance check)
        for (long key : pq) {
            // Decode: extract x, y, z from the key
            int z = (int) (key & 0x3FFFFFF) - 30_000_000;
            int y = (int) ((key >> 26) & 0xFFF);
            int x = (int) (key >> 38) - 30_000_000;
            assertTrue(
                "Block (" + x + "," + y + "," + z + ") outside bigRadius",
                Math.abs(x - grid.originX) <= 3 && Math.abs(y - grid.originY) <= 3 && Math.abs(z - grid.originZ) <= 3);
        }
    }
}
