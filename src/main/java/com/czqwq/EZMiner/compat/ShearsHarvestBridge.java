package com.czqwq.EZMiner.compat;

import java.util.ArrayList;

import net.minecraft.block.Block;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemShears;
import net.minecraft.item.ItemStack;
import net.minecraft.stats.StatList;
import net.minecraft.world.World;
import net.minecraftforge.common.IShearable;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.world.BlockEvent;

/**
 * Replays the vanilla shear-drop logic for EZMiner's fast harvest paths.
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
 * Unlike the vanilla hook, the shear drops are <em>not</em> spawned directly as
 * {@code EntityItem}s — {@code ItemShears.onBlockStartBreak} bypasses
 * {@code HarvestDropsEvent}, so EZMiner's {@code ChainDropCollector} (which
 * listens on that event) never sees them and every leaf lands at its own
 * position. Instead this bridge calls {@code IShearable.onSheared} itself and
 * posts a {@code HarvestDropsEvent} carrying the harvester, so the drops flow
 * into the collector like every other chain drop. When no listener collects
 * them (e.g. Bandit is loaded and EZMiner yields), they fall back to vanilla
 * entity spawning.
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
     * Generates the shear drops for the held shears on an {@code IShearable}
     * block and injects them into the drop collection pipeline, mirroring the
     * vanilla trigger-block break.
     *
     * <p>
     * The block is <em>not</em> consumed — the caller must continue with its
     * normal removal / tool damage / drop flow.
     *
     * @param player the mining player (server side)
     * @param x      block x
     * @param y      block y
     * @param z      block z
     * @param block  the resolved block at (x, y, z) — the caller already fetched
     *               it, so no extra world query happens
     */
    public static void fireIfShears(EntityPlayerMP player, int x, int y, int z, Block block) {
        if (!(block instanceof IShearable)) return;
        ItemStack stack = player.getCurrentEquippedItem();
        // Mirrors ToolEligibility.isShears' primary branch (client package — not
        // imported here to keep server-side chain code layering clean).
        if (stack == null || !(stack.getItem() instanceof ItemShears)) return;
        World world = player.worldObj;
        if (world == null) return;

        IShearable target = (IShearable) block;
        if (!target.isShearable(stack, world, x, y, z)) return;

        // Same drop source as vanilla ItemShears.onBlockStartBreak: fortune feeds
        // into IShearable.onSheared (e.g. more leaves per block).
        int fortune = EnchantmentHelper.getEnchantmentLevel(Enchantment.fortune.effectId, stack);
        ArrayList<ItemStack> drops = target.onSheared(stack, world, x, y, z, fortune);

        if (!drops.isEmpty()) {
            // Inject into the drop pipeline via HarvestDropsEvent (harvester set):
            // Manager.onHarvestDrops collects event.drops into the ChainDropCollector
            // during an active chain and clears the list. What no listener collects
            // (e.g. Bandit yield) is spawned as entities below, like vanilla would.
            BlockEvent.HarvestDropsEvent event = new BlockEvent.HarvestDropsEvent(
                x,
                y,
                z,
                world,
                block,
                world.getBlockMetadata(x, y, z),
                fortune,
                1.0F,
                drops,
                player,
                false);
            MinecraftForge.EVENT_BUS.post(event);
            spawnRemainingDrops(world, x, y, z, drops);
        }

        // Same tool wear and block stat as vanilla ItemShears.onBlockStartBreak.
        // The fast path's later onBlockDestroyed call skips damage for shearable
        // blocks (GTNH ItemShears), so the tool loses exactly 1 durability.
        stack.damageItem(1, player);
        player.addStat(StatList.mineBlockStatArray[Block.getIdFromBlock(block)], 1);
    }

    /** Spawns drops that no listener collected, mirroring vanilla entity spawns. */
    private static void spawnRemainingDrops(World world, int x, int y, int z, ArrayList<ItemStack> drops) {
        for (ItemStack drop : drops) {
            if (drop == null || drop.stackSize <= 0) continue;
            float f = 0.7F;
            double d = (double) (world.rand.nextFloat() * f) + (1.0F - f) * 0.5D;
            double d1 = (double) (world.rand.nextFloat() * f) + (1.0F - f) * 0.5D;
            double d2 = (double) (world.rand.nextFloat() * f) + (1.0F - f) * 0.5D;
            EntityItem entityitem = new EntityItem(world, (double) x + d, (double) y + d1, (double) z + d2, drop);
            entityitem.delayBeforeCanPickup = 10;
            world.spawnEntityInWorld(entityitem);
        }
    }
}
