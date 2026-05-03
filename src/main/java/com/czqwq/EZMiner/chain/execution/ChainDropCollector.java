package com.czqwq.EZMiner.chain.execution;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

import net.minecraft.entity.item.EntityItem;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;

import com.czqwq.EZMiner.core.ItemStackKey;
import com.czqwq.EZMiner.core.founder.DeterminingIdentical;

/**
 * Aggregates block drops during a chain operation and flushes them as world entities.
 *
 * <p>
 * Uses a two-tier storage strategy:
 * <ul>
 * <li><b>Fast path</b>: Items without NBT are keyed by {@link ItemStackKey} (item type +
 * damage) in a {@link LinkedHashMap}. Merging a newly-received drop is O(1).</li>
 * <li><b>Slow path</b>: Items carrying an {@link NBTTagCompound} fall back to a short
 * {@link ArrayList} with O(n) content comparison, since {@code NBTTagCompound} does
 * not override {@code hashCode()} in Minecraft 1.7.10. NBT-bearing drops are rare
 * (usually none in a GregTech ore vein), so the linear cost is negligible.</li>
 * </ul>
 *
 * <p>
 * Extracted from {@link com.czqwq.EZMiner.core.Manager} to give drop aggregation a
 * dedicated owner with a single responsibility.
 */
public class ChainDropCollector {

    private final LinkedHashMap<ItemStackKey, ItemStack> dropsMap = new LinkedHashMap<>();
    private final List<ItemStack> dropsWithNbt = new ArrayList<>();

    /**
     * Merges all stacks in {@code drops} into the internal collection, then clears
     * {@code drops} so the caller's list is empty (preventing vanilla item spawning).
     */
    public void collect(List<ItemStack> drops) {
        for (ItemStack drop : drops) {
            if (drop == null || drop.stackSize <= 0) continue;
            NBTTagCompound tag = drop.getTagCompound();
            if (tag == null) {
                // Fast O(1) path: no NBT — item + damage uniquely identifies the stack type.
                ItemStackKey key = ItemStackKey.of(drop);
                ItemStack existing = dropsMap.get(key);
                if (existing != null) {
                    existing.stackSize += drop.stackSize;
                } else {
                    dropsMap.put(key, drop.copy());
                }
            } else {
                // Slow O(n) path: items with NBT require full content comparison (rare).
                boolean merged = false;
                for (ItemStack existing : dropsWithNbt) {
                    if (!DeterminingIdentical.isSame(existing, drop)) continue;
                    existing.stackSize += drop.stackSize;
                    merged = true;
                    break;
                }
                if (!merged) dropsWithNbt.add(drop.copy());
            }
        }
        drops.clear();
    }

    /** Returns {@code true} when no drops have been accumulated. */
    public boolean isEmpty() {
        return dropsMap.isEmpty() && dropsWithNbt.isEmpty();
    }

    /**
     * Spawns all accumulated drops as {@link EntityItem}s at the given world position,
     * then clears the internal collection.
     */
    public void flush(World world, double x, double y, double z) {
        for (ItemStack stack : dropsMap.values()) {
            if (stack == null || stack.stackSize <= 0) continue;
            world.spawnEntityInWorld(new EntityItem(world, x, y, z, stack));
        }
        for (ItemStack stack : dropsWithNbt) {
            if (stack == null || stack.stackSize <= 0) continue;
            world.spawnEntityInWorld(new EntityItem(world, x, y, z, stack));
        }
        clear();
    }

    /** Discards all accumulated drops without spawning them. */
    public void clear() {
        dropsMap.clear();
        dropsWithNbt.clear();
    }
}
