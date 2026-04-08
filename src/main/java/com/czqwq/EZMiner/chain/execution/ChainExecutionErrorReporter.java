package com.czqwq.EZMiner.chain.execution;

import net.minecraft.util.ChatComponentTranslation;

import org.joml.Vector3i;

import com.czqwq.EZMiner.EZMiner;
import com.czqwq.EZMiner.core.Manager;
import com.czqwq.EZMiner.utils.MessageUtils;

/**
 * Shared error reporter for chain execution failures.
 */
public final class ChainExecutionErrorReporter {

    private ChainExecutionErrorReporter() {}

    public static void reportHarvestError(Manager manager, Vector3i pos, Exception error) {
        EZMiner.LOG.error("EZMiner: Error while harvesting block at {}: {}", pos, error.getMessage(), error);
        MessageUtils.serverSendPlayerMessage(
            new ChatComponentTranslation("ezminer.message.chain.error", pos, error.toString()),
            manager.playerUUID);
    }
}
