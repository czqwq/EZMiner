package com.czqwq.EZMiner.core;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

/**
 * Compact, hash-efficient identity key for an {@link ItemStack} (item type + damage).
 *
 * <p>
 * Used by {@link Manager}'s drop-aggregation map so that merging a newly-received drop into
 * the accumulated set is O(1) rather than an O(n) linear scan. Items that carry an
 * {@code NBTTagCompound} are intentionally excluded from this fast path and handled
 * via a short fallback list, because {@code NBTTagCompound} does not override
 * {@code hashCode()} in Minecraft 1.7.10 (so equality by content is not hash-stable).
 *
 * <p>
 * {@link Item} instances are registry singletons, so reference equality ({@code ==}) is
 * both correct and produces better hash distribution than a virtual {@code equals()} call.
 */
public final class ItemStackKey {

    private final Item item;
    private final int damage;
    private final int hash;

    private ItemStackKey(Item item, int damage) {
        this.item = item;
        this.damage = damage;
        // identityHashCode is stable per-instance and gives good hash distribution
        // when combined with the damage value.
        this.hash = System.identityHashCode(item) * 31 + damage;
    }

    /** Creates a key capturing the item type and damage of the given stack. */
    public static ItemStackKey of(ItemStack stack) {
        return new ItemStackKey(stack.getItem(), stack.getItemDamage());
    }

    @Override
    public int hashCode() {
        return hash;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ItemStackKey)) return false;
        ItemStackKey k = (ItemStackKey) o;
        // Item is a registry singleton → reference equality is correct and faster than equals().
        return item == k.item && damage == k.damage;
    }
}
