package com.czqwq.EZMiner.chain.execution;

import java.util.Queue;
import java.util.function.Function;

import net.minecraft.entity.player.EntityPlayerMP;

import org.joml.Vector3i;

/**
 * Session-level execution shell. Mutations are delegated to action executors.
 */
public class ChainExecutor {

    private final ChainActionExecutor actionExecutor;

    public ChainExecutor(ChainActionExecutor actionExecutor) {
        this.actionExecutor = actionExecutor;
    }

    public int executeBatch(Queue<Vector3i> queue, EntityPlayerMP player, int maxPerTick) {
        int done = 0;
        Vector3i pos;
        while (done < maxPerTick && (pos = queue.poll()) != null) {
            actionExecutor.execute(pos, player);
            done++;
        }
        return done;
    }

    public int executeBatch(Queue<Vector3i> queue, int maxPerTick, Function<Vector3i, Boolean> perPosition) {
        int done = 0;
        Vector3i pos;
        while (done < maxPerTick && (pos = queue.poll()) != null) {
            if (!perPosition.apply(pos)) break;
            done++;
        }
        return done;
    }
}
