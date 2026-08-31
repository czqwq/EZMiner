package com.czqwq.EZMiner.toolswap.server;

import net.minecraft.item.ItemStack;

/**
 * Server-side inventory boundary for tool-swap mutations.
 *
 * <p>
 * Mirrors Qz-Miner's {@code AutoToolSwapInventoryPort}: the server is the only
 * owner of physical inventory mutations during a chain. Implementations must run
 * on the server main thread.
 */
public interface ToolSwapInventoryPort {

    boolean isPlayerAlive();

    boolean isCreativeMode();

    boolean hasPersonalInventoryWindow0();

    boolean isCursorEmpty();

    int selectedHotbarSlot();

    ItemStack readInventorySlot(int slot);

    /** Atomically swaps two 0..35 slots. Normal return means the swap is applied. */
    void swapInventorySlotsAtomically(int slotA, int slotB);

    /** Marks inventory dirty and sends the full personal-inventory window to the client. */
    void syncInventoryDifference();
}
