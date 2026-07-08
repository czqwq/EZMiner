package com.czqwq.EZMiner.compat;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

/**
 * TiC tool durability compat — NBT-based, zero reflection, zero compile-time dependency.
 *
 * <p>
 * TiC stores real durability in NBT ({@code InfiTool.Damage}/{@code InfiTool.TotalDurability}),
 * not in vanilla metadata. {@code getMaxDamage()} always returns 100 for TiC tools —
 * a hardcoded placeholder for the durability bar, not the real cap.
 * </p>
 *
 * <p>
 * Unbreakable tools ({@code InfiTool.Unbreaking >= 10}) never take durability damage
 * and should always be allowed to chain-mine.
 * </p>
 */
public final class TinkersConstructCompat {

    private TinkersConstructCompat() {}

    // ── Detection ────────────────────────────────────────────────────────────────

    /** True if stack has NBT key {@code "InfiTool"} — the universal TiC tool marker. */
    public static boolean isTiCTool(ItemStack stack) {
        if (stack == null) return false;
        NBTTagCompound tags = stack.getTagCompound();
        return tags != null && tags.hasKey("InfiTool");
    }

    // ── State queries ───────────────────────────────────────────────────────────

    /** Unbreakable = Reinforced X ({@code Unbreaking >= 10}). Tool never loses durability. */
    public static boolean isUnbreakable(ItemStack stack) {
        if (!isTiCTool(stack)) return false;
        NBTTagCompound toolTag = stack.getTagCompound()
            .getCompoundTag("InfiTool");
        return toolTag.getInteger("Unbreaking") >= 10;
    }

    /** Broken TiC tool ({@code Broken == true}) — mines at ~0.1× speed, should not chain. */
    public static boolean isBroken(ItemStack stack) {
        if (!isTiCTool(stack)) return false;
        return stack.getTagCompound()
            .getCompoundTag("InfiTool")
            .getBoolean("Broken");
    }

    // ── Durability decision ─────────────────────────────────────────────────────

    /**
     * Durability gate for chain mining.
     * <ol>
     * <li>Broken → reject.</li>
     * <li>Unbreakable → allow.</li>
     * <li>Normal tool → check NBT {@code TotalDurability - Damage > 1}.</li>
     * </ol>
     * Non-TiC stacks always return true (caller uses vanilla check).
     */
    public static boolean canContinueMining(ItemStack stack) {
        if (!isTiCTool(stack)) return true;

        NBTTagCompound toolTag = stack.getTagCompound()
            .getCompoundTag("InfiTool");

        // 1. Broken tool — cannot mine at all (speed ≈ 0.1×).
        if (toolTag.getBoolean("Broken")) return false;

        // 2. Unbreakable tool — durability loss is always negated; safe to continue.
        if (toolTag.getInteger("Unbreaking") >= 10) return true;

        // 3. Normal tool — use the real NBT durability values.
        int damage = toolTag.getInteger("Damage");
        int maxDurability = toolTag.getInteger("TotalDurability");

        // Guard against malformed tools (TotalDurability not yet computed).
        if (maxDurability <= 0) return true;

        return (maxDurability - damage) > 1;
    }
}
