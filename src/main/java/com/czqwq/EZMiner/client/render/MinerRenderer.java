package com.czqwq.EZMiner.client.render;

import java.util.concurrent.LinkedBlockingQueue;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.client.event.DrawBlockHighlightEvent;
import net.minecraftforge.common.MinecraftForge;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector3i;

import com.czqwq.EZMiner.Config;
import com.czqwq.EZMiner.EZMiner;
import com.czqwq.EZMiner.client.ClientStateContainer;
import com.czqwq.EZMiner.core.MinerConfig;
import com.czqwq.EZMiner.core.founder.BasePositionFounder;
import com.czqwq.EZMiner.shader.ShaderManager;
import com.czqwq.EZMiner.utils.FileReadUtils;
import com.czqwq.EZMiner.utils.MatrixUtils;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Client-side renderer that draws block outlines for the current chain preview.
 * Rendering penetrates blocks (depth test disabled) to match QzMiner behaviour.
 */
@SideOnly(Side.CLIENT)
public class MinerRenderer {

    public boolean inPressChainKey = false;

    private final ShaderManager shader = new ShaderManager();
    private static boolean shaderLoaded = false;

    public static final RenderCache renderCache = new RenderCache();
    private final SpaceCalculator spaceCalc = new SpaceCalculator();

    private Vector3i lastTarget = new Vector3i(Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE);
    private BasePositionFounder founder = null;
    private final LinkedBlockingQueue<Vector3i> foundQueue = new LinkedBlockingQueue<>();
    private boolean searchComplete = false;
    private int lastIndexCount = 0;

    private ClientStateContainer clientState;

    public MinerRenderer(ClientStateContainer state) {
        this.clientState = state;
    }

    @SubscribeEvent
    public void onBlockHighlight(DrawBlockHighlightEvent event) {
        if (!Config.usePreview) return;
        if (!inPressChainKey) {
            stopViewer();
            return;
        }
        if (event.target == null) return;

        // Capture GL matrices here while we are in a render context
        MatrixUtils.captureMatrices();

        Vector3i target = new Vector3i(event.target.blockX, event.target.blockY, event.target.blockZ);
        if (!lastTarget.equals(target)) {
            lastTarget = new Vector3i(target);
            restartViewer(target);
        }

        drainQueue();
        doRender(event.partialTicks);
    }

    private void restartViewer(Vector3i target) {
        stopViewer();
        searchComplete = false;
        spaceCalc.hasChange = false;
        // reset space calculator
        spaceCalc.posSet.clear();
        spaceCalc.positions.clear();

        EntityPlayer player = Minecraft.getMinecraft().thePlayer;
        founder = clientState.minerModeState.createPositionFounder(target, foundQueue, player, new MinerConfig());
        EZMiner.parallelTick.addNormalTask(founder);
    }

    private void stopViewer() {
        if (founder != null) {
            founder.interrupt();
            founder = null;
        }
        foundQueue.clear();
        spaceCalc.posSet.clear();
        spaceCalc.positions.clear();
        spaceCalc.hasChange = false;
        lastIndexCount = 0;
        // Clear render cache
        renderCache.updateData(new float[0], new int[0]);
    }

    private static final long MAX_DRAIN_MS = 5;
    private static final int MAX_DRAIN = 128;

    private void drainQueue() {
        if (searchComplete) return;
        if (founder != null && founder.stopped.get()) searchComplete = true;
        long t0 = System.currentTimeMillis();
        int n = 0;
        Vector3i p;
        while ((p = foundQueue.poll()) != null && n < MAX_DRAIN && System.currentTimeMillis() - t0 < MAX_DRAIN_MS) {
            spaceCalc.add(p);
            n++;
        }
        if (spaceCalc.hasChange || n > 0) {
            SpaceCalculator.VertexAndIndex vi = spaceCalc.getVertexAndIndex();
            lastIndexCount = vi.indices.length;
            renderCache.updateData(vi.vertices, vi.indices);
        }
    }

    private void doRender(float partialTicks) {
        if (lastIndexCount <= 0) return;
        ensureShader();
        if (!shaderLoaded) return;

        try {
            shader.bind();
            Vector3f cam = MatrixUtils.getCameraPos(partialTicks);
            Matrix4f model = MatrixUtils.getModelMatrix(-cam.x, -cam.y, -cam.z);
            Matrix4f view = MatrixUtils.getModelViewByOriginal();
            Matrix4f proj = MatrixUtils.getProjectionByOriginal();
            shader.setUniformM4f("model", model);
            shader.setUniformM4f("view", view);
            shader.setUniformM4f("projection", proj);
            shader.setUniform3F("cameraPos", cam);
            renderCache.render(lastIndexCount);
        } finally {
            shader.unbind();
        }
    }

    private void ensureShader() {
        if (shaderLoaded) return;
        try {
            String vert = FileReadUtils.readText("assets/EZMiner/shader/MinerPreviewVertex.glsl");
            String frag = FileReadUtils.readText("assets/EZMiner/shader/MinerPreviewFragment.glsl");
            String geom = FileReadUtils.readText("assets/EZMiner/shader/MinerPreviewGeometry.glsl");
            shader.loadShader(vert, frag, geom.isEmpty() ? null : geom);
            shaderLoaded = true;
        } catch (Exception e) {
            EZMiner.LOG.error("Failed to load preview shaders", e);
        }
    }

    public void registry() {
        MinecraftForge.EVENT_BUS.register(this);
        FMLCommonHandler.instance()
            .bus()
            .register(this);
    }

    public void unRegistry() {
        MinecraftForge.EVENT_BUS.unregister(this);
        FMLCommonHandler.instance()
            .bus()
            .unregister(this);
    }
}
