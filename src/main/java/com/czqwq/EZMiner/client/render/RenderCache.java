package com.czqwq.EZMiner.client.render;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;

import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

import com.czqwq.EZMiner.EZMiner;

/**
 * Manages OpenGL VAO/VBO/EBO for the chain preview wireframe.
 *
 * <p>GL objects are created lazily on the first {@link #updateData} or {@link #render} call so
 * that the constructor is safe to call before an OpenGL context exists (e.g. during FML proxy
 * setup).
 */
public class RenderCache {

    public int vao, vbo, ebo;
    private int vboCap = 0, eboCap = 0;
    private boolean glInitialized = false;

    public RenderCache() {
        // Intentionally empty – GL objects are created lazily in ensureGLInit().
    }

    /**
     * Initialise GL objects. Must only be called from the render thread when a GL context is
     * active.
     */
    private void ensureGLInit() {
        if (glInitialized) return;
        glInitialized = true;

        vao = GL30.glGenVertexArrays();
        vbo = GL15.glGenBuffers();
        ebo = GL15.glGenBuffers();

        final int INITIAL = 10 * 1024 * 1024; // 10 MB
        vboCap = INITIAL;
        eboCap = INITIAL;

        GL30.glBindVertexArray(vao);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vbo);
        GL15.glBufferData(GL15.GL_ARRAY_BUFFER, vboCap, GL15.GL_DYNAMIC_DRAW);
        GL20.glVertexAttribPointer(0, 3, GL11.GL_FLOAT, false, 0, 0);
        GL20.glEnableVertexAttribArray(0);

        GL15.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, ebo);
        GL15.glBufferData(GL15.GL_ELEMENT_ARRAY_BUFFER, eboCap, GL15.GL_DYNAMIC_DRAW);

        GL30.glBindVertexArray(0);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
        GL15.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, 0);
    }

    public void updateData(float[] vertices, int[] indices) {
        ensureGLInit();
        GL30.glBindVertexArray(vao);

        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vbo);
        FloatBuffer fb = BufferUtils.createFloatBuffer(vertices.length);
        fb.put(vertices)
            .flip();
        if (vertices.length * 4 > vboCap) {
            vboCap = Math.max(vboCap * 2, vertices.length * 4);
            EZMiner.LOG.debug("VBO expanded to {} bytes", vboCap);
        }
        GL15.glBufferData(GL15.GL_ARRAY_BUFFER, fb, GL15.GL_DYNAMIC_DRAW);

        GL15.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, ebo);
        IntBuffer ib = BufferUtils.createIntBuffer(indices.length);
        ib.put(indices)
            .flip();
        if (indices.length * 4 > eboCap) {
            eboCap = Math.max(eboCap * 2, indices.length * 4);
            EZMiner.LOG.debug("EBO expanded to {} bytes", eboCap);
        }
        GL15.glBufferData(GL15.GL_ELEMENT_ARRAY_BUFFER, ib, GL15.GL_DYNAMIC_DRAW);

        GL30.glBindVertexArray(0);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
        GL15.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, 0);
    }

    public void render(int indexCount) {
        if (indexCount <= 0) return;
        ensureGLInit();
        if (vao == 0) return; // GL init failed – do nothing rather than crash
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glDisable(GL11.GL_CULL_FACE);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

        GL30.glBindVertexArray(vao);
        GL20.glEnableVertexAttribArray(0);
        GL11.glDrawElements(GL11.GL_LINES, indexCount, GL11.GL_UNSIGNED_INT, 0);
        GL20.glDisableVertexAttribArray(0);
        GL30.glBindVertexArray(0);

        GL11.glEnable(GL11.GL_DEPTH_TEST);
        GL11.glPopAttrib();
    }
}
