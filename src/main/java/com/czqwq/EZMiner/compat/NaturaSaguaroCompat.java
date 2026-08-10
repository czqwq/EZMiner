package com.czqwq.EZMiner.compat;

import java.util.ArrayList;
import java.util.Queue;

import net.minecraft.block.Block;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.stats.StatList;
import net.minecraft.world.World;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.world.BlockEvent;

import org.joml.Vector3i;

import cpw.mods.fml.common.Loader;

/**
 * Bridge for Natura's giant cactus ({@code SaguaroBlock}) structure integrity.
 *
 * <p>
 * EZMiner's fast harvest paths deliberately suppress neighbor notifications
 * (raw {@code ExtendedBlockStorage} writes / {@code setBlock} without flag 1),
 * but SaguaroBlock is the one Natura block whose structural integrity relies
 * entirely on {@code onNeighborBlockChange → canBlockStay} self-destruction:
 * when the block below a saguaro is removed silently, the now-floating upper
 * blocks stay in the server world forever — no drop, no cascade — which the
 * client renders as "texture gone but block still there". Additionally the
 * non-zero metas (sapling 1-2, fruit arms 3-6) drop an unregistered
 * {@code ItemStack(NContent.seedFood, 1, meta)} because {@code damageDropped}
 * is not overridden while only damage 0 is registered.
 *
 * <p>
 * This bridge re-creates the vanilla cascade (neighbors whose
 * {@code canBlockStay} fails after a removal are appended to the work queue)
 * and fixes the invalid drops. All Natura class references live inside the
 * nested {@link Impl} holder, which the JVM only classloads when Natura is
 * present; when it is not, {@link #NATURA_LOADED} is a constant {@code false}
 * and the JIT eliminates the entire call chain.
 */
public final class NaturaSaguaroCompat {

    private static final boolean NATURA_LOADED = Loader.isModLoaded("Natura");

    private NaturaSaguaroCompat() {}

    /** True when {@code block} is a Natura saguaro block (Natura absent → false). */
    public static boolean isSaguaroBlock(Block block) {
        if (!NATURA_LOADED) return false;
        return Impl.isSaguaro(block);
    }

    /** True when the block at the given position is a Natura saguaro block. */
    public static boolean isSaguaroBlock(World world, int x, int y, int z) {
        if (!NATURA_LOADED) return false;
        return Impl.isSaguaro(world.getBlock(x, y, z));
    }

    /**
     * Called AFTER the block at ({@code x}, {@code y}, {@code z}) was removed.
     * Checks the neighbors whose support may have vanished (directly above and
     * the four horizontal sides — the below neighbor is unaffected) and appends
     * every saguaro that can no longer stay to {@code out}, so the caller's
     * work queue cascades naturally. {@code canBlockStay} is used (rather than
     * "is saguaro") so sand-based fruit arms and adjacent independent plants
     * survive.
     */
    public static void cascadeUnsupportedNeighbors(World world, int x, int y, int z, Queue<Vector3i> out) {
        if (!NATURA_LOADED) return;
        checkAndEnqueue(world, x, y + 1, z, out);
        checkAndEnqueue(world, x + 1, y, z, out);
        checkAndEnqueue(world, x - 1, y, z, out);
        checkAndEnqueue(world, x, y, z + 1, out);
        checkAndEnqueue(world, x, y, z - 1, out);
    }

    private static void checkAndEnqueue(World world, int x, int y, int z, Queue<Vector3i> out) {
        if (Impl.isSaguaro(world.getBlock(x, y, z)) && !Impl.canStay(world, x, y, z)) {
            out.add(new Vector3i(x, y, z));
        }
    }

    /**
     * Drop interception for saguaro blocks: non-zero metas drop
     * {@code ItemStack(seedFood, 1, 0)} instead of the unregistered
     * {@code ItemStack(seedFood, 1, meta)}. Drops are fired through the Forge
     * {@code HarvestDropsEvent} so EZMiner's drop collector (and Bandit) handle
     * them like any other harvest; if the list survives the event, the items are
     * spawned at the block position (covers non-collecting contexts).
     *
     * @return true when this method handled the drops — the caller must skip
     *         {@code block.harvestBlock}. Returns false for non-saguaro blocks
     *         and meta 0 (trunk drops the block itself, which is legal).
     */
    public static boolean maybeHarvest(World world, EntityPlayer player, int x, int y, int z, Block block, int meta) {
        if (!NATURA_LOADED) return false;
        if (!Impl.isSaguaro(block) || meta == 0) return false;

        player.addStat(StatList.mineBlockStatArray[Block.getIdFromBlock(block)], 1);
        player.addExhaustion(0.025F);
        ArrayList<ItemStack> drops = new ArrayList<>();
        drops.add(Impl.fruitStack());
        MinecraftForge.EVENT_BUS
            .post(new BlockEvent.HarvestDropsEvent(x, y, z, world, block, meta, 0, 1.0F, drops, player, false));
        if (!drops.isEmpty()) {
            // Survivors spawn as a regular ground drop (non-collecting contexts).
            float f = 0.7F;
            for (ItemStack drop : drops) {
                EntityItem item = new EntityItem(
                    world,
                    x + world.rand.nextFloat() * f + (1.0F - f) * 0.5D,
                    y + world.rand.nextFloat() * f + (1.0F - f) * 0.5D + 0.25D,
                    z + world.rand.nextFloat() * f + (1.0F - f) * 0.5D,
                    drop);
                item.delayBeforeCanPickup = 10;
                world.spawnEntityInWorld(item);
            }
        }
        return true;
    }

    /** Holder for all Natura class references — only classloaded when Natura is present. */
    private static final class Impl {

        private Impl() {}

        static boolean isSaguaro(Block block) {
            return block instanceof mods.natura.blocks.trees.SaguaroBlock;
        }

        static boolean canStay(World world, int x, int y, int z) {
            return ((mods.natura.blocks.trees.SaguaroBlock) world.getBlock(x, y, z)).canBlockStay(world, x, y, z);
        }

        /** Legal saguaro fruit: {@code NContent.seedFood} is only registered at damage 0. */
        static ItemStack fruitStack() {
            return new ItemStack(mods.natura.common.NContent.seedFood, 1, 0);
        }
    }
}
