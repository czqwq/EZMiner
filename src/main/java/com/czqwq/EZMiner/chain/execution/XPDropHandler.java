package com.czqwq.EZMiner.chain.execution;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.block.Block;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.item.EntityXPOrb;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

import com.czqwq.EZMiner.Config;

/**
 * Handles experience orb drops during chain mining operations.
 *
 * <p>
 * Two modes (controlled by {@link Config#xpDropMode}):
 * <ul>
 * <li><b>Immediate (0)</b> — spawns XP orbs per-block as vanilla does, but with
 * correctly computed XP (Fortune, Silk Touch accounted for).</li>
 * <li><b>Delayed (1, default)</b> — accumulates XP values during the chain and
 * spawns them when the chain ends. When {@link Config#mergeXPOrbs} is
 * {@code true} (default), all accumulated XP is merged into a single large orb;
 * otherwise individual per-block orbs are spawned.</li>
 * </ul>
 *
 * <p>
 * Fully decoupled from the Manager/operator lifecycle — uses a static per-player
 * map keyed by UUID. Callers only need the player reference to accumulate or
 * flush XP.
 */
public class XPDropHandler {

    /** Per-player accumulated XP values (individual per-block XP amounts). */
    private static final Map<UUID, List<Integer>> accumulatedXP = new HashMap<>();

    /**
     * Computes the experience that should be dropped when {@code player} breaks
     * {@code block}, mirroring what {@code BlockEvent.BreakEvent} would compute.
     *
     * @param block  the block being broken
     * @param world  world access for metadata-dependent XP
     * @param meta   block metadata
     * @param player the mining player
     * @return amount of XP to drop (0 if Silk Touch, creative, or block drops none)
     */
    public static int computeBlockXP(Block block, IBlockAccess world, int meta, EntityPlayer player) {
        if (player == null || player.capabilities.isCreativeMode) return 0;

        // Silk Touch prevents XP drops
        if (EnchantmentHelper.getSilkTouchModifier(player)) return 0;

        // Can't harvest → no XP (mirrors BreakEvent logic)
        if (!block.canHarvestBlock(player, meta)) return 0;

        int fortune = EnchantmentHelper.getFortuneModifier(player);
        return block.getExpDrop(world, meta, fortune);
    }

    /**
     * Primary entry point for block harvest paths. Computes XP for the broken block
     * and either drops it immediately (mode 0) or accumulates it (mode 1).
     *
     * @param world  the world
     * @param block  the block that was broken
     * @param meta   block metadata
     * @param x      block X coordinate
     * @param y      block Y coordinate
     * @param z      block Z coordinate
     * @param player the mining player
     */
    public static void handleBlockXP(World world, Block block, int meta, int x, int y, int z, EntityPlayer player) {
        if (world.isRemote) return;
        int exp = computeBlockXP(block, world, meta, player);
        if (exp <= 0) return;

        if (Config.xpDropMode == 0) {
            // Immediate mode: spawn XP orb at the block position (vanilla behavior)
            block.dropXpOnBlockBreak(world, x, y, z, exp);
        } else {
            // Delayed mode: accumulate for later flush
            accumulate(player, exp);
        }
    }

    /**
     * Overload for paths where XP has already been computed (e.g. from a
     * pre-fired {@code BreakEvent}). Still respects the immediate/delayed
     * mode setting.
     *
     * @param world  the world
     * @param block  the block that was broken
     * @param x      block X coordinate
     * @param y      block Y coordinate
     * @param z      block Z coordinate
     * @param exp    pre-computed XP amount
     * @param player the mining player
     */
    public static void handlePreComputedXP(World world, Block block, int x, int y, int z, int exp,
        EntityPlayer player) {
        if (world.isRemote || exp <= 0) return;

        if (Config.xpDropMode == 0) {
            block.dropXpOnBlockBreak(world, x, y, z, exp);
        } else {
            accumulate(player, exp);
        }
    }

    private static void accumulate(EntityPlayer player, int exp) {
        UUID uuid = player.getUniqueID();
        synchronized (accumulatedXP) {
            List<Integer> list = accumulatedXP.get(uuid);
            if (list == null) {
                list = new ArrayList<>();
                accumulatedXP.put(uuid, list);
            }
            list.add(exp);
        }
    }

    /**
     * Returns {@code true} if the given player has any accumulated XP waiting
     * to be flushed.
     */
    public static boolean hasAccumulatedXP(EntityPlayer player) {
        synchronized (accumulatedXP) {
            List<Integer> list = accumulatedXP.get(player.getUniqueID());
            return list != null && !list.isEmpty();
        }
    }

    /**
     * Spawns all accumulated XP for the given player and clears the accumulation.
     *
     * @param world        the world to spawn into
     * @param player       the player whose XP to flush
     * @param x            spawn X coordinate
     * @param y            spawn Y coordinate
     * @param z            spawn Z coordinate
     * @param mergeIntoOne if {@code true}, all XP is merged into a single large orb;
     *                     if {@code false}, individual per-block orbs are spawned
     */
    public static void flush(World world, EntityPlayer player, double x, double y, double z, boolean mergeIntoOne) {
        if (world.isRemote) return;
        UUID uuid = player.getUniqueID();
        List<Integer> values;
        synchronized (accumulatedXP) {
            values = accumulatedXP.remove(uuid);
        }
        if (values == null || values.isEmpty()) return;

        if (mergeIntoOne) {
            int total = 0;
            for (int v : values) {
                total += v;
            }
            if (total > 0) {
                world.spawnEntityInWorld(new EntityXPOrb(world, x, y, z, total));
            }
        } else {
            for (int v : values) {
                if (v > 0) {
                    world.spawnEntityInWorld(new EntityXPOrb(world, x, y, z, v));
                }
            }
        }
    }

    /**
     * Discards all accumulated XP for the given player without spawning anything.
     */
    public static void clear(EntityPlayer player) {
        synchronized (accumulatedXP) {
            accumulatedXP.remove(player.getUniqueID());
        }
    }
}
