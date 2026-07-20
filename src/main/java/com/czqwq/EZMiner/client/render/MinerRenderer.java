package com.czqwq.EZMiner.client.render;

import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.MovingObjectPosition;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.common.MinecraftForge;

import org.joml.Vector3i;
import org.lwjgl.opengl.GL11;

import com.czqwq.EZMiner.Config;
import com.czqwq.EZMiner.EZMiner;
import com.czqwq.EZMiner.chain.client.preview.ChainPreviewController;
import com.czqwq.EZMiner.chain.client.preview.ChainPreviewState;
import com.czqwq.EZMiner.client.ClientStateContainer;
import com.czqwq.EZMiner.core.MinerConfig;
import com.czqwq.EZMiner.core.founder.BasePositionFounder;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Client-side renderer that draws block outlines for the current chain preview.
 *
 * <p>
 * Uses {@link RenderWorldLastEvent} (fired once per frame after all world geometry) and
 * fixed-function OpenGL — no custom shaders are required. The coordinate system is shifted by
 * {@code -RenderManager.renderPos} so block positions stored in world space map directly onto
 * the rendered scene using fixed-function OpenGL which works on all supported versions.
 *
 * <p>
 * Preview lifecycle:
 * <ol>
 * <li>While the chain key is <em>not</em> held the renderer continuously updates the preview
 * as the player looks around (normal mode).</li>
 * <li>When the chain key is pressed ({@link #freeze()}): the current preview is frozen in
 * place – no new searches are started, the existing wireframe keeps rendering during the
 * chain operation.</li>
 * <li>When the chain key is released ({@link #unfreeze()}): the frozen frame is cleared and
 * the renderer returns to normal mode, ready for the next chain.</li>
 * </ol>
 */
@SideOnly(Side.CLIENT)
public class MinerRenderer {

    public static final RenderCache renderCache = new RenderCache();
    private final SpaceCalculator spaceCalc = new SpaceCalculator();

    private static final BlockOutlineRenderStrategy NATIVE_RENDERER = new NativeBlockOutlineRenderer();
    private static final BlockOutlineRenderStrategy MODERN_RENDERER = new ModernBlockOutlineRenderer();

    private Vector3i lastTarget = new Vector3i(Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE);
    private BasePositionFounder founder = null;
    private final LinkedBlockingQueue<Vector3i> foundQueue = new LinkedBlockingQueue<>();
    private boolean searchComplete = false;
    private int lastIndexCount = 0;
    /** Last minesweeperFlaggedVersion seen; used to detect when the flagged-mine list changed. */
    private int lastMinesweeperVersion = -1;
    /** Last sudokuFilledVersion seen; used to detect when the filled-cell list changed. */
    private int lastSudokuVersion = -1;

    private final ClientStateContainer clientState;
    private final ChainPreviewController previewController = new ChainPreviewController();

    public MinerRenderer(ClientStateContainer state) {
        this.clientState = state;
    }

    /**
     * Freezes the preview: stops any in-progress search and holds the current wireframe.
     * Called when the chain operation begins so that the preview does not update while
     * blocks are being broken.
     */
    public void freeze() {
        previewController.freeze();
        // Stop the search thread – no new positions will arrive, the frozen frame is final.
        if (founder != null) {
            founder.interrupt();
            founder = null;
        }
        foundQueue.clear();
    }

    /**
     * Unfreezes the preview and clears the display.
     * Called when the chain key is released so that the renderer returns to normal mode for
     * the next activation.
     */
    public void unfreeze() {
        previewController.unfreeze();
        stopViewer();
    }

    @SubscribeEvent
    public void onRenderWorldLast(RenderWorldLastEvent event) {
        if (!Config.isPreviewEnabled()) {
            stopViewer();
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.theWorld == null || mc.thePlayer == null) {
            stopViewer();
            return;
        }
        if (!clientState.chainClientState.keyPressed) {
            stopViewer();
            return;
        }

        // ── Minesweeper mode: render flagged mine positions, skip ore-search preview. ──
        if (clientState.minerModeState.mainMode == 2 && clientState.minerModeState.specialMode == 0) {
            // If a normal ore-search was running, stop it and force a full rebuild.
            if (founder != null) {
                founder.interrupt();
                founder = null;
                foundQueue.clear();
                lastTarget = new Vector3i(Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE);
                lastMinesweeperVersion = -1;
            }
            renderMinesweeperMarks();
            return;
        }

        // ── Sudoku mode: render filled cell positions (board origins). ──
        if (clientState.minerModeState.mainMode == 2 && clientState.minerModeState.specialMode == 2) {
            if (founder != null) {
                founder.interrupt();
                founder = null;
                foundQueue.clear();
                lastTarget = new Vector3i(Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE);
                lastSudokuVersion = -1;
            }
            renderSudokuFills();
            return;
        }

        // ── Frozen mode: chain is active, just render the locked-in preview. ──
        ChainPreviewState previewState = previewController.getState();
        if (previewState.frozen) {
            doRender();
            return;
        }

        // ── Cached chain mode: render server-authoritative pre-calculated preview. ──
        if (clientState.minerModeState.isCachedChainMode()) {
            renderCachedPreview();
            return;
        }

        if (mc.objectMouseOver == null || mc.objectMouseOver.typeOfHit != MovingObjectPosition.MovingObjectType.BLOCK) {
            stopViewer();
            return;
        }

        Vector3i target = new Vector3i(mc.objectMouseOver.blockX, mc.objectMouseOver.blockY, mc.objectMouseOver.blockZ);
        if (!lastTarget.equals(target)) {
            restartViewer(mc, target);
            previewController.setTarget(target);
            lastTarget = new Vector3i(target);
        }

        drainQueue(mc);
        doRender();
    }

    private void restartViewer(Minecraft mc, Vector3i target) {
        stopViewer();
        searchComplete = false;
        spaceCalc.hasChange = false;
        spaceCalc.posSet.clear();
        spaceCalc.positions.clear();

        EntityPlayer player = mc.thePlayer;
        // Use preview-specific limits so the search stays responsive and GPU vertex data
        // stays small, independently of the (potentially larger) server mining limits.
        MinerConfig previewConfig = new MinerConfig();
        previewConfig.bigRadius = Config.previewBigRadius;
        previewConfig.blockLimit = Config.previewBlockLimit;
        founder = EZMiner.chainPlanningRuntimeFactory
            .createFounderForMode(clientState.minerModeState, target, foundQueue, player, previewConfig);
        if (founder != null) {
            founder.setSkipHarvestCheck(true);
            EZMiner.parallelTick.addNormalTask(founder);
        }
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
        lastMinesweeperVersion = -1;
        clientState.previewRenderedCount = 0;
        previewController.getState().renderedCount = 0;
        previewController.setTarget(null);
        // Reset so that pressing the key again while looking at the same block
        // correctly triggers restartViewer (lastTarget != any real block).
        lastTarget = new Vector3i(Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE);
    }

    private void drainQueue(Minecraft mc) {
        if (searchComplete) return;
        int n = 0;
        Vector3i p;
        EntityPlayer player = mc.thePlayer;
        // Render distance in blocks (chunks × 16). Blocks beyond this have no loaded chunk
        // data on the client and would produce null-pointer crashes in the GL pipeline.
        int renderDistBlocks = mc.gameSettings.renderDistanceChunks * 16;
        while ((p = foundQueue.poll()) != null) {
            if (player != null && withinRenderDist(p, player, renderDistBlocks)) {
                spaceCalc.add(p);
            }
            n++;
        }
        // Only mark complete once the founder has stopped AND the queue is fully drained.
        if (founder != null && founder.stopped.get() && foundQueue.isEmpty()) {
            searchComplete = true;
        }
        if (n > 0) {
            SpaceCalculator.VertexAndIndex vi = spaceCalc.getVertexAndIndex();
            lastIndexCount = vi.indices.length;
            renderCache.updateData(vi.vertices, vi.indices);
            clientState.previewRenderedCount = spaceCalc.positions.size();
            previewController.getState().renderedCount = clientState.previewRenderedCount;
        }
    }

    /** Returns true if {@code pos} is within {@code dist} blocks of the player on X and Z. */
    private static boolean withinRenderDist(Vector3i pos, EntityPlayer player, int dist) {
        int playerX = (int) Math.floor(player.posX);
        int playerZ = (int) Math.floor(player.posZ);
        return Math.abs(pos.x - playerX) <= dist && Math.abs(pos.z - playerZ) <= dist;
    }

    /**
     * Rebuilds the wireframe mesh from flagged minesweeper positions when the list has changed,
     * then renders the outlines in a distinct orange-red colour.
     */
    private void renderMinesweeperMarks() {
        int version = clientState.minesweeperFlaggedVersion;
        if (version != lastMinesweeperVersion) {
            spaceCalc.posSet.clear();
            spaceCalc.positions.clear();
            spaceCalc.hasChange = false;
            for (Vector3i pos : clientState.minesweeperFlaggedPositions) {
                spaceCalc.add(pos);
            }
            if (!spaceCalc.positions.isEmpty()) {
                SpaceCalculator.VertexAndIndex vi = spaceCalc.getVertexAndIndex();
                lastIndexCount = vi.indices.length;
                renderCache.updateData(vi.vertices, vi.indices);
            } else {
                lastIndexCount = 0;
            }
            // Update the HUD "client rendered blocks" counter with the flagged-mine count.
            clientState.previewRenderedCount = spaceCalc.positions.size();
            lastMinesweeperVersion = version;
        }
        doRenderMinesweeper();
    }

    /** Renders the minesweeper-flag wireframe using an orange-red colour to distinguish it from ore previews. */
    private void doRenderMinesweeper() {
        if (lastIndexCount <= 0) return;

        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        GL11.glPushMatrix();
        GL11.glTranslated(-RenderManager.renderPosX, -RenderManager.renderPosY, -RenderManager.renderPosZ);

        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_CULL_FACE);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glLineWidth(2.0F);
        GL11.glColor4f(1.0F, 0.3F, 0.1F, 0.9F);

        renderCache.render(lastIndexCount);

        GL11.glPopMatrix();
        GL11.glPopAttrib();
    }

    /**
     * Rebuilds the wireframe mesh from filled Sudoku cell positions when the list has changed,
     * then renders the outlines in a green colour to distinguish them from minesweeper marks.
     */
    private void renderSudokuFills() {
        int version = clientState.sudokuFilledVersion;
        if (version != lastSudokuVersion) {
            spaceCalc.posSet.clear();
            spaceCalc.positions.clear();
            spaceCalc.hasChange = false;
            for (Vector3i pos : clientState.sudokuFilledPositions) {
                spaceCalc.add(pos);
            }
            if (!spaceCalc.positions.isEmpty()) {
                SpaceCalculator.VertexAndIndex vi = spaceCalc.getVertexAndIndex();
                lastIndexCount = vi.indices.length;
                renderCache.updateData(vi.vertices, vi.indices);
            } else {
                lastIndexCount = 0;
            }
            clientState.previewRenderedCount = spaceCalc.positions.size();
            lastSudokuVersion = version;
        }
        doRenderSudoku();
    }

    /** Renders the Sudoku-fill wireframe using a green colour to distinguish it from minesweeper marks. */
    private void doRenderSudoku() {
        if (lastIndexCount <= 0) return;

        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        GL11.glPushMatrix();
        GL11.glTranslated(-RenderManager.renderPosX, -RenderManager.renderPosY, -RenderManager.renderPosZ);

        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_CULL_FACE);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glLineWidth(2.0F);
        GL11.glColor4f(0.3F, 1.0F, 0.3F, 0.9F); // green

        renderCache.render(lastIndexCount);

        GL11.glPopMatrix();
        GL11.glPopAttrib();
    }

    /**
     * Renders the preview wireframe by delegating to the active {@link BlockOutlineRenderStrategy}.
     *
     * <p>
     * The matrix is shifted by {@code -RenderManager.renderPos} so that world-space block
     * positions map directly onto the rendered scene.
     */
    private void doRender() {
        if (Config.renderStyle == 1) {
            doRenderModern();
        } else {
            doRenderNative();
        }
    }

    /** Native single-pass wireframe (original behaviour). */
    private void doRenderNative() {
        if (lastIndexCount <= 0) return;

        BlockOutlineRenderStrategy strategy = Config.renderStyle == 1 ? MODERN_RENDERER : NATIVE_RENDERER;

        GL11.glPushMatrix();
        GL11.glTranslated(-RenderManager.renderPosX, -RenderManager.renderPosY, -RenderManager.renderPosZ);
        strategy.render(renderCache, lastIndexCount);
        GL11.glPopMatrix();
    }

    /**
     * Modern two-pass rendering inspired by FTB-Ultimine.
     *
     * <p>
     * Pass 1 – depth-tested: draws visible outline edges as solid, opaque, thicker lines so
     * the preview hugs block surfaces correctly and has clear depth perception.
     *
     * <p>
     * Pass 2 – no depth test: draws the same edges with low alpha so the selection shape
     * is still readable through solid geometry (hidden-line ghost effect).
     */
    private void doRenderModern() {
        if (lastIndexCount <= 0) return;

        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        GL11.glPushMatrix();
        GL11.glTranslated(-RenderManager.renderPosX, -RenderManager.renderPosY, -RenderManager.renderPosZ);

        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_CULL_FACE);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

        // Pass 1: visible edges – depth-tested, solid, thick, bright white-cyan
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        GL11.glLineWidth(2.5F);
        GL11.glColor4f(0.8F, 1.0F, 1.0F, 1.0F);
        renderCache.render(lastIndexCount);

        // Pass 2: hidden edges – no depth test, translucent, thin, dim cyan
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glLineWidth(1.0F);
        GL11.glColor4f(0.25F, 0.9F, 1.0F, 0.2F);
        renderCache.render(lastIndexCount);

        GL11.glPopMatrix();
        GL11.glPopAttrib();
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

    // ── Cached chain preview (server-authoritative, decoupled from client-side search) ──

    /** Last cached preview version we've built mesh data for; -1 forces a rebuild. */
    private int lastCachedPreviewVersion = -1;

    /**
     * Renders the server-authoritative cached preview for cached chain sub-modes.
     *
     * <p>
     * Unlike the normal preview which runs its own client-side founder search,
     * cached chain modes rely on the server to pre-calculate and sync the block
     * list. This method rebuilds the wireframe mesh when a new cache version
     * arrives from the server, and draws the cached outlines directly.
     */
    private void renderCachedPreview() {
        // Stop any running normal-mode founder — cached mode doesn't use client-side search.
        if (founder != null) {
            founder.interrupt();
            founder = null;
            foundQueue.clear();
            searchComplete = false;
        }
        // Check for updated cache from the server.
        if (clientState.cachedPreviewVersion != lastCachedPreviewVersion) {
            lastCachedPreviewVersion = clientState.cachedPreviewVersion;
            List<Vector3i> positions = clientState.cachedPreviewPositions;
            if (positions != null && !positions.isEmpty()) {
                // Build mesh from cached positions.
                spaceCalc.posSet.clear();
                spaceCalc.positions.clear();
                spaceCalc.hasChange = false;
                for (Vector3i pos : positions) {
                    spaceCalc.add(pos);
                }
                if (!spaceCalc.positions.isEmpty()) {
                    SpaceCalculator.VertexAndIndex vi = spaceCalc.getVertexAndIndex();
                    lastIndexCount = vi.indices.length;
                    renderCache.updateData(vi.vertices, vi.indices);
                    clientState.previewRenderedCount = spaceCalc.positions.size();
                } else {
                    lastIndexCount = 0;
                }
            } else {
                // Empty cache — clear the preview.
                lastIndexCount = 0;
                clientState.previewRenderedCount = 0;
            }
        }
        doRender();
    }
}
