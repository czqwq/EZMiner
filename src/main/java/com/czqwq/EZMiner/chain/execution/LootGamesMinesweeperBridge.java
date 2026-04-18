package com.czqwq.EZMiner.chain.execution;

import java.lang.reflect.Constructor;
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
 * Optional LootGames minesweeper integration bridge.
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
    private Method getBoardOriginMethod = null;
    private Method getStageMethod = null;
    private Class<?> stageWaitingClass = null;
    private Method stageSwapFieldMarkMethod = null;
    private Constructor<?> pos2iConstructor = null;
    private Method blockPosGetXMethod = null;
    private Method blockPosGetYMethod = null;
    private Method blockPosGetZMethod = null;
    private Object bombTypeConstant = null;
    private Object noMarkConstant = null;

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
            getBoardOriginMethod = gameMineSweeperClass.getMethod("getBoardOrigin");
            stageSwapFieldMarkMethod = stageWaitingClass.getMethod("swapFieldMark", pos2iClass);
            pos2iConstructor = pos2iClass.getConstructor(int.class, int.class);
            blockPosGetXMethod = blockPosClass.getMethod("getX");
            blockPosGetYMethod = blockPosClass.getMethod("getY");
            blockPosGetZMethod = blockPosClass.getMethod("getZ");
            @SuppressWarnings({ "unchecked", "rawtypes" })
            Object bomb = Enum.valueOf((Class<? extends Enum>) typeClass, "BOMB");
            @SuppressWarnings({ "unchecked", "rawtypes" })
            Object noMark = Enum.valueOf((Class<? extends Enum>) markClass, "NO_MARK");
            bombTypeConstant = bomb;
            noMarkConstant = noMark;
            hasLootGamesApi = true;
            EZMiner.LOG.info("EZMiner: LootGames minesweeper API detected – special minesweeper mode enabled.");
        } catch (ClassNotFoundException e) {
            EZMiner.LOG.debug("EZMiner: LootGames not found – special minesweeper mode disabled.");
        } catch (Exception e) {
            EZMiner.LOG.warn("EZMiner: LootGames bridge init failed: {}", e.getMessage());
        }
        compatibilityChecked = true;
    }

    public Vector3i detectNearestBomb(EntityPlayerMP player, Set<String> detectedBombs) {
        if (player == null || player.worldObj == null || detectedBombs == null) return null;
        if (!compatibilityChecked) checkCompatibility();
        if (!hasLootGamesApi || msMasterTileClass == null) return null;

        World world = player.worldObj;
        DetectedBomb best = null;
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
                int size = (Integer) boardSizeMethod.invoke(board);

                Object boardOrigin = getBoardOriginMethod.invoke(game);
                if (boardOrigin == null) continue;
                int originX = (Integer) blockPosGetXMethod.invoke(boardOrigin);
                int originY = (Integer) blockPosGetYMethod.invoke(boardOrigin);
                int originZ = (Integer) blockPosGetZMethod.invoke(boardOrigin);

                for (int x = 0; x < size; x++) {
                    for (int z = 0; z < size; z++) {
                        Object type = boardGetTypeMethod.invoke(board, x, z);
                        if (type != bombTypeConstant) continue;
                        if (!((Boolean) boardIsHiddenMethod.invoke(board, x, z))) continue;
                        if (boardGetMarkMethod.invoke(board, x, z) != noMarkConstant) continue;
                        int worldX = originX + x;
                        int worldY = originY;
                        int worldZ = originZ + z;
                        String key = player.dimension + ":" + worldX + ":" + worldY + ":" + worldZ;
                        if (detectedBombs.contains(key)) continue;
                        double distSq = player.getDistanceSq(worldX + 0.5, worldY + 0.5, worldZ + 0.5);
                        if (best == null || distSq < best.distanceSq) {
                            best = new DetectedBomb(key, worldX, worldY, worldZ, x, z, stage, distSq);
                        }
                    }
                }
            }
        } catch (Exception e) {
            EZMiner.LOG.debug("EZMiner: LootGames minesweeper probe failed: {}", e.getMessage());
            return null;
        }

        if (best == null) return null;
        try {
            Object pos2i = pos2iConstructor.newInstance(best.boardX, best.boardZ);
            stageSwapFieldMarkMethod.invoke(best.stage, pos2i);
        } catch (Exception e) {
            EZMiner.LOG.debug("EZMiner: LootGames minesweeper mark failed: {}", e.getMessage());
            return null;
        }
        detectedBombs.add(best.key);
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
        return new Vector3i(best.x, best.y, best.z);
    }

    private static class DetectedBomb {

        private final String key;
        private final int x;
        private final int y;
        private final int z;
        private final int boardX;
        private final int boardZ;
        private final Object stage;
        private final double distanceSq;

        private DetectedBomb(String key, int x, int y, int z, int boardX, int boardZ, Object stage, double distanceSq) {
            this.key = key;
            this.x = x;
            this.y = y;
            this.z = z;
            this.boardX = boardX;
            this.boardZ = boardZ;
            this.stage = stage;
            this.distanceSq = distanceSq;
        }
    }
}
