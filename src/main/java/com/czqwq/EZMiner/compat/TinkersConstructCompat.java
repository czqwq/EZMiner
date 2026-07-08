package com.czqwq.EZMiner.compat;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

/**
 * Tool durability compatibility bridge for Tinkers' Construct.
 *
 * <p>
 * TiC tools store their real durability in NBT tags ({@code InfiTool.Damage} /
 * {@code InfiTool.TotalDurability}) rather than in vanilla's
 * metadata-based {@code ItemStack.itemDamage}. The vanilla
 * {@code getMaxDamage()} always returns a hardcoded 100 (used solely for the
 * durability-bar display), which makes vanilla durability checks inaccurate for
 * TiC tools.
 * </p>
 *
 * <h3>The unbreakable (不毁) problem</h3>
 * <p>
 * A TiC tool with {@code InfiTool.Unbreaking >= 10} cannot take durability
 * damage — every damage attempt is probabilistically negated. EZMiner's
 * vanilla-style durability check in {@code BaseOperator.canOperate()}
 * incorrectly treats it as a breakable tool and may reject a perfectly
 * functional unbreakable tool whose scaled vanilla damage happens to be high
 * (e.g. the tool was heavily used before receiving the Reinforced X modifier).
 * </p>
 *
 * <h3>Design</h3>
 * <p>
 * Detection is entirely NBT-based — no class loading, no reflection, and
 * <strong>zero compile-time dependency</strong> on Tinkers' Construct. The
 * {@code "InfiTool"} NBT key is the universal marker for any TiC-crafted tool.
 * This class follows the same compat-module pattern as
 * {@link GT5ToolCompat}.
 * </p>
 */
public final class TinkersConstructCompat {

    private TinkersConstructCompat() {}

    // ── Detection ────────────────────────────────────────────────────────────────

    /**
     * Returns {@code true} when {@code stack} is a Tinkers' Construct tool.
     * Detection is purely NBT-based — the {@code "InfiTool"} compound key is the
     * canonical marker that TiC's {@code ToolBuilder} stamps onto every crafted
     * tool.
     *
     * @param stack the item stack to test (nullable)
     */
    public static boolean isTiCTool(ItemStack stack) {
        if (stack == null) return false;
        NBTTagCompound tags = stack.getTagCompound();
        return tags != null && tags.hasKey("InfiTool");
    }

    // ── State queries ───────────────────────────────────────────────────────────

    /**
     * Returns {@code true} when the TiC tool is unbreakable (Reinforced X or
     * higher, i.e. {@code InfiTool.Unbreaking >= 10}). An unbreakable tool
     * negates every durability-damage attempt and will never break.
     *
     * <p>
     * Callers should guard with {@link #isTiCTool(ItemStack)} before calling
     * this method; passing a non-TiC stack returns {@code false}.
     * </p>
     */
    public static boolean isUnbreakable(ItemStack stack) {
        if (!isTiCTool(stack)) return false;
        NBTTagCompound toolTag = stack.getTagCompound()
            .getCompoundTag("InfiTool");
        return toolTag.getInteger("Unbreaking") >= 10;
    }

    /**
     * Returns {@code true} when the TiC tool is broken
     * ({@code InfiTool.Broken == true}). A broken TiC tool mines at ~0.1×
     * speed and should not be used for chain mining.
     *
     * <p>
     * Callers should guard with {@link #isTiCTool(ItemStack)} before calling
     * this method; passing a non-TiC stack returns {@code false}.
     * </p>
     */
    public static boolean isBroken(ItemStack stack) {
        if (!isTiCTool(stack)) return false;
        return stack.getTagCompound()
            .getCompoundTag("InfiTool")
            .getBoolean("Broken");
    }

    // ── Durability decision ─────────────────────────────────────────────────────

    /**
     * Primary durability gate for chain mining: returns {@code false} when the
     * tool cannot safely harvest another block.
     *
     * <p>
     * Decision order (TiC tools only — non-TiC stacks always return
     * {@code true} so the caller falls through to the vanilla check):
     * </p>
     * <ol>
     * <li><b>Broken</b> → reject ({@code false}).</li>
     * <li><b>Unbreakable</b> ({@code Unbreaking >= 10}) → allow
     * ({@code true}) — durability is meaningless for these tools.</li>
     * <li><b>Normal tool</b> → read real NBT durability
     * ({@code TotalDurability - Damage}) and require at least 2
     * remaining uses.</li>
     * </ol>
     *
     * @param stack the held item (nullable; non-TiC stacks return {@code true})
     * @return {@code true} if mining should continue
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
