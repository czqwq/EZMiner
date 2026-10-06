package com.czqwq.EZMiner.chain.execution;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.world.World;

import com.czqwq.EZMiner.EZMiner;
import com.czqwq.EZMiner.utils.MessageUtils;

/**
 * Optional LootGames minesweeper integration bridge.
 *
 * <p>
 * Every LootGames type is reached reflectively because the mod is an optional runtime dependency.
 * The bridge is stateless: the caller owns the {@link Mark} list and hands it in, so the same
 * instance serves a probe cycle, a re-validation cycle and a client resync.
 * </p>
 */
public class LootGamesMinesweeperBridge {

    private static final double DISTANCE_ROUNDING_PRECISION = 10.0;
    private volatile boolean compatibilityChecked = false;
    private boolean hasLootGamesApi = false;

    private Class<?> msMasterTileClass = null;
    private Method getGameMethod = null;
    private Method isBoardGeneratedMethod = null;
    private Method getBoardMethod = null;
    private Method boardSizeMethod = null;
    private Method boardGetTypeMethod = null;
    private Method boardIsHiddenMethod = null;
    private Method boardGetMarkMethod = null;
    private Method boardIsGeneratedMethod = null;
    private Method boardGetBombCountMethod = null;
    private Method getBoardOriginMethod = null;
    private Method getCurrentBoardSizeMethod = null;
    private Method getStageMethod = null;
    private Class<?> stageWaitingClass = null;
    private Method stageSwapFieldMarkMethod = null;
    /** Auto-reveal hook; {@code null} on a LootGames build without {@code StageWaiting.revealField}. */
    private Method stageRevealFieldMethod = null;
    /** Full tile-entity sync hook ({@code LootGame.saveAndSync}); {@code null} when unavailable. */
    private Method getSaveAndSyncMethod = null;
    private Constructor<?> pos2iConstructor = null;
    private Method blockPosGetXMethod = null;
    private Method blockPosGetYMethod = null;
    private Method blockPosGetZMethod = null;
    private Object bombTypeConstant = null;
    private Object noMarkConstant = null;
    private Object flagMarkConstant = null;

    public synchronized void checkCompatibility() {
        if (compatibilityChecked) return;
        try {
            msMasterTileClass = Class.forName("ru.timeconqueror.lootgames.common.block.tile.MSMasterTile");
            Class<?> gameMineSweeperClass = Class
                .forName("ru.timeconqueror.lootgames.minigame.minesweeper.GameMineSweeper");
            Class<?> msBoardClass = Class.forName("ru.timeconqueror.lootgames.minigame.minesweeper.MSBoard");
            Class<?> typeClass = Class.forName("ru.timeconqueror.lootgames.minigame.minesweeper.Type");
            Class<?> markClass = Class.forName("ru.timeconqueror.lootgames.minigame.minesweeper.Mark");
            Class<?> pos2iClass = Class.forName("ru.timeconqueror.lootgames.api.util.Pos2i");
            Class<?> blockPosClass = Class.forName("ru.timeconqueror.lootgames.utils.future.BlockPos");
            stageWaitingClass = Class
                .forName("ru.timeconqueror.lootgames.minigame.minesweeper.GameMineSweeper$StageWaiting");
            getGameMethod = msMasterTileClass.getMethod("getGame");
            isBoardGeneratedMethod = gameMineSweeperClass.getMethod("isBoardGenerated");
            getBoardMethod = gameMineSweeperClass.getMethod("getBoard");
            getStageMethod = gameMineSweeperClass.getMethod("getStage");
            boardSizeMethod = msBoardClass.getMethod("size");
            boardGetTypeMethod = msBoardClass.getMethod("getType", int.class, int.class);
            boardIsHiddenMethod = msBoardClass.getMethod("isHidden", int.class, int.class);
            boardGetMarkMethod = msBoardClass.getMethod("getMark", int.class, int.class);
            boardIsGeneratedMethod = msBoardClass.getMethod("isGenerated");
            boardGetBombCountMethod = msBoardClass.getMethod("getBombCount");
            getBoardOriginMethod = gameMineSweeperClass.getMethod("getBoardOrigin");
            getCurrentBoardSizeMethod = gameMineSweeperClass.getMethod("getCurrentBoardSize");
            stageSwapFieldMarkMethod = stageWaitingClass.getMethod("swapFieldMark", pos2iClass);
            pos2iConstructor = pos2iClass.getConstructor(int.class, int.class);
            blockPosGetXMethod = blockPosClass.getMethod("getX");
            blockPosGetYMethod = blockPosClass.getMethod("getY");
            blockPosGetZMethod = blockPosClass.getMethod("getZ");
            @SuppressWarnings({ "unchecked", "rawtypes" })
            Object bomb = Enum.valueOf((Class<? extends Enum>) typeClass, "BOMB");
            @SuppressWarnings({ "unchecked", "rawtypes" })
            Object noMark = Enum.valueOf((Class<? extends Enum>) markClass, "NO_MARK");
            @SuppressWarnings({ "unchecked", "rawtypes" })
            Object flag = Enum.valueOf((Class<? extends Enum>) markClass, "FLAG");
            bombTypeConstant = bomb;
            noMarkConstant = noMark;
            flagMarkConstant = flag;
            // Auto-reveal is an extra capability, not a prerequisite: a LootGames build without
            // StageWaiting.revealField must not switch the (essential) auto-flagging half off.
            try {
                stageRevealFieldMethod = stageWaitingClass.getMethod("revealField", EntityPlayerMP.class, pos2iClass);
            } catch (NoSuchMethodException e) {
                EZMiner.LOG.warn(
                    "EZMiner: LootGames StageWaiting.revealField() not found - minesweeper auto-reveal disabled.");
            }
            // Client-state repair hook; also an extra capability rather than a prerequisite.
            try {
                getSaveAndSyncMethod = gameMineSweeperClass.getMethod("saveAndSync");
            } catch (NoSuchMethodException e) {
                EZMiner.LOG.warn("EZMiner: LootGame.saveAndSync() not found - minesweeper client repair disabled.");
            }
            hasLootGamesApi = true;
            EZMiner.LOG.info("EZMiner: LootGames minesweeper API detected – special minesweeper mode enabled.");
        } catch (ClassNotFoundException e) {
            EZMiner.LOG.debug("EZMiner: LootGames not found – special minesweeper mode disabled.");
        } catch (Exception e) {
            EZMiner.LOG.warn("EZMiner: LootGames bridge init failed: {}", e.getMessage());
        }
        compatibilityChecked = true;
    }

    /**
     * One scan of {@code world.loadedTileEntityList} collecting every minesweeper board that is
     * currently generated and waiting for clicks.
     *
     * <p>
     * The scan copies the whole loaded-tile-entity list and reflects over every element, so callers
     * must cache the result (the handlers use a 500&nbsp;ms TTL) instead of calling it per tick.
     * </p>
     *
     * @param world the world to scan
     * @return the boards found; empty when LootGames is absent or no game is in {@code StageWaiting}
     */
    public List<BoardView> snapshotBoards(World world) {
        List<BoardView> views = new ArrayList<>();
        if (world == null) return views;
        if (!compatibilityChecked) checkCompatibility();
        if (!hasLootGamesApi || msMasterTileClass == null) return views;

        try {
            @SuppressWarnings("unchecked")
            List<TileEntity> loadedTileEntities = new ArrayList<>(world.loadedTileEntityList);
            for (TileEntity te : loadedTileEntities) {
                if (te == null || te.isInvalid() || te.getWorldObj() != world) continue;
                if (!msMasterTileClass.isInstance(te)) continue;

                Object game = getGameMethod.invoke(te);
                if (game == null) continue;
                if (!((Boolean) isBoardGeneratedMethod.invoke(game))) continue;
                Object stage = getStageMethod.invoke(game);
                if (stage == null || !stageWaitingClass.isInstance(stage)) continue;
                Object board = getBoardMethod.invoke(game);
                if (board == null) continue;
                Object origin = getBoardOriginMethod.invoke(game);
                if (origin == null) continue;

                views.add(
                    new BoardView(
                        world.provider.dimensionId + ":" + te.xCoord + ":" + te.yCoord + ":" + te.zCoord,
                        game,
                        board,
                        stage,
                        (Integer) boardSizeMethod.invoke(board),
                        (Integer) blockPosGetXMethod.invoke(origin),
                        (Integer) blockPosGetYMethod.invoke(origin),
                        (Integer) blockPosGetZMethod.invoke(origin)));
            }
        } catch (Exception e) {
            EZMiner.LOG.debug("EZMiner: LootGames minesweeper board scan failed: {}", e.getMessage());
        }
        return views;
    }

    /**
     * Drops every remembered flag that no longer describes a flagged bomb of the board it was made
     * on, removing the survivors' entries from {@code marks} in place.
     *
     * <p>
     * This is what keeps a marker wireframe from outliving its flag. A LootGames board is not a
     * fixed piece of furniture: completing a level calls {@code MSBoard.resetBoard(newSize, ...)}
     * and the next board is generated with a new size, while
     * {@code BoardLootGame.getBoardOrigin()} re-centres the current board inside the allocated
     * (stage-4) area — the origin moves by exactly one block per level. A stored world position
     * therefore describes a *different* cell one level later, which is what used to make the
     * orange boxes appear to drift by one cell and linger on the next board. A flag the player
     * right-clicked away and a bomb revealed by an explosion are caught by the same check.
     * </p>
     *
     * @param views the boards from {@link #snapshotBoards(World)}
     * @param marks the caller's mark list, modified in place
     * @return the number of marks removed
     */
    public int invalidateStaleMarks(List<BoardView> views, List<Mark> marks) {
        if (marks.isEmpty()) return 0;
        Map<String, BoardView> byKey = new HashMap<>();
        for (BoardView view : views) {
            byKey.put(view.key, view);
        }
        int removed = 0;
        for (int i = marks.size() - 1; i >= 0; i--) {
            if (!isMarkAlive(marks.get(i), byKey.get(marks.get(i).boardKey))) {
                marks.remove(i);
                removed++;
            }
        }
        return removed;
    }

    /** True when {@code mark} still is a hidden, flagged bomb of the current board it belongs to. */
    private boolean isMarkAlive(Mark mark, BoardView view) {
        if (view == null) return false;
        // The origin is derived from the current board size, so comparing it detects a board that
        // was re-created (level completed) even while every cell would still be in range.
        if (view.size != mark.boardSize || view.originX != mark.originX
            || view.originY != mark.originY
            || view.originZ != mark.originZ) {
            return false;
        }
        try {
            if (!((Boolean) boardIsHiddenMethod.invoke(view.board, mark.cellX, mark.cellZ))) return false;
            if (boardGetTypeMethod.invoke(view.board, mark.cellX, mark.cellZ) != bombTypeConstant) return false;
            return boardGetMarkMethod.invoke(view.board, mark.cellX, mark.cellZ) == flagMarkConstant;
        } catch (Exception e) {
            EZMiner.LOG.debug("EZMiner: LootGames minesweeper mark validation failed: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Flags the nearest still-unflagged bomb and appends it to {@code marks}.
     *
     * <p>
     * Exactly one bomb is flagged per call, and nothing is revealed here: the board is opened only
     * once every bomb carries a flag (see {@link #areAllBombsFlagged} and
     * {@link #revealAllSafeCells}).
     * </p>
     *
     * @param player the player whose proximity selects the bomb
     * @param views  the boards from {@link #snapshotBoards(World)}
     * @param marks  the caller's mark list; the new mark is appended on success
     * @return the newly flagged mark, or {@code null} when there was nothing to flag
     */
    public Mark flagNearestBomb(EntityPlayerMP player, List<BoardView> views, List<Mark> marks) {
        if (player == null || player.worldObj == null) return null;
        if (!compatibilityChecked) checkCompatibility();
        if (!hasLootGamesApi || msMasterTileClass == null) return null;

        Candidate best = null;
        try {
            for (BoardView view : views) {
                for (int x = 0; x < view.size; x++) {
                    for (int z = 0; z < view.size; z++) {
                        if (boardGetTypeMethod.invoke(view.board, x, z) != bombTypeConstant) continue;
                        if (!((Boolean) boardIsHiddenMethod.invoke(view.board, x, z))) continue;
                        Object mark = boardGetMarkMethod.invoke(view.board, x, z);
                        // Already flagged (by EZMiner or by the player) — nothing to do. A
                        // QUESTION_MARK is an unresolved note on a real bomb and must still be
                        // flagged, otherwise the "every bomb carries a flag" phase could never
                        // complete and the board would never be opened.
                        if (mark == flagMarkConstant) continue;
                        boolean questionMarked = mark != noMarkConstant;

                        int worldX = view.originX + x;
                        int worldY = view.originY;
                        int worldZ = view.originZ + z;
                        if (containsMark(marks, worldX, worldY, worldZ)) continue;
                        double distSq = player.getDistanceSq(worldX + 0.5, worldY + 0.5, worldZ + 0.5);
                        if (best == null || distSq < best.distanceSq) {
                            best = new Candidate(view, x, z, worldX, worldY, worldZ, distSq, questionMarked);
                        }
                    }
                }
            }
        } catch (Exception e) {
            EZMiner.LOG.debug("EZMiner: LootGames minesweeper probe failed: {}", e.getMessage());
            return null;
        }
        if (best == null) return null;

        // The board list may be up to the caller's TTL old, and a probe mutates the board. A level
        // completed inside that window re-creates the board at a new origin/size, so the candidate
        // cell would be acted on with the wrong geometry — skip the cycle and let the next scan see
        // the new board instead.
        if (!isGeometryCurrent(best.view)) {
            EZMiner.LOG.debug("EZMiner: LootGames minesweeper board changed during probe - skipping this cycle.");
            return null;
        }

        try {
            stageSwapFieldMarkMethod.invoke(best.view.stage, toGamePos(best.cellX, best.cellZ));
            if (best.questionMarked) {
                // Mark#getNext cycles QUESTION_MARK -> NO_MARK -> FLAG, so a question-marked bomb
                // needs one extra cycle to actually end up flagged.
                stageSwapFieldMarkMethod.invoke(best.view.stage, toGamePos(best.cellX, best.cellZ));
            }
        } catch (Exception e) {
            EZMiner.LOG.debug("EZMiner: LootGames minesweeper mark failed: {}", e.getMessage());
            return null;
        }

        Mark mark = new Mark(
            best.view.key,
            best.x,
            best.y,
            best.z,
            best.cellX,
            best.cellZ,
            best.view.size,
            best.view.originX,
            best.view.originY,
            best.view.originZ);
        marks.add(mark);

        float distance = (float) (Math.round(Math.sqrt(best.distanceSq) * DISTANCE_ROUNDING_PRECISION)
            / DISTANCE_ROUNDING_PRECISION);
        MessageUtils.serverSendPlayerMessage(
            new ChatComponentTranslation(
                "ezminer.message.special.minesweeper.marked",
                best.x,
                best.y,
                best.z,
                distance),
            player.getUniqueID());
        return mark;
    }

    /**
     * True when every bomb of the board currently carries a flag, i.e. the flagging phase is over.
     *
     * <p>
     * Counted on the live board (bombs are never revealed while the game waits for clicks, and a
     * mis-flag on a safe cell is not counted because the cell type is checked first), so a
     * player-placed flag for a real bomb counts too and a partially flagged board never qualifies.
     * </p>
     */
    public boolean areAllBombsFlagged(BoardView view) {
        if (view == null) return false;
        try {
            if (!((Boolean) boardIsGeneratedMethod.invoke(view.board))) return false;
            int bombCount = (Integer) boardGetBombCountMethod.invoke(view.board);
            if (bombCount <= 0) return false;
            int flagged = 0;
            for (int x = 0; x < view.size; x++) {
                for (int z = 0; z < view.size; z++) {
                    if (boardGetTypeMethod.invoke(view.board, x, z) != bombTypeConstant) continue;
                    if (boardGetMarkMethod.invoke(view.board, x, z) == flagMarkConstant) flagged++;
                }
            }
            return flagged == bombCount;
        } catch (Exception e) {
            EZMiner.LOG.debug("EZMiner: LootGames minesweeper flag-completion check failed: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Opens every hidden, non-bomb cell of the board. Called once the whole board is flagged.
     *
     * <p>
     * This is the second half of the mode's two-phase design: flag every bomb first (one per probe,
     * see {@link #flagNearestBomb}), then open the safe side in one go. Cells are revealed directly
     * rather than through the flagged bomb's neighbours because LootGames only cascades out of an
     * {@code EMPTY} cell, and an {@code EMPTY} cell can never be a neighbour of a bomb — a
     * neighbour-only reveal left every blank pocket the first cascade did not reach dark forever.
     * The engine's own cascade still runs for every {@code EMPTY} cell, and cells it already opened
     * are skipped ({@code isHidden} is re-read for every cell).
     * </p>
     *
     * <p>
     * Bombs are never revealed (that would detonate the board), and every step re-checks the live
     * board because a reveal can complete the level — LootGames then resets the board to
     * {@code null} inside the same call stack, after which any further board access would throw.
     * Marks are deliberately not consulted: a mark on a cell the server knows to be safe (a
     * mis-flag or a question mark) must not keep that cell dark.
     * </p>
     */
    public void revealAllSafeCells(EntityPlayerMP player, BoardView view) {
        if (view == null || stageRevealFieldMethod == null) return;
        for (int x = 0; x < view.size; x++) {
            for (int z = 0; z < view.size; z++) {
                try {
                    // The board is gone once the level was completed by a previous reveal.
                    if (!((Boolean) boardIsGeneratedMethod.invoke(view.board))) return;
                    if (!((Boolean) boardIsHiddenMethod.invoke(view.board, x, z))) continue;
                    if (boardGetTypeMethod.invoke(view.board, x, z) == bombTypeConstant) continue;
                    stageRevealFieldMethod.invoke(view.stage, player, toGamePos(x, z));
                } catch (Exception e) {
                    EZMiner.LOG.debug("EZMiner: LootGames minesweeper auto-reveal failed: {}", e.getMessage());
                    return;
                }
            }
        }
    }

    /**
     * Pushes the authoritative board state to the client once.
     *
     * <p>
     * LootGames' client only learns about the board from {@code generateBoard}'s
     * {@code SPMSGenBoard} packet and from a full tile-entity sync ({@code saveAndSync}). Those two
     * travel on different channels, and an auto-solved level ends almost immediately after the deal
     * — so the level-end sync ("not generated") can land after the next deal's
     * {@code SPMSGenBoard} ("generated"). The client then renders the board as unopened forever:
     * {@code onClick} keeps taking the "already generated" branch on the server (so it never sends a
     * new {@code SPMSGenBoard}), and the player cannot interact with the board at all. Re-sending
     * the true state is the repair, and it is also harmless when the client was in sync.
     * </p>
     */
    public void syncClient(BoardView view) {
        if (view == null || getSaveAndSyncMethod == null) return;
        try {
            getSaveAndSyncMethod.invoke(view.game);
        } catch (Exception e) {
            EZMiner.LOG.debug("EZMiner: LootGames minesweeper client re-sync failed: {}", e.getMessage());
        }
    }

    /** True while {@code view}'s board is still generated (a reveal may have completed the level). */
    public boolean isStillGenerated(BoardView view) {
        if (view == null || boardIsGeneratedMethod == null) return false;
        try {
            return (Boolean) boardIsGeneratedMethod.invoke(view.board);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Deterministic signature of the board set in a snapshot: identity of every active master tile
     * plus its current size and origin. Any change — a freshly dealt board, a completed level
     * (new size/origin), a new attempt, a new game or the game ending — produces a different value.
     */
    public static String boardSignature(List<BoardView> views) {
        if (views.isEmpty()) return "";
        List<String> tokens = new ArrayList<>(views.size());
        for (BoardView view : views) {
            tokens.add(view.key + "|" + view.size + "|" + view.originX + "," + view.originY + "," + view.originZ);
        }
        Collections.sort(tokens);
        return String.join(";", tokens);
    }

    /** True when the board's live size and origin still match the snapshot the cell was read from. */
    private boolean isGeometryCurrent(BoardView view) {
        try {
            Object origin = getBoardOriginMethod.invoke(view.game);
            return ((Integer) getCurrentBoardSizeMethod.invoke(view.game)).intValue() == view.size
                && ((Integer) blockPosGetXMethod.invoke(origin)).intValue() == view.originX
                && ((Integer) blockPosGetYMethod.invoke(origin)).intValue() == view.originY
                && ((Integer) blockPosGetZMethod.invoke(origin)).intValue() == view.originZ;
        } catch (Exception e) {
            EZMiner.LOG.debug("EZMiner: LootGames minesweeper geometry re-check failed: {}", e.getMessage());
            return false;
        }
    }

    private Object toGamePos(int x, int z) throws Exception {
        return pos2iConstructor.newInstance(x, z);
    }

    private static boolean containsMark(List<Mark> marks, int x, int y, int z) {
        for (Mark mark : marks) {
            if (mark.x == x && mark.y == y && mark.z == z) return true;
        }
        return false;
    }

    /** Snapshot of one minesweeper board that is currently generated and waiting for clicks. */
    public static final class BoardView {

        /** {@code dimension:masterX:masterY:masterZ} — stable identity of the master tile. */
        public final String key;
        /** The live {@code GameMineSweeper}, for re-checking the geometry before a probe writes. */
        public final Object game;
        /** The live {@code MSBoard}; read on every validation instead of caching cell state. */
        public final Object board;
        /** The live {@code GameMineSweeper.StageWaiting} the board belongs to. */
        public final Object stage;
        public final int size;
        public final int originX;
        public final int originY;
        public final int originZ;

        private BoardView(String key, Object game, Object board, Object stage, int size, int originX, int originY,
            int originZ) {
            this.key = key;
            this.game = game;
            this.board = board;
            this.stage = stage;
            this.size = size;
            this.originX = originX;
            this.originY = originY;
            this.originZ = originZ;
        }
    }

    /**
     * A bomb flagged by EZMiner, remembered with everything needed to re-check it against the live
     * board (see {@link #invalidateStaleMarks}).
     */
    public static final class Mark {

        /** Identity of the board this flag was made on. */
        public final String boardKey;
        /** World position of the flagged bomb, as sent to the client. */
        public final int x;
        public final int y;
        public final int z;
        /** Board cell of the flag, and the board geometry it was recorded with. */
        public final int cellX;
        public final int cellZ;
        public final int boardSize;
        public final int originX;
        public final int originY;
        public final int originZ;

        private Mark(String boardKey, int x, int y, int z, int cellX, int cellZ, int boardSize, int originX,
            int originY, int originZ) {
            this.boardKey = boardKey;
            this.x = x;
            this.y = y;
            this.z = z;
            this.cellX = cellX;
            this.cellZ = cellZ;
            this.boardSize = boardSize;
            this.originX = originX;
            this.originY = originY;
            this.originZ = originZ;
        }
    }

    /** Nearest-bomb candidate for one probe cycle. */
    private static final class Candidate {

        private final BoardView view;
        private final int cellX;
        private final int cellZ;
        private final int x;
        private final int y;
        private final int z;
        private final double distanceSq;
        /** True when the cell carried a QUESTION_MARK, which needs one extra mark cycle to flag. */
        private final boolean questionMarked;

        private Candidate(BoardView view, int cellX, int cellZ, int x, int y, int z, double distanceSq,
            boolean questionMarked) {
            this.view = view;
            this.cellX = cellX;
            this.cellZ = cellZ;
            this.x = x;
            this.y = y;
            this.z = z;
            this.distanceSq = distanceSq;
            this.questionMarked = questionMarked;
        }
    }
}
