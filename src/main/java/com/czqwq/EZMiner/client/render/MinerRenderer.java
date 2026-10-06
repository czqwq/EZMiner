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
import com.czqwq.EZMiner.core.MinerModeState;
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

    // ── Render style strategies (one instance per style, stateless) ────────────
    private static final BlockOutlineRenderStrategy NATIVE_RENDERER = new NativeBlockOutlineRenderer();
    private static final BlockOutlineRenderStrategy MODERN_RENDERER = new ModernBlockOutlineRenderer();
    private static final BlockOutlineRenderStrategy RAINBOW_RENDERER = new RainbowBlockOutlineRenderer();
    private static final BlockOutlineRenderStrategy GRADIENT_RENDERER = new GradientBlockOutlineRenderer();

    // ── Render style constants (mirrors Config.renderStyle values) ────────────────
    private static final int STYLE_NATIVE = 0;
    private static final int STYLE_MODERN = 1;
    private static final int STYLE_MODERN_RAINBOW = 2;
    private static final int STYLE_MODERN_GRADIENT = 3;
    private static final int STYLE_OFF = 4;

    private Vector3i lastTarget = new Vector3i(Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE);
    /** Scratch for the per-frame aim-target comparison and the "n" counter removed below. */
    private final Vector3i scratchTarget = new Vector3i();
    private BasePositionFounder founder = null;
    private final LinkedBlockingQueue<Vector3i> foundQueue = new LinkedBlockingQueue<>();
    private boolean searchComplete = false;
    private int lastIndexCount = 0;
    /** Last minesweeperFlaggedVersion seen; used to detect when the flagged-mine list changed. */
    private int lastMinesweeperVersion = -1;
    /** Last sudokuFilledVersion seen; used to detect when the filled-cell list changed. */
    private int lastSudokuVersion = -1;
    /**
     * Mode fingerprint of the last preview search. Mode switches (while the chain
     * key stays held) do not change the aim target, so without this the previous
     * mode's founder preview would linger (e.g. a normal chain preview after
     * switching into planting mode).
     */
    private int lastPreviewModeFingerprint = Integer.MIN_VALUE;

    // ── Active render path (see noteRenderKind) ───────────────────────────────────────────
    private static final int KIND_NONE = -1;
    private static final int KIND_PREVIEW = 0;
    private static final int KIND_MINESWEEPER = 1;
    private static final int KIND_SUDOKU = 2;
    /**
     * Which render path owns the geometry currently uploaded to {@link #renderCache}. Minesweeper
     * marks, Sudoku fills and the chain preview all share one {@code SpaceCalculator} and one
     * VBO/EBO pair, so without this a path switch could keep drawing the previous path's geometry
     * (a Sudoku fill list rendered as orange minesweeper markers, and vice versa) until its own
     * version counter happened to change.
     */
    private int lastRenderKind = KIND_NONE;

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
            }
            noteRenderKind(KIND_MINESWEEPER);
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
            }
            noteRenderKind(KIND_SUDOKU);
            renderSudokuFills();
            return;
        }

        // Every remaining path renders the chain preview; entering it from a special mode must
        // rebuild the mesh instead of reusing the marks/fills still in the cache.
        noteRenderKind(KIND_PREVIEW);

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

        // A mode switch while holding the chain key leaves the aim target
        // unchanged — invalidate lastTarget so restartViewer runs and builds the
        // preview for the newly selected mode instead of lingering on the old one.
        int modeFingerprint = previewModeFingerprint(clientState.minerModeState);
        if (modeFingerprint != lastPreviewModeFingerprint) {
            lastPreviewModeFingerprint = modeFingerprint;
            lastTarget = new Vector3i(Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE);
        }

        if (mc.objectMouseOver == null || mc.objectMouseOver.typeOfHit != MovingObjectPosition.MovingObjectType.BLOCK) {
            stopViewer();
            return;
        }

        scratchTarget.set(mc.objectMouseOver.blockX, mc.objectMouseOver.blockY, mc.objectMouseOver.blockZ);
        if (!lastTarget.equals(scratchTarget)) {
            // restartViewer hands the position to the new founder, which keeps the reference, so
            // it gets its own copy — the scratch must never be retained.
            restartViewer(mc, new Vector3i(scratchTarget));
            lastTarget.set(scratchTarget);
        }

        drainQueue(mc);
        doRender();
    }

    /**
     * Records {@code kind} as the active render path and invalidates the per-path version counters
     * when it changed, so the next build of the new path always re-uploads its own geometry.
     *
     * <p>
     * {@link #lastIndexCount} is deliberately not reset here: every special path sets it as part of
     * its rebuild, and the preview path already invalidates its mesh through {@link #stopViewer()}
     * (called by {@link #restartViewer} on a mode-fingerprint change). Keeping it out also leaves a
     * frozen preview's locked-in geometry untouched.
     * </p>
     */
    private void noteRenderKind(int kind) {
        if (kind == lastRenderKind) return;
        lastRenderKind = kind;
        lastMinesweeperVersion = -1;
        lastSudokuVersion = -1;
        lastCachedPreviewVersion = -1;
    }

    /** Encodes all four mode indices into a single int for switch detection. */
    private static int previewModeFingerprint(MinerModeState state) {
        return (state.mainMode << 8) | (state.blastMode << 5) | (state.chainMode << 3) | state.specialMode;
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
        // Reset so that pressing the key again while looking at the same block
        // correctly triggers restartViewer (lastTarget != any real block).
        lastTarget = new Vector3i(Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE);
    }

    private void drainQueue(Minecraft mc) {
        if (searchComplete) return;
        Vector3i p;
        EntityPlayer player = mc.thePlayer;
        // Render distance in blocks (chunks × 16). Blocks beyond this have no loaded chunk
        // data on the client and would produce null-pointer crashes in the GL pipeline.
        int renderDistBlocks = mc.gameSettings.renderDistanceChunks * 16;
        while ((p = foundQueue.poll()) != null) {
            if (player != null && withinRenderDist(p, player, renderDistBlocks)) {
                spaceCalc.add(p);
            }
        }
        // Only mark complete once the founder has stopped AND the queue is fully drained.
        if (founder != null && founder.stopped.get() && foundQueue.isEmpty()) {
            searchComplete = true;
        }
        // SpaceCalculator.add() maintains the dirty flag, so a batch of positions that were all
        // filtered out (out of render distance / duplicates) no longer triggers a full mesh rebuild.
        if (spaceCalc.hasChange) {
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
     * Renders the preview wireframe, dispatching to the active
     * {@link BlockOutlineRenderStrategy}.
     *
     * <p>
     * The matrix is shifted by {@code -RenderManager.renderPos} so that world-space
     * block positions map directly onto the rendered scene. Each strategy receives
     * the pre-built cache plus the raw position list so per-block renderers can
     * do their own colouring.
     */
    private void doRender() {
        if (lastIndexCount <= 0) return;

        final BlockOutlineRenderStrategy strategy;
        switch (Config.renderStyle) {
            case STYLE_NATIVE:
                strategy = NATIVE_RENDERER;
                break;
            case STYLE_MODERN:
                strategy = MODERN_RENDERER;
                break;
            case STYLE_MODERN_RAINBOW:
                strategy = RAINBOW_RENDERER;
                break;
            case STYLE_MODERN_GRADIENT:
                strategy = GRADIENT_RENDERER;
                break;
            case STYLE_OFF:
                return; // Preview rendering is disabled.
            default:
                strategy = MODERN_RENDERER; // fallback to default
                break;
        }

        GL11.glPushMatrix();
        GL11.glTranslated(-RenderManager.renderPosX, -RenderManager.renderPosY, -RenderManager.renderPosZ);
        // Batch renderers (Native, Modern, Rainbow) use cache+indexCount.
        // Per-block renderers (Gradient) use positions and ignore cache.
        strategy.render(renderCache, lastIndexCount, spaceCalc.positions);
        GL11.glPopMatrix();
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
