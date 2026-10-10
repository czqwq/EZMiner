package com.czqwq.EZMiner.client.render;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.joml.Vector2i;
import org.joml.Vector3i;

import com.czqwq.EZMiner.utils.ArrayConverter;

/**
 * Computes the set of visible edges for a collection of block positions.
 * Shared edges between adjacent blocks are removed for a clean wireframe look.
 */
public class SpaceCalculator {

    // Unit-cube vertex positions (index 0-7)
    public static final float[] VERTEX = {
        // front face
        0, 0, 1, 1, 0, 1, 1, 1, 1, 0, 1, 1,
        // back face
        0, 0, 0, 1, 0, 0, 1, 1, 0, 0, 1, 0, };

    // All 12 edges of a unit cube
    public static final int[] INDEX = { 0, 1, 1, 2, 2, 3, 0, 3, // front
        4, 5, 5, 6, 6, 7, 4, 7, // back
        2, 6, 3, 7, // top
        0, 4, 1, 5 // bottom
    };

    private static final List<Vector2i> COMPLETE_EDGES = Arrays.asList(
        new Vector2i(0, 1),
        new Vector2i(1, 2),
        new Vector2i(2, 3),
        new Vector2i(0, 3),
        new Vector2i(4, 5),
        new Vector2i(5, 6),
        new Vector2i(6, 7),
        new Vector2i(4, 7),
        new Vector2i(2, 6),
        new Vector2i(3, 7),
        new Vector2i(0, 4),
        new Vector2i(1, 5));

    // Direction → edges on that face
    private static final Map<String, Vector2i[]> DIR_EDGES = new HashMap<>();
    static {
        DIR_EDGES.put(
            "YP",
            new Vector2i[] { new Vector2i(2, 3), new Vector2i(6, 7), new Vector2i(2, 6), new Vector2i(3, 7) });
        DIR_EDGES.put(
            "YN",
            new Vector2i[] { new Vector2i(0, 1), new Vector2i(4, 5), new Vector2i(0, 4), new Vector2i(1, 5) });
        DIR_EDGES.put(
            "XP",
            new Vector2i[] { new Vector2i(1, 2), new Vector2i(5, 6), new Vector2i(1, 5), new Vector2i(2, 6) });
        DIR_EDGES.put(
            "XN",
            new Vector2i[] { new Vector2i(0, 3), new Vector2i(4, 7), new Vector2i(0, 4), new Vector2i(3, 7) });
        DIR_EDGES.put(
            "ZP",
            new Vector2i[] { new Vector2i(0, 1), new Vector2i(1, 2), new Vector2i(2, 3), new Vector2i(0, 3) });
        DIR_EDGES.put(
            "ZN",
            new Vector2i[] { new Vector2i(4, 5), new Vector2i(5, 6), new Vector2i(6, 7), new Vector2i(4, 7) });
    }

    // direction → offset
    private static final Map<String, Vector3i> DIR_OFFSET = new HashMap<>();
    static {
        DIR_OFFSET.put("YP", new Vector3i(0, 1, 0));
        DIR_OFFSET.put("YN", new Vector3i(0, -1, 0));
        DIR_OFFSET.put("XP", new Vector3i(1, 0, 0));
        DIR_OFFSET.put("XN", new Vector3i(-1, 0, 0));
        DIR_OFFSET.put("ZP", new Vector3i(0, 0, 1));
        DIR_OFFSET.put("ZN", new Vector3i(0, 0, -1));
    }

    /**
     * Array views of the two tables above, built once at class load. The edge sets are resolved to
     * {@link #COMPLETE_EDGES} indices, so the per-block hot loop costs an array read instead of a
     * string-keyed {@code HashMap} lookup, and the index mapping cannot drift from the tables.
     */
    private static final String[] DIRECTIONS = { "YP", "YN", "XP", "XN", "ZP", "ZN" };
    private static final int[][] DIR_EDGES_IDX = new int[DIRECTIONS.length][];
    private static final Vector3i[] DIR_OFFSET_ARR = new Vector3i[DIRECTIONS.length];
    static {
        for (int d = 0; d < DIRECTIONS.length; d++) {
            Vector2i[] edges = DIR_EDGES.get(DIRECTIONS[d]);
            int[] idx = new int[edges.length];
            for (int i = 0; i < edges.length; i++) {
                idx[i] = COMPLETE_EDGES.indexOf(edges[i]);
            }
            DIR_EDGES_IDX[d] = idx;
            DIR_OFFSET_ARR[d] = DIR_OFFSET.get(DIRECTIONS[d]);
        }
    }

    // Position lookup for O(1) neighbour check
    public final Set<Vector3i> posSet = new HashSet<>();
    public final List<Vector3i> positions = new ArrayList<>();

    public boolean hasChange = false;

    /** Scratch: which of the 12 cube edges survive for the block currently being processed. */
    private final boolean[] edgeKept = new boolean[COMPLETE_EDGES.size()];
    /** Scratch probe for neighbour lookups — avoids a Vector3i allocation per direction/block. */
    private final Vector3i probePos = new Vector3i();

    /**
     * The per-position index ranges produced by the most recent
     * {@link #getVertexAndIndex()}, exposed for render strategies that draw a subset of the
     * positions (the gradient renderer's Y-bands).
     *
     * <p>
     * Thread-local because it is written while the mesh is rebuilt and read while rendering —
     * both on the client thread — without changing the {@code BlockOutlineRenderStrategy}
     * signature or having the renderer duplicate the geometry walk.
     * </p>
     */
    private static final ThreadLocal<VertexAndIndex> LAST_GEOMETRY = new ThreadLocal<>();

    /** See {@link #LAST_GEOMETRY}. May be {@code null} before the first mesh build. */
    public static VertexAndIndex lastGeometry() {
        return LAST_GEOMETRY.get();
    }

    public void add(Vector3i pos) {
        if (posSet.contains(pos)) return;
        posSet.add(pos);
        positions.add(pos);
        hasChange = true;
    }

    public VertexAndIndex getVertexAndIndex() {
        ArrayList<float[]> verts = new ArrayList<>();
        ArrayList<int[]> inds = new ArrayList<>();
        int base = 0;
        // Per-position index range inside the flat index stream. Consumers that draw a subset of
        // the positions (the gradient renderer's Y-bands) need this, because the stream is NOT
        // uniform per position: a fully-enclosed block contributes no indices at all, and a
        // partially exposed block contributes only its surviving edges (kept * 2 indices). A
        // fixed "position index * 24" stride therefore points at the wrong indices for every
        // block after the first enclosed one.
        final int[] blockIndexOffset = new int[positions.size()];
        final int[] blockIndexCount = new int[positions.size()];
        int runningIndex = 0;
        for (int i = 0; i < positions.size(); i++) {
            Vector3i p = positions.get(i);
            Arrays.fill(edgeKept, true);
            // Remove edges shared with neighbours (positions are block origins, so a unit offset in
            // each direction is the only possible neighbour). Allocation-free: one reused boolean[]
            // instead of a HashSet rebuilt for every block.
            for (int d = 0; d < DIR_OFFSET_ARR.length; d++) {
                Vector3i off = DIR_OFFSET_ARR[d];
                probePos.set(p.x + off.x, p.y + off.y, p.z + off.z);
                if (!posSet.contains(probePos)) continue;
                for (int e : DIR_EDGES_IDX[d]) edgeKept[e] = false;
            }
            int kept = 0;
            for (boolean b : edgeKept) {
                if (b) kept++;
            }
            // Skip fully-enclosed blocks – but do NOT increment base here;
            // base must only advance when vertices are actually appended.
            if (kept == 0) {
                blockIndexOffset[i] = runningIndex;
                blockIndexCount[i] = 0;
                continue;
            }

            float[] v = VERTEX.clone();
            for (int k = 0; k < v.length; k += 3) {
                v[k] += p.x;
                v[k + 1] += p.y;
                v[k + 2] += p.z;
            }
            verts.add(v);

            int c = 0;
            int[] idx = new int[kept * 2];
            for (int e = 0; e < edgeKept.length; e++) {
                if (!edgeKept[e]) continue;
                Vector2i edge = COMPLETE_EDGES.get(e);
                idx[c++] = edge.x + base;
                idx[c++] = edge.y + base;
            }
            inds.add(idx);
            blockIndexOffset[i] = runningIndex;
            blockIndexCount[i] = idx.length;
            runningIndex += idx.length;
            base += 8; // advance only after vertices are appended
        }
        hasChange = false;
        VertexAndIndex vi = new VertexAndIndex(
            ArrayConverter.convertF(verts),
            ArrayConverter.convertI(inds),
            blockIndexOffset,
            blockIndexCount);
        LAST_GEOMETRY.set(vi);
        return vi;
    }

    public static class VertexAndIndex {

        public final float[] vertices;
        public final int[] indices;
        /**
         * Flat index-stream offset of each entry in {@link SpaceCalculator#positions}, in the
         * same order. Length equals {@code positions.size()}.
         */
        public final int[] blockIndexOffset;
        /** Number of indices belonging to each entry in {@link SpaceCalculator#positions}. */
        public final int[] blockIndexCount;

        public VertexAndIndex(float[] v, int[] i) {
            this(v, i, null, null);
        }

        public VertexAndIndex(float[] v, int[] i, int[] offsets, int[] counts) {
            vertices = v;
            indices = i;
            blockIndexOffset = offsets;
            blockIndexCount = counts;
        }

        /**
         * Index-stream offset for the position at {@code positionIndex}, falling back to the
         * legacy fixed 24-index stride when per-position ranges are absent.
         */
        public int indexOffset(int positionIndex) {
            if (blockIndexOffset == null || positionIndex < 0 || positionIndex >= blockIndexOffset.length) {
                return positionIndex * 24;
            }
            return blockIndexOffset[positionIndex];
        }

        /** Number of indices for the position at {@code positionIndex}; 24 when unknown. */
        public int indexCount(int positionIndex) {
            if (blockIndexCount == null || positionIndex < 0 || positionIndex >= blockIndexCount.length) {
                return 24;
            }
            return blockIndexCount[positionIndex];
        }
    }
}
