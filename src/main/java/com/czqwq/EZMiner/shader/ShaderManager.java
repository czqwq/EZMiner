package com.czqwq.EZMiner.shader;

import java.nio.FloatBuffer;
import java.util.HashMap;
import java.util.Map;

import javax.annotation.Nullable;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL32;

import com.czqwq.EZMiner.EZMiner;

public class ShaderManager {

    public int programId;
    private final Map<String, Integer> uniforms = new HashMap<>();
    private final FloatBuffer matBuf = BufferUtils.createFloatBuffer(16);
    private static int currentProgram = 0;

    public ShaderManager() {
        // Intentionally empty – GL program is created lazily inside loadShader() so that
        // this constructor is safe to call before an OpenGL context exists.
    }

    public void loadShader(String vert, String frag, @Nullable String geom) {
        uniforms.clear();
        // Create (or recreate) the GL program object now that we know a context is active.
        if (programId != 0) {
            GL20.glDeleteProgram(programId);
        }
        programId = GL20.glCreateProgram();
        int vs = compile(vert, GL20.GL_VERTEX_SHADER);
        int fs = compile(frag, GL20.GL_FRAGMENT_SHADER);
        int gs = -1;
        if (geom != null) gs = compile(geom, GL32.GL_GEOMETRY_SHADER);

        GL20.glAttachShader(programId, vs);
        GL20.glAttachShader(programId, fs);
        if (gs >= 0) GL20.glAttachShader(programId, gs);

        GL20.glLinkProgram(programId);
        if (GL20.glGetProgrami(programId, GL20.GL_LINK_STATUS) == GL11.GL_FALSE)
            throw new RuntimeException("Shader link error: " + GL20.glGetProgramInfoLog(programId, 4096));
        GL20.glValidateProgram(programId);
        if (GL20.glGetProgrami(programId, GL20.GL_VALIDATE_STATUS) == GL11.GL_FALSE)
            throw new RuntimeException("Shader validate error: " + GL20.glGetProgramInfoLog(programId, 4096));

        GL20.glDeleteShader(vs);
        GL20.glDeleteShader(fs);
        if (gs >= 0) GL20.glDeleteShader(gs);
        EZMiner.LOG.info("EZMiner shaders loaded successfully.");
    }

    private int compile(String source, int type) {
        int id = GL20.glCreateShader(type);
        GL20.glShaderSource(id, source);
        GL20.glCompileShader(id);
        if (GL20.glGetShaderi(id, GL20.GL_COMPILE_STATUS) == GL11.GL_FALSE)
            throw new RuntimeException("Shader compile error:\n" + GL20.glGetShaderInfoLog(id, 4096));
        return id;
    }

    private int uniform(String name) {
        return uniforms.computeIfAbsent(name, n -> {
            int loc = GL20.glGetUniformLocation(programId, n);
            if (loc == -1) EZMiner.LOG.warn("Uniform '{}' not found", n);
            return loc;
        });
    }

    public void setUniformM4f(String name, Matrix4f m) {
        int loc = uniform(name);
        if (loc < 0) return;
        matBuf.clear();
        matBuf.put(m.get(new float[16]))
            .flip();
        GL20.glUniformMatrix4(loc, false, matBuf);
    }

    public void setUniform3F(String name, Vector3f v) {
        int loc = uniform(name);
        if (loc >= 0) GL20.glUniform3f(loc, v.x, v.y, v.z);
    }

    public void bind() {
        if (programId != currentProgram) {
            GL20.glUseProgram(programId);
            currentProgram = programId;
        }
    }

    public void unbind() {
        GL20.glUseProgram(0);
        currentProgram = 0;
    }
}
