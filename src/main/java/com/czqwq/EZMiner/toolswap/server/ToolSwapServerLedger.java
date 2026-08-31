package com.czqwq.EZMiner.toolswap.server;

import java.util.ArrayDeque;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

/**
 * Per-player server-side physical ledger for tool borrows during a chain.
 *
 * <p>
 * This is the EZMiner port of Qz-Miner's {@code PhysicalLedger} responsibility:
 * the server records every tool-borrow swap and restores the original hotbar
 * layout when the chain ends. Restore is LIFO and guarded by content
 * fingerprints, so externally-changed slots are never overwritten blindly.
 */
public final class ToolSwapServerLedger {

    private final ArrayDeque<SwapRecord> records = new ArrayDeque<>();
    private boolean recoveryOnly;
    private boolean conflict;

    /**
     * Records a swap <em>before</em> it is applied. {@code slotA} and {@code slotB}
     * must currently hold the original items.
     */
    public void recordSwap(int slotA, int slotB, ItemStack a, ItemStack b) {
        records.addLast(new SwapRecord(slotA, slotB, a, b));
    }

    /**
     * Attempts to remove a record that is being reversed by a client restore swap.
     * Returns true when a matching record was found and removed.
     */
    public boolean removeSwap(int slotA, int slotB, ItemStack a, ItemStack b) {
        java.util.Iterator<SwapRecord> it = records.iterator();
        while (it.hasNext()) {
            SwapRecord r = it.next();
            if (r.slotA == slotA && r.slotB == slotB && r.matchesPostSwap(a, b)) {
                it.remove();
                return true;
            }
        }
        return false;
    }

    /** Restores all borrows in reverse order; only slots whose fingerprints still match are touched. */
    public void restoreAll(EntityPlayerMP player) {
        if (records.isEmpty()) return;
        MinecraftToolSwapInventoryPort inv = new MinecraftToolSwapInventoryPort(player);
        if (!inv.isPlayerAlive() || !inv.hasPersonalInventoryWindow0() || !inv.isCursorEmpty()) {
            recoveryOnly = true;
            return;
        }
        boolean changed = false;
        while (!records.isEmpty()) {
            SwapRecord r = records.removeLast();
            ItemStack a = inv.readInventorySlot(r.slotA);
            ItemStack b = inv.readInventorySlot(r.slotB);
            if (r.matchesPostSwap(a, b)) {
                try {
                    inv.swapInventorySlotsAtomically(r.slotA, r.slotB);
                    changed = true;
                } catch (RuntimeException | LinkageError failure) {
                    // Restore mutation failed — do not retry blindly, mark recovery.
                    recoveryOnly = true;
                }
            } else {
                // The borrowed layout changed externally — do not overwrite, mark conflict.
                conflict = true;
            }
        }
        if (changed) {
            inv.syncInventoryDifference();
        }
    }

    /** True when restore could not safely run (e.g. inventory unsafe). */
    public boolean isRecoveryOnly() {
        return recoveryOnly;
    }

    /** True when at least one slot no longer matched the borrowed layout. */
    public boolean isConflict() {
        return conflict;
    }

    public boolean isEmpty() {
        return records.isEmpty();
    }

    /** Immutable two-slot borrow record with content fingerprints. */
    private static final class SwapRecord {

        final int slotA;
        final int slotB;
        private final String idA;
        private final String idB;
        private final int damageA;
        private final int damageB;
        private final int sizeA;
        private final int sizeB;

        SwapRecord(int slotA, int slotB, ItemStack a, ItemStack b) {
            this.slotA = slotA;
            this.slotB = slotB;
            this.idA = fingerprintId(a);
            this.idB = fingerprintId(b);
            this.damageA = a == null ? 0 : a.getItemDamage();
            this.damageB = b == null ? 0 : b.getItemDamage();
            this.sizeA = a == null ? 0 : a.stackSize;
            this.sizeB = b == null ? 0 : b.stackSize;
        }

        /** True when the current slots hold the post-swap arrangement (A=oldB, B=oldA). */
        boolean matchesPostSwap(ItemStack a, ItemStack b) {
            return matches(a, idB, damageB, sizeB) && matches(b, idA, damageA, sizeA);
        }

        private static boolean matches(ItemStack stack, String expectedId, int expectedDamage, int expectedSize) {
            if (expectedId == null) return stack == null;
            if (stack == null || stack.getItem() == null) return false;
            return expectedId.equals(fingerprintId(stack)) && stack.getItemDamage() == expectedDamage
                && stack.stackSize == expectedSize;
        }

        private static String fingerprintId(ItemStack stack) {
            if (stack == null || stack.getItem() == null) return null;
            Object name = Item.itemRegistry.getNameForObject(stack.getItem());
            return name == null ? "minecraft:unknown" : String.valueOf(name);
        }
    }
}
