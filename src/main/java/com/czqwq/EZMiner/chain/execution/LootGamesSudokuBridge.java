package com.czqwq.EZMiner.chain.execution;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

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
 * degrades gracefully (all methods return {@code null} or {@code false}).
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

    // --- Board methods ---
    private Method boardIsGeneratedMethod = null;
    private Method boardCheckWinMethod = null;
    private Method boardGetPlayerValueMethod = null;
    private Field boardPlayerField = null;
    private Field boardSolutionField = null;
    private Field boardPuzzleField = null;

    // --- Pos2i ---
    private Constructor<?> pos2iConstructor = null;

    // --- SPSSyncCell ---
    private Constructor<?> spSyncCellConstructor = null;

    // --- BlockPos ---
    private Method blockPosGetXMethod = null;
    private Method blockPosGetYMethod = null;
    private Method blockPosGetZMethod = null;

    @SuppressWarnings("unchecked")
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
     * Checks whether any LootGames Sudoku game is currently active (in
     * {@code StageWaiting}) in the given world.
     */
    public boolean isAnyGameActive(World world) {
        if (world == null) return false;
        if (!compatibilityChecked) checkCompatibility();
        if (!hasLootGamesApi || sudokuTileClass == null) return false;

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
                if (stage != null && stageWaitingClass.isInstance(stage)) {
                    return true;
                }
            }
        } catch (Exception e) {
            EZMiner.LOG.debug("EZMiner: LootGames Sudoku isAnyGameActive check failed: {}", e.getMessage());
        }
        return false;
    }

    /**
     * Finds the nearest unfilled Sudoku cell and fills it with the correct solution
     * value. Returns the world position of the filled cell, or {@code null} if no
     * cell needed filling.
     */
    public Vector3i detectAndFillNearestCell(EntityPlayerMP player, Set<String> filledCells) {
        if (player == null || player.worldObj == null || filledCells == null) return null;
        if (!compatibilityChecked) checkCompatibility();
        if (!hasLootGamesApi || sudokuTileClass == null) return null;

        World world = player.worldObj;
        FillCandidate best = null;
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

                Object boardOrigin = getBoardOriginMethod.invoke(game);
                if (boardOrigin == null) continue;
                int originX = (Integer) blockPosGetXMethod.invoke(boardOrigin);
                int originY = (Integer) blockPosGetYMethod.invoke(boardOrigin);
                int originZ = (Integer) blockPosGetZMethod.invoke(boardOrigin);

                Integer[][] playerGrid = (Integer[][]) boardPlayerField.get(board);
                Integer[][] solutionGrid = (Integer[][]) boardSolutionField.get(board);
                Integer[][] puzzleGrid = (Integer[][]) boardPuzzleField.get(board);

                for (int x = 0; x < 9; x++) {
                    for (int y = 0; y < 9; y++) {
                        // Skip clue cells (pre-filled by the puzzle)
                        if (puzzleGrid[x][y] != null && puzzleGrid[x][y] != 0) continue;

                        int solutionValue = solutionGrid[x][y] != null ? solutionGrid[x][y] : 0;
                        if (solutionValue == 0) continue;

                        int playerValue = playerGrid[x][y] != null ? playerGrid[x][y] : 0;
                        if (playerValue == solutionValue) continue;

                        int worldX = originX + x;
                        int worldY = originY;
                        int worldZ = originZ + y;
                        String key = player.dimension + ":" + worldX + ":" + worldY + ":" + worldZ;
                        if (filledCells.contains(key)) continue;

                        double distSq = player.getDistanceSq(worldX + 0.5, worldY + 0.5, worldZ + 0.5);
                        if (best == null || distSq < best.distanceSq) {
                            best = new FillCandidate(key, game, board, x, y, solutionValue, distSq);
                        }
                    }
                }
            }
        } catch (Exception e) {
            EZMiner.LOG.debug("EZMiner: LootGames Sudoku probe failed: {}", e.getMessage());
            return null;
        }

        if (best == null) return null;

        try {
            // Set player value directly
            Integer[][] playerGrid = (Integer[][]) boardPlayerField.get(best.board);
            playerGrid[best.boardX][best.boardY] = best.solutionValue;

            // Sync to clients
            Object pos2i = pos2iConstructor.newInstance(best.boardX, best.boardY);
            int playerValue = (Integer) boardGetPlayerValueMethod.invoke(best.board, pos2i);
            Object syncPacket = spSyncCellConstructor.newInstance(pos2i, playerValue);
            sendUpdatePacketToNearbyMethod.invoke(best.game, syncPacket);
            saveMethod.invoke(best.game);

            // Check win
            boolean won = (Boolean) boardCheckWinMethod.invoke(best.board);
            if (won) {
                onLevelSuccessfullyFinishedMethod.invoke(best.game);
            }

            filledCells.add(best.key);
            MessageUtils.serverSendPlayerMessage(
                new ChatComponentTranslation(
                    "ezminer.message.special.sudoku.filled",
                    best.boardX + 1,
                    best.boardY + 1,
                    best.solutionValue),
                player.getUniqueID());
            Object origin = getBoardOriginMethod.invoke(best.game);
            return new Vector3i(
                (Integer) blockPosGetXMethod.invoke(origin),
                (Integer) blockPosGetYMethod.invoke(origin),
                (Integer) blockPosGetZMethod.invoke(origin));
        } catch (Exception e) {
            EZMiner.LOG.debug("EZMiner: LootGames Sudoku fill failed: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Returns a fingerprint string for the first Sudoku board found in the given world,
     * or {@code null} if no board is active. The fingerprint changes whenever the board
     * is regenerated (e.g. after a level-up), allowing the handler to detect that stale
     * filled-cell keys must be cleared.
     */
    public String getBoardFingerprint(World world) {
        if (world == null) return null;
        if (!compatibilityChecked) checkCompatibility();
        if (!hasLootGamesApi || sudokuTileClass == null) return null;

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

                Integer[][] solutionGrid = (Integer[][]) boardSolutionField.get(board);
                // Build a fingerprint from the first 3 solution cells.
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < 3 && i < 9; i++) {
                    sb.append(solutionGrid[0][i] != null ? solutionGrid[0][i] : 0);
                    sb.append(',');
                }
                return sb.toString();
            }
        } catch (Exception e) {
            EZMiner.LOG.debug("EZMiner: LootGames Sudoku fingerprint failed: {}", e.getMessage());
        }
        return null;
    }

    private static class FillCandidate {

        private final String key;
        private final Object game;
        private final Object board;
        private final int boardX;
        private final int boardY;
        private final int solutionValue;
        private final double distanceSq;

        private FillCandidate(String key, Object game, Object board, int boardX, int boardY, int solutionValue,
            double distanceSq) {
            this.key = key;
            this.game = game;
            this.board = board;
            this.boardX = boardX;
            this.boardY = boardY;
            this.solutionValue = solutionValue;
            this.distanceSq = distanceSq;
        }
    }
}
