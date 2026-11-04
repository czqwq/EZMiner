package com.czqwq.EZMiner.chain.execution;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.world.World;

import org.joml.Vector3i;

import com.czqwq.EZMiner.EZMiner;
import com.czqwq.EZMiner.utils.MessageUtils;

/**
 * Optional LootGames Sudoku integration bridge.
 *
 * <p>
 * Uses reflection to interact with the LootGames Sudoku API so that EZMiner has no
 * compile-time dependency on LootGames. When LootGames is not installed the bridge
 * degrades gracefully (all methods return {@code null} or an empty list).
 * </p>
 *
 * <p>
 * The bridge is stateless: {@link #snapshotBoards(World)} produces immutable
 * {@link BoardView}s and the caller owns the remembered fills, so the same instance serves a
 * probe cycle, a re-validation cycle and a client re-sync. Every board read happens on the
 * server thread, because the handler only calls in from its per-tick work.
 * </p>
 */
public class LootGamesSudokuBridge {

    private volatile boolean compatibilityChecked = false;
    private boolean hasLootGamesApi = false;

    // --- Class references ---
    private Class<?> sudokuTileClass = null;
    private Class<?> gameSudokuClass = null;
    private Class<?> sudokuBoardClass = null;
    private Class<?> pos2iClass = null;
    private Class<?> spSyncCellClass = null;
    private Class<?> stageWaitingClass = null;
    private Class<?> blockPosClass = null;
    private Class<?> iserverGamePacketClass = null;

    // --- Game access methods ---
    private Method getGameMethod = null;
    private Method getBoardMethod = null;
    private Method getBoardOriginMethod = null;
    private Method getStageMethod = null;
    private Method sendUpdatePacketToNearbyMethod = null;
    private Method saveMethod = null;
    private Method onLevelSuccessfullyFinishedMethod = null;

    // --- Board methods / fields ---
    private Method boardIsGeneratedMethod = null;
    private Method boardCheckWinMethod = null;
    private Method boardGetPlayerValueMethod = null;
    private Field boardPlayerField = null;
    private Field boardSolutionField = null;
    private Field boardPuzzleField = null;
    /** {@code SudokuBoard.SIZE}; LootGames' board is a fixed square grid (9×9 by default). */
    private int boardSize = 9;

    // --- Pos2i ---
    private Constructor<?> pos2iConstructor = null;

    // --- SPSSyncCell ---
    private Constructor<?> spSyncCellConstructor = null;

    // --- BlockPos ---
    private Method blockPosGetXMethod = null;
    private Method blockPosGetYMethod = null;
    private Method blockPosGetZMethod = null;

    public synchronized void checkCompatibility() {
        if (compatibilityChecked) return;
        try {
            sudokuTileClass = Class.forName("ru.timeconqueror.lootgames.common.block.tile.SudokuTile");
            gameSudokuClass = Class.forName("ru.timeconqueror.lootgames.minigame.sudoku.GameSudoku");
            sudokuBoardClass = Class.forName("ru.timeconqueror.lootgames.minigame.sudoku.SudokuBoard");
            pos2iClass = Class.forName("ru.timeconqueror.lootgames.api.util.Pos2i");
            spSyncCellClass = Class.forName("ru.timeconqueror.lootgames.common.packet.game.sudoku.SPSSyncCell");
            stageWaitingClass = Class.forName("ru.timeconqueror.lootgames.minigame.sudoku.GameSudoku$StageWaiting");
            blockPosClass = Class.forName("ru.timeconqueror.lootgames.utils.future.BlockPos");
            iserverGamePacketClass = Class.forName("ru.timeconqueror.lootgames.api.packet.IServerGamePacket");

            // GameMasterTile.getGame()
            getGameMethod = sudokuTileClass.getSuperclass()
                .getMethod("getGame");
            // GameSudoku.getBoard() (Lombok @Getter)
            getBoardMethod = gameSudokuClass.getMethod("getBoard");
            // BoardLootGame.getBoardOrigin()
            getBoardOriginMethod = gameSudokuClass.getSuperclass()
                .getMethod("getBoardOrigin");
            // LootGame.getStage()
            getStageMethod = gameSudokuClass.getSuperclass()
                .getSuperclass()
                .getMethod("getStage");
            // LootGame.sendUpdatePacketToNearby(IServerGamePacket)
            sendUpdatePacketToNearbyMethod = gameSudokuClass.getSuperclass()
                .getSuperclass()
                .getMethod("sendUpdatePacketToNearby", iserverGamePacketClass);
            // LootGame.save()
            saveMethod = gameSudokuClass.getSuperclass()
                .getSuperclass()
                .getMethod("save");
            // GameSudoku.onLevelSuccessfullyFinished()
            onLevelSuccessfullyFinishedMethod = gameSudokuClass.getMethod("onLevelSuccessfullyFinished");

            // SudokuBoard methods
            boardIsGeneratedMethod = sudokuBoardClass.getMethod("isGenerated");
            boardCheckWinMethod = sudokuBoardClass.getMethod("checkWin");
            boardGetPlayerValueMethod = sudokuBoardClass.getMethod("getPlayerValue", pos2iClass);
            boardPlayerField = sudokuBoardClass.getField("player");
            boardSolutionField = sudokuBoardClass.getField("solution");
            boardPuzzleField = sudokuBoardClass.getField("puzzle");
            try {
                boardSize = (Integer) sudokuBoardClass.getField("SIZE")
                    .get(null);
            } catch (Exception ignored) {
                boardSize = 9;
            }
            if (boardSize <= 0) boardSize = 9;

            // Pos2i(int, int)
            pos2iConstructor = pos2iClass.getConstructor(int.class, int.class);

            // SPSSyncCell(Pos2i, int)
            spSyncCellConstructor = spSyncCellClass.getConstructor(pos2iClass, int.class);

            // BlockPos
            blockPosGetXMethod = blockPosClass.getMethod("getX");
            blockPosGetYMethod = blockPosClass.getMethod("getY");
            blockPosGetZMethod = blockPosClass.getMethod("getZ");

            hasLootGamesApi = true;
            EZMiner.LOG.info("EZMiner: LootGames Sudoku API detected – Sudoku special mode enabled.");
        } catch (ClassNotFoundException e) {
            EZMiner.LOG.debug("EZMiner: LootGames not found – Sudoku special mode disabled.");
        } catch (Exception e) {
            EZMiner.LOG.warn("EZMiner: LootGames Sudoku bridge init failed: {}", e.getMessage());
        }
        compatibilityChecked = true;
    }

    /**
     * One scan of {@code world.loadedTileEntityList} collecting every Sudoku board that is
     * currently generated and waiting for clicks.
     *
     * <p>
     * The scan copies the whole loaded-tile-entity list and reflects over every element, so
     * callers must cache the result (the handler uses a 500&nbsp;ms TTL) instead of calling it
     * per tick.
     * </p>
     *
     * @param world the world to scan
     * @return the boards found; empty when LootGames is absent or no board is in {@code StageWaiting}
     */
    public List<BoardView> snapshotBoards(World world) {
        List<BoardView> views = new ArrayList<>();
        if (world == null) return views;
        if (!compatibilityChecked) checkCompatibility();
        if (!hasLootGamesApi || sudokuTileClass == null) return views;

        try {
            @SuppressWarnings("unchecked")
            List<TileEntity> loadedTileEntities = new ArrayList<>(world.loadedTileEntityList);
            for (TileEntity te : loadedTileEntities) {
                if (te == null || te.isInvalid() || te.getWorldObj() != world) continue;
                if (!sudokuTileClass.isInstance(te)) continue;

                Object game = getGameMethod.invoke(te);
                if (game == null) continue;
                Object board = getBoardMethod.invoke(game);
                if (board == null) continue;
                if (!((Boolean) boardIsGeneratedMethod.invoke(board))) continue;
                Object stage = getStageMethod.invoke(game);
                if (stage == null || !stageWaitingClass.isInstance(stage)) continue;
                Object origin = getBoardOriginMethod.invoke(game);
                if (origin == null) continue;

                views.add(
                    new BoardView(
                        world.provider.dimensionId,
                        te.xCoord + ":" + te.yCoord + ":" + te.zCoord,
                        game,
                        board,
                        stage,
                        boardSize,
                        (Integer) blockPosGetXMethod.invoke(origin),
                        (Integer) blockPosGetYMethod.invoke(origin),
                        (Integer) blockPosGetZMethod.invoke(origin),
                        solutionHash(board)));
            }
        } catch (Exception e) {
            EZMiner.LOG.debug("EZMiner: LootGames Sudoku board scan failed: {}", e.getMessage());
        }
        return views;
    }

    /**
     * Deterministic fingerprint of the board set in a snapshot: the identity/origin of every
     * waiting board plus a hash of its solution grid. A freshly dealt board or a completed level
     * changes it; a wrong-answer retry does not, because LootGames keeps the puzzle and solution
     * and only resets the player grid (that case is caught by {@link #invalidateStaleFills}).
     */
    public static String boardFingerprint(List<BoardView> views) {
        if (views.isEmpty()) return "";
        List<String> tokens = new ArrayList<>(views.size());
        for (BoardView view : views) {
            tokens.add(view.fingerprint());
        }
        Collections.sort(tokens);
        return String.join(";", tokens);
    }

    /**
     * Drops every remembered fill that no longer describes a correctly filled cell of a live
     * board, removing the stale entries from {@code filledCells} in place.
     *
     * <p>
     * This is what keeps the assistant from stalling after a wrong submission. LootGames'
     * {@code handleEndGameCheck} answers a wrong answer with {@code SudokuBoard.resetPlayer()}
     * (the default {@code clear_on_wrong_answer=ALL} mode) while the puzzle and solution stay
     * identical, so the board fingerprint does not change — and every cell EZMiner had already
     * filled is silently emptied. Without this re-validation those cells stay in the memo and are
     * skipped by {@link #fillNearestCell} forever, so the board can never be completed again.
     * </p>
     *
     * @param views       the boards from {@link #snapshotBoards(World)}
     * @param filledCells the caller's memo ({@code key -> world cell position}), modified in place
     * @return the number of entries removed
     */
    public int invalidateStaleFills(List<BoardView> views, Map<String, Vector3i> filledCells) {
        if (filledCells.isEmpty()) return 0;
        int removed = 0;
        Iterator<Map.Entry<String, Vector3i>> iterator = filledCells.entrySet()
            .iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, Vector3i> entry = iterator.next();
            if (!isFillStillCorrect(views, entry.getKey(), entry.getValue())) {
                iterator.remove();
                removed++;
            }
        }
        return removed;
    }

    /** True when {@code pos} still holds {@code solution} in the live board that owns it. */
    private boolean isFillStillCorrect(List<BoardView> views, String key, Vector3i pos) {
        for (BoardView view : views) {
            if (!key.startsWith(view.dimensionPrefix)) continue;
            int cellX = pos.x - view.originX;
            int cellZ = pos.z - view.originZ;
            if (pos.y != view.originY) continue;
            if (cellX < 0 || cellZ < 0 || cellX >= view.size || cellZ >= view.size) continue;
            try {
                Integer[][] playerGrid = (Integer[][]) boardPlayerField.get(view.board);
                Integer[][] solutionGrid = (Integer[][]) boardSolutionField.get(view.board);
                Integer playerValue = playerGrid[cellX][cellZ];
                Integer solutionValue = solutionGrid[cellX][cellZ];
                return playerValue != null && solutionValue != null
                    && playerValue != 0
                    && playerValue.equals(solutionValue);
            } catch (Exception e) {
                EZMiner.LOG.debug("EZMiner: LootGames Sudoku fill validation failed: {}", e.getMessage());
                return false;
            }
        }
        return false;
    }

    /**
     * Finds the nearest not-yet-correct cell of any waiting board and fills it with the
     * solution value. Returns the world position of the filled cell, or {@code null} when
     * there was nothing to fill.
     *
     * <p>
     * Clue cells are never written, and a cell whose live value already equals the solution is
     * skipped by reading the board itself — not by trusting the memo — so a board reset by
     * LootGames is refilled on the next pass even if the memo had not been pruned yet.
     * </p>
     *
     * @param player      the player whose proximity selects the cell
     * @param views       the boards from {@link #snapshotBoards(World)}
     * @param filledCells the caller's memo; the newly filled cell is added on success
     * @return the world position of the filled cell, or {@code null}
     */
    public Vector3i fillNearestCell(EntityPlayerMP player, List<BoardView> views, Map<String, Vector3i> filledCells) {
        if (player == null || player.worldObj == null) return null;
        if (!compatibilityChecked) checkCompatibility();
        if (!hasLootGamesApi || sudokuTileClass == null) return null;

        int dimensionId = player.dimension;
        FillCandidate best = null;
        try {
            for (BoardView view : views) {
                Integer[][] playerGrid = (Integer[][]) boardPlayerField.get(view.board);
                Integer[][] solutionGrid = (Integer[][]) boardSolutionField.get(view.board);
                Integer[][] puzzleGrid = (Integer[][]) boardPuzzleField.get(view.board);

                for (int x = 0; x < view.size; x++) {
                    for (int y = 0; y < view.size; y++) {
                        // Skip clue cells (pre-filled by the puzzle)
                        if (puzzleGrid[x][y] != null && puzzleGrid[x][y] != 0) continue;

                        int solutionValue = solutionGrid[x][y] != null ? solutionGrid[x][y] : 0;
                        if (solutionValue == 0) continue;

                        int playerValue = playerGrid[x][y] != null ? playerGrid[x][y] : 0;
                        if (playerValue == solutionValue) continue;

                        int worldX = view.originX + x;
                        int worldY = view.originY;
                        int worldZ = view.originZ + y;
                        String key = dimensionId + ":" + worldX + ":" + worldY + ":" + worldZ;
                        if (filledCells.containsKey(key)) continue;

                        double distSq = player.getDistanceSq(worldX + 0.5, worldY + 0.5, worldZ + 0.5);
                        if (best == null || distSq < best.distanceSq) {
                            best = new FillCandidate(view, x, y, solutionValue, worldX, worldY, worldZ, key, distSq);
                        }
                    }
                }
            }
        } catch (Exception e) {
            EZMiner.LOG.debug("EZMiner: LootGames Sudoku probe failed: {}", e.getMessage());
            return null;
        }
        if (best == null) return null;

        // The board list may be up to the caller's TTL old, and a fill mutates the board. A game
        // that ended (or was re-dealt) inside that window must not be written to, so re-check the
        // live stage/generated state before touching the cell.
        if (!isViewCurrent(best.view)) {
            EZMiner.LOG.debug("EZMiner: LootGames Sudoku board changed during probe - skipping this cycle.");
            return null;
        }

        try {
            // Set player value directly (the puzzle guard is already applied above).
            Integer[][] playerGrid = (Integer[][]) boardPlayerField.get(best.view.board);
            playerGrid[best.cellX][best.cellY] = best.solutionValue;

            // Sync to clients
            Object pos2i = pos2iConstructor.newInstance(best.cellX, best.cellY);
            int playerValue = (Integer) boardGetPlayerValueMethod.invoke(best.view.board, pos2i);
            Object syncPacket = spSyncCellConstructor.newInstance(pos2i, playerValue);
            sendUpdatePacketToNearbyMethod.invoke(best.view.game, syncPacket);
            saveMethod.invoke(best.view.game);

            // Check win
            boolean won = (Boolean) boardCheckWinMethod.invoke(best.view.board);
            if (won) {
                onLevelSuccessfullyFinishedMethod.invoke(best.view.game);
            }

            Vector3i filledPos = new Vector3i(best.x, best.y, best.z);
            filledCells.put(best.key, filledPos);
            MessageUtils.serverSendPlayerMessage(
                new ChatComponentTranslation(
                    "ezminer.message.special.sudoku.filled",
                    best.cellX + 1,
                    best.cellY + 1,
                    best.solutionValue),
                player.getUniqueID());
            return filledPos;
        } catch (Exception e) {
            EZMiner.LOG.debug("EZMiner: LootGames Sudoku fill failed: {}", e.getMessage());
            return null;
        }
    }

    /** True when the live game still is the same generated {@code StageWaiting} board. */
    private boolean isViewCurrent(BoardView view) {
        try {
            Object stage = getStageMethod.invoke(view.game);
            if (stage != view.stage) return false;
            return (Boolean) boardIsGeneratedMethod.invoke(view.board);
        } catch (Exception e) {
            EZMiner.LOG.debug("EZMiner: LootGames Sudoku board re-check failed: {}", e.getMessage());
            return false;
        }
    }

    /** Best-effort hash of a board's full solution grid; used to detect a re-dealt board. */
    private int solutionHash(Object board) throws IllegalAccessException {
        Integer[][] solutionGrid = (Integer[][]) boardSolutionField.get(board);
        int hash = 17;
        for (int x = 0; x < boardSize; x++) {
            for (int y = 0; y < boardSize; y++) {
                Integer value = solutionGrid[x] != null ? solutionGrid[x][y] : null;
                hash = hash * 31 + (value == null ? 0 : value);
            }
        }
        return hash;
    }

    /** Snapshot of one Sudoku board that is currently generated and waiting for clicks. */
    public static final class BoardView {

        /** Dimension the board lives in. */
        public final int dimensionId;
        /** {@code masterX:masterY:masterZ} — stable identity within the dimension. */
        public final String masterKey;
        /** {@code dimension:} — prefix used to attribute a remembered fill to this board. */
        public final String dimensionPrefix;
        /** The live {@code GameSudoku}. */
        public final Object game;
        /** The live {@code SudokuBoard}. */
        public final Object board;
        /** The live {@code GameSudoku.StageWaiting} the board belongs to. */
        public final Object stage;
        public final int size;
        public final int originX;
        public final int originY;
        public final int originZ;
        /** Hash of the solution grid; changes when LootGames re-deals the board. */
        public final int solutionHash;

        private BoardView(int dimensionId, String masterKey, Object game, Object board, Object stage, int size,
            int originX, int originY, int originZ, int solutionHash) {
            this.dimensionId = dimensionId;
            this.masterKey = masterKey;
            this.dimensionPrefix = dimensionId + ":";
            this.game = game;
            this.board = board;
            this.stage = stage;
            this.size = size;
            this.originX = originX;
            this.originY = originY;
            this.originZ = originZ;
            this.solutionHash = solutionHash;
        }

        /** Deterministic board identity used to detect a re-deal/level change. */
        public String fingerprint() {
            return dimensionPrefix + masterKey
                + "|"
                + size
                + "|"
                + originX
                + ","
                + originY
                + ","
                + originZ
                + "|"
                + solutionHash;
        }
    }

    /** Nearest-fill candidate for one probe cycle. */
    private static final class FillCandidate {

        private final BoardView view;
        private final int cellX;
        private final int cellY;
        private final int solutionValue;
        private final int x;
        private final int y;
        private final int z;
        private final String key;
        private final double distanceSq;

        private FillCandidate(BoardView view, int cellX, int cellY, int solutionValue, int x, int y, int z, String key,
            double distanceSq) {
            this.view = view;
            this.cellX = cellX;
            this.cellY = cellY;
            this.solutionValue = solutionValue;
            this.x = x;
            this.y = y;
            this.z = z;
            this.key = key;
            this.distanceSq = distanceSq;
        }
    }
}
