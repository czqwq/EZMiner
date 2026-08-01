package com.czqwq.EZMiner.compat;

import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemShears;
import net.minecraft.item.ItemStack;
import net.minecraftforge.common.IShearable;

/**
 * Replays the vanilla shear-drop hook for EZMiner's fast harvest paths.
 *
 * <p>
 * Vanilla {@code ItemInWorldManager.tryHarvestBlock} fires
 * {@code Item.onBlockStartBreak} <em>before</em> removing the block. In GTNH's
 * Forge the shear-drop logic for leaves / tall grass / vines / dead bushes lives
 * exactly there: {@code ItemShears.onBlockStartBreak} detects {@code IShearable}
 * blocks and spawns the block item itself (leaf, grass, vine), then returns
 * {@code false} so the block is still removed normally. The trigger block of a
 * chain is broken through that vanilla path, so it drops leaves — but EZMiner's
 * fast paths skip {@code onBlockStartBreak} entirely, which is why chain-mined
 * leaves with shears dropped saplings instead of leaves.
 *
 * <p>
 * This bridge restores only the shear-on-{@code IShearable} case with two cheap
 * gates, keeping the hot path (stone / ore / dirt — the vast majority of mined
 * blocks) at a single {@code instanceof} that short-circuits before any
 * inventory access:
 * <ol>
 * <li>{@code block instanceof IShearable} — false for nearly every non-shearable
 * block.</li>
 * <li>held item is {@code ItemShears} (or subclass) — one {@code instanceof} on
 * the held stack.</li>
 * </ol>
 *
 * <p>
 * TiC tools are deliberately excluded: {@link TinkersConstructLevelingBridge}
 * replays their hooks instead, since their {@code onBlockStartBreak} AOE
 * overrides would recursively break extra blocks mid-chain. Excluding them here
 * also means no per-block virtual call is added for the common TiC case.
 */
public final class ShearsHarvestBridge {

    private ShearsHarvestBridge() {}

    /**
     * Fires {@code Item.onBlockStartBreak} for the held shears on an
     * {@code IShearable} block, mirroring the vanilla trigger-block path.
     *
     * @param player the mining player (server side)
     * @param x      block x
     * @param y      block y
     * @param z      block z
     * @param block  the resolved block at (x, y, z) — the caller already fetched
     *               it, so no extra world query happens
     * @return {@code true} if the hook consumed the block (the caller must skip
     *         its own tool damage / removal / drops and treat the block as
     *         harvested), {@code false} to continue with the normal fast path
     */
    public static boolean fireIfShears(EntityPlayerMP player, int x, int y, int z, Block block) {
        if (!(block instanceof IShearable)) return false;
        ItemStack stack = player.getCurrentEquippedItem();
        // Mirrors ToolEligibility.isShears' primary branch (client package — not
        // imported here to keep server-side chain code layering clean).
        if (stack == null || !(stack.getItem() instanceof ItemShears)) return false;
        return stack.getItem()
            .onBlockStartBreak(stack, x, y, z, player);
    }
}
