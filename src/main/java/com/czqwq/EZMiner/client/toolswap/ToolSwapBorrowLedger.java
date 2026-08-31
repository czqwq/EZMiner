package com.czqwq.EZMiner.client.toolswap;

import java.util.ArrayDeque;

import net.minecraft.client.Minecraft;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import com.czqwq.EZMiner.EZMiner;
import com.czqwq.EZMiner.network.PacketInventorySwap;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Client-side ledger for handoff full-inventory borrows.
 *
 * <p>
 * When {@code PacketToolBreakHandoff} swaps a tool from the main inventory into
 * the hotbar, the physical arrangement is recorded here. The borrows are
 * returned (in reverse order, with content fingerprint checks) when a chain
 * ends or smart-switch mode is deactivated, so the hotbar layout is restored
 * without trusting that no external change happened in between.
 *
 * <p>
 * This is the EZMiner counterpart of the borrow/restore part of Qz-Miner's
 * {@code AutoToolSwapServerBatchService}, kept deliberately small and
 * client-side for low overhead. All methods are client-only.
 */
@SideOnly(Side.CLIENT)
public final class ToolSwapBorrowLedger {

    private static final ArrayDeque<BorrowRecord> BORROWS = new ArrayDeque<>();

    private ToolSwapBorrowLedger() {}

    /**
     * Records a physical swap performed by the tool-break handoff so it can be
     * returned later. Only full-inventory borrows (hotbar ↔ inventory) need a
     * ledger; a pure currentItem change does not.
     */
    public static void recordHandoffSwap(int slotA, int slotB) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null || slotA < 0
            || slotB < 0
            || slotA >= mc.thePlayer.inventory.mainInventory.length
            || slotB >= mc.thePlayer.inventory.mainInventory.length
            || slotA == slotB) return;
        ItemStack a = mc.thePlayer.inventory.mainInventory[slotA];
        ItemStack b = mc.thePlayer.inventory.mainInventory[slotB];
        BORROWS.addLast(new BorrowRecord(slotA, slotB, a, b));
    }

    /** Restores all handoff borrows in reverse order when content fingerprints still match. */
    public static void restoreHandoffSwaps() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null) return;
        while (!BORROWS.isEmpty()) {
            BorrowRecord swap = BORROWS.removeLast();
            ItemStack a = mc.thePlayer.inventory.mainInventory[swap.slotA];
            ItemStack b = mc.thePlayer.inventory.mainInventory[swap.slotB];
            if (swap.canRestore(a, b)) {
                mc.thePlayer.inventory.mainInventory[swap.slotA] = b;
                mc.thePlayer.inventory.mainInventory[swap.slotB] = a;
                EZMiner.network.network.sendToServer(new PacketInventorySwap(swap.slotA, swap.slotB));
            }
        }
    }

    /** Clears all borrow records (e.g. on disconnect — no server to restore to). */
    public static void clear() {
        BORROWS.clear();
    }

    /** Immutable record of a handoff full-inventory borrow, restored in reverse order. */
    private static final class BorrowRecord {

        final int slotA;
        final int slotB;
        private final String idA;
        private final String idB;
        private final int damageA;
        private final int damageB;

        BorrowRecord(int slotA, int slotB, ItemStack a, ItemStack b) {
            this.slotA = slotA;
            this.slotB = slotB;
            this.idA = a == null ? null : Item.itemRegistry.getNameForObject(a.getItem());
            this.idB = b == null ? null : Item.itemRegistry.getNameForObject(b.getItem());
            this.damageA = a == null ? 0 : a.getItemDamage();
            this.damageB = b == null ? 0 : b.getItemDamage();
        }

        /** True when both slots still contain the items that were swapped (fingerprint check). */
        boolean canRestore(ItemStack a, ItemStack b) {
            return matches(a, idA, damageA) && matches(b, idB, damageB);
        }

        private static boolean matches(ItemStack stack, String expectedId, int expectedDamage) {
            if (expectedId == null) return stack == null;
            if (stack == null || stack.getItem() == null) return false;
            return expectedId.equals(Item.itemRegistry.getNameForObject(stack.getItem()))
                && stack.getItemDamage() == expectedDamage;
        }
    }
}
