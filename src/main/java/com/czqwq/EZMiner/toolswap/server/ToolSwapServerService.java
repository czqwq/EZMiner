package com.czqwq.EZMiner.toolswap.server;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;

import com.czqwq.EZMiner.EZMiner;

/**
 * Server-side registry of physical tool-swap ledgers (one per player).
 *
 * <p>
 * The server is the authority for restoring borrowed hotbar items after a chain.
 * The client may still initiate swaps, but every borrow is recorded here and
 * {@link #finalize(EntityPlayerMP)} restores the original layout on chain end.
 */
public final class ToolSwapServerService {

    private static final Map<UUID, ToolSwapServerLedger> LEDGERS = new HashMap<>();

    private ToolSwapServerService() {}

    /** Records a borrow swap before it is applied. Call on the server thread. */
    public static void recordSwap(EntityPlayerMP player, int slotA, int slotB, ItemStack a, ItemStack b) {
        if (player == null || slotA < 0 || slotB < 0 || slotA >= 36 || slotB >= 36 || slotA == slotB) return;
        ledger(player).recordSwap(slotA, slotB, a, b);
    }

    /** Removes a ledger record when a client restore swap reverses it. Returns true when removed. */
    public static boolean removeSwap(EntityPlayerMP player, int slotA, int slotB, ItemStack a, ItemStack b) {
        if (player == null) return false;
        ToolSwapServerLedger ledger = LEDGERS.get(player.getUniqueID());
        if (ledger != null) {
            boolean removed = ledger.removeSwap(slotA, slotB, a, b);
            if (ledger.isEmpty()) LEDGERS.remove(player.getUniqueID());
            return removed;
        }
        return false;
    }

    /** Restores all recorded borrows for the player and clears the ledger. */
    public static void finalize(EntityPlayerMP player) {
        if (player == null) return;
        ToolSwapServerLedger ledger = LEDGERS.remove(player.getUniqueID());
        if (ledger != null) {
            ledger.restoreAll(player);
            if (ledger.isRecoveryOnly()) {
                EZMiner.LOG.debug("[ToolSwap] finalize recoveryOnly for {}", player.getUniqueID());
            }
            if (ledger.isConflict()) {
                EZMiner.LOG.debug("[ToolSwap] finalize conflict for {}", player.getUniqueID());
            }
        }
    }

    /** Drops any ledger for the player (logout/respawn/dimension change). */
    public static void clear(UUID playerId) {
        if (playerId != null) LEDGERS.remove(playerId);
    }

    private static ToolSwapServerLedger ledger(EntityPlayerMP player) {
        return LEDGERS.computeIfAbsent(player.getUniqueID(), k -> new ToolSwapServerLedger());
    }
}
