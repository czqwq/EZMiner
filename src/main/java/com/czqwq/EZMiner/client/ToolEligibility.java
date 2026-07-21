package com.czqwq.EZMiner.client;

import net.minecraft.block.Block;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

/**
 * Pure eligibility checks for smart tool switching.
 * <p>
 * All methods are static and side-effect-free — they only read immutable
 * properties of the arguments. Separated from the handler to keep scoring,
 * durability, and efficiency rules in one testable place.
 */
public final class ToolEligibility {

    /** Minimum remaining durability for a tool to be considered usable. */
    public static final int MIN_REMAINING_DURABILITY = 2;

    private ToolEligibility() {}

    // ── Durability ──────────────────────────────────────────────────────────────

    /** Returns remaining durability; unbreakable items map to Integer.MAX_VALUE. */
    public static int remainingDurability(ItemStack stack) {
        if (stack == null || !stack.isItemStackDamageable()) return Integer.MAX_VALUE;
        return Math.max(0, stack.getMaxDamage() - stack.getItemDamage());
    }

    /** Returns true when the tool has enough durability reserve to safely continue. */
    public static boolean hasDurabilityReserve(ItemStack stack) {
        return remainingDurability(stack) >= MIN_REMAINING_DURABILITY;
    }

    // ── Efficiency ──────────────────────────────────────────────────────────────

    /** Returns true when the tool has actual mining efficiency on the target block (digSpeed > 1.0F). */
    public static boolean isEffectiveForBlock(ItemStack stack, Block block, int meta) {
        if (stack == null || stack.getItem() == null || block == null) return false;
        return stack.getItem()
            .getDigSpeed(stack, block, meta) > 1.0F;
    }

    // ── Combined eligibility ────────────────────────────────────────────────────

    /**
     * Full eligibility check: effective + sufficient durability.
     * Does NOT check harvest level / tool class — those are handled by scoring.
     */
    public static boolean isEligible(ItemStack stack, Block block, int meta) {
        return hasDurabilityReserve(stack) && isEffectiveForBlock(stack, block, meta);
    }

    // ── Shears ──────────────────────────────────────────────────────────────────

    /** Returns true for blocks that are best harvested with shears. */
    public static boolean needsShears(Block block) {
        net.minecraft.block.material.Material mat = block.getMaterial();
        return mat == net.minecraft.block.material.Material.leaves || mat == net.minecraft.block.material.Material.vine
            || mat == net.minecraft.block.material.Material.plants
            || mat == net.minecraft.block.material.Material.web
            || mat == net.minecraft.block.material.Material.cloth
            || mat == net.minecraft.block.material.Material.carpet;
    }

    /** Returns true when the stack is a shears-like item (vanilla ItemShears or subclass). */
    public static boolean isShears(ItemStack stack) {
        if (stack == null) return false;
        Item item = stack.getItem();
        if (item == null) return false;
        if (item instanceof net.minecraft.item.ItemShears) return true;
        // Fallback: walk class hierarchy by name (handles transformer-renamed classes)
        for (Class<?> c = item.getClass(); c != null; c = c.getSuperclass()) {
            if (c.getName()
                .equals("net.minecraft.item.ItemShears")) return true;
        }
        return false;
    }
}
