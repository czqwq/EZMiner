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

    // Position lookup for O(1) neighbour check
    public final Set<Vector3i> posSet = new HashSet<>();
    public final List<Vector3i> positions = new ArrayList<>();

    public boolean hasChange = false;

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
        for (Vector3i p : positions) {
            Set<Vector2i> edges = new HashSet<>(COMPLETE_EDGES);
            // Remove edges shared with neighbours
            for (Map.Entry<String, Vector3i> e : DIR_OFFSET.entrySet()) {
                Vector3i neighbour = new Vector3i(p).add(e.getValue());
                if (posSet.contains(neighbour)) {
                    Vector2i[] toRemove = DIR_EDGES.get(e.getKey());
                    if (toRemove != null) {
                        for (Vector2i edge : toRemove) edges.remove(edge);
                    }
                }
            }
            // Skip fully-enclosed blocks – but do NOT increment base here;
            // base must only advance when vertices are actually appended.
            if (edges.isEmpty()) {
                continue;
            }

            float[] v = VERTEX.clone();
            for (int i = 0; i < v.length; i += 3) {
                v[i] += p.x;
                v[i + 1] += p.y;
                v[i + 2] += p.z;
            }
            verts.add(v);

            int[] idx = new int[edges.size() * 2];
            int c = 0;
            for (Vector2i edge : edges) {
                idx[c++] = edge.x + base;
                idx[c++] = edge.y + base;
            }
            inds.add(idx);
            base += 8; // advance only after vertices are appended
        }
        hasChange = false;
        return new VertexAndIndex(ArrayConverter.convertF(verts), ArrayConverter.convertI(inds));
    }

    public static class VertexAndIndex {

        public final float[] vertices;
        public final int[] indices;

        public VertexAndIndex(float[] v, int[] i) {
            vertices = v;
            indices = i;
        }
    }
}
