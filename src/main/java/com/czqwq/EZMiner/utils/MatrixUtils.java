package com.czqwq.EZMiner.utils;

import java.nio.FloatBuffer;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public class MatrixUtils {

    public static final Logger LOG = LogManager.getLogger();

    public static final FloatBuffer floatBuffer = BufferUtils.createFloatBuffer(16);
    public static final Matrix4f modelView = new Matrix4f();
    public static final Matrix4f projection = new Matrix4f();

    /** Capture current GL modelview and projection matrices. Call from a render context. */
    public static void captureMatrices() {
        floatBuffer.clear();
        GL11.glGetFloat(GL11.GL_MODELVIEW_MATRIX, floatBuffer);
        floatBuffer.rewind();
        modelView.set(floatBuffer);

        floatBuffer.clear();
        GL11.glGetFloat(GL11.GL_PROJECTION_MATRIX, floatBuffer);
        floatBuffer.rewind();
        projection.set(floatBuffer);
    }

    public static Matrix4f getModelViewByOriginal() {
        return modelView;
    }

    public static Matrix4f getProjectionByOriginal() {
        return projection;
    }

    public static Matrix4f getModelMatrix(float x, float y, float z) {
        Matrix4f m = new Matrix4f();
        m.identity();
        m.translate(new Vector3f(x, y, z));
        return m;
    }

    public static Vector3f getCameraPos(float partialTicks) {
        EntityPlayer player = Minecraft.getMinecraft().thePlayer;
        double eyeX = player.prevPosX + (player.posX - player.prevPosX) * partialTicks;
        double eyeY = player.prevPosY + (player.posY - player.prevPosY) * partialTicks;
        double eyeZ = player.prevPosZ + (player.posZ - player.prevPosZ) * partialTicks;
        return new Vector3f((float) eyeX, (float) eyeY, (float) eyeZ);
    }
}
