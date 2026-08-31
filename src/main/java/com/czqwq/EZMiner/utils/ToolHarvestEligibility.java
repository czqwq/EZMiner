package com.czqwq.EZMiner.utils;

import net.minecraft.block.Block;
import net.minecraft.item.ItemStack;
import net.minecraftforge.common.ForgeHooks;

import com.czqwq.EZMiner.compat.GT5ToolCompat;
import com.czqwq.EZMiner.compat.TinkersConstructCompat;

/**
 * Shared harvest-eligibility rules for auto tool switching.
 *
 * <p>
 * This is the EZMiner counterpart of Qz-Miner's
 * {@code ToolHarvestEligibility}/{@code AutoToolUsabilityPolicy}: a candidate
 * tool must be able to actually harvest the target <em>and</em> keep a small
 * durability reserve. Non-tool items (arrows, seeds, blocks, food…) fail the
 * {@link #canHarvest} gate for hard blocks, so they can never be selected as a
 * replacement mining tool.
 *
 * <p>
 * All methods are static, side-effect free and intentionally cheap — they read
 * only the stack/block arguments and never touch the world or inventory.
 */
public final class ToolHarvestEligibility {

    /** Minimum remaining durability for a tool to be considered usable. */
    public static final int MIN_REMAINING_DURABILITY = 2;

    private ToolHarvestEligibility() {}

    /** Remaining durability; non-damageable stacks map to {@link Integer#MAX_VALUE}. */
    public static int remainingDurability(ItemStack stack) {
        if (stack == null) return 0;
        if (TinkersConstructCompat.isTiCTool(stack)) {
            return TinkersConstructCompat.remainingDurability(stack);
        }
        if (!stack.isItemStackDamageable()) return Integer.MAX_VALUE;
        return Math.max(0, stack.getMaxDamage() - stack.getItemDamage());
    }

    /** True when the stack has at least the minimum durability reserve. */
    public static boolean hasDurabilityReserve(ItemStack stack) {
        return remainingDurability(stack) >= MIN_REMAINING_DURABILITY;
    }

    /**
     * True when the stack can actually harvest {@code target} with the given meta.
     *
     * <p>
     * Uses the same stable rules as Qz-Miner: Forge harvest-tool hook when the
     * block declares one, otherwise the vanilla "tool not required" material rule,
     * otherwise {@code Item.canHarvestBlock}. Failures are fail-closed.
     */
    public static boolean canHarvest(ItemStack stack, Block target, int metadata) {
        if (stack == null || stack.getItem() == null || target == null) return false;
        try {
            // GT tools report a class-agnostic harvest level in getHarvestLevel(),
            // so ForgeHooks.canToolHarvestBlock would wrongly accept a wrench for
            // stone. Use GT's own isMinableBlock as the authority instead.
            if (GT5ToolCompat.isGTTool(stack)) {
                return GT5ToolCompat.canGTToolMineBlock(stack, target, metadata);
            }
            String harvestTool = target.getHarvestTool(metadata);
            if (harvestTool != null) {
                return ForgeHooks.canToolHarvestBlock(target, metadata, stack);
            }
            if (target.getMaterial()
                .isToolNotRequired()) {
                return true;
            }
            // TiC ToolCore extends Item (not ItemTool) and does not override
            // Item.canHarvestBlock, so the generic fallback would wrongly reject it
            // on the rare no-harvest-tool hard blocks. Use dig-speed as the practical
            // harvest signal for TiC tools.
            if (TinkersConstructCompat.isTiCTool(stack)) {
                return stack.getItem()
                    .getDigSpeed(stack, target, metadata) > 1.0F;
            }
            return stack.getItem()
                .canHarvestBlock(target, stack);
        } catch (RuntimeException | LinkageError failure) {
            return false;
        }
    }

    /** True when the stack is both able to harvest the target and has a durability reserve. */
    public static boolean isEligible(ItemStack stack, Block target, int metadata) {
        return canHarvest(stack, target, metadata) && hasDurabilityReserve(stack);
    }

    /**
     * Target-less "plausible mining tool" heuristic used when no live target is
     * available (e.g. a handoff packet without a look target).
     *
     * <p>
     * Rejects non-damageable non-tool items (arrows, seeds, blocks). TiC tools
     * use their real NBT durability.
     */
    public static boolean isUsableMiningTool(ItemStack stack) {
        if (stack == null || stack.getItem() == null) return false;
        if (TinkersConstructCompat.isTiCTool(stack)) {
            return TinkersConstructCompat.canContinueMining(stack);
        }
        // GT tools need a concrete target: their getHarvestLevel is class-agnostic,
        // so without a target we cannot tell a wrench from a pickaxe. Reject them in
        // the target-less heuristic; target-aware paths use canGTToolMineBlock.
        if (GT5ToolCompat.isGTTool(stack)) {
            return false;
        }
        if (stack.getItem() instanceof net.minecraft.item.ItemTool) {
            return hasDurabilityReserve(stack);
        }
        return stack.isItemStackDamageable() && hasDurabilityReserve(stack);
    }
}
