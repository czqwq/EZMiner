package com.czqwq.EZMiner.compat;

import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.world.World;

import com.czqwq.EZMiner.core.crop.ICropAdapter;

/**
 * Et Futurum Requiem crop compatibility adapter.
 *
 * <p>
 * Handles EFR berry bushes ({@code BlockBerryBush}) and cave vines
 * ({@code BaseCaveVines}). Uses reflection via {@link ClassNameCompatSupport}
 * for class detection so EZMiner has zero compile-time dependency on EFR.
 * </p>
 *
 * <p>
 * Registered in {@link com.czqwq.EZMiner.core.crop.CropAdapterRegistry#init()}
 * when the {@code etfuturum} mod is present.
 * </p>
 *
 * <p>
 * Et Futurum Requiem crop compatibility adapter.
 * </p>
 */
public class EtFuturumCropCompat implements ICropAdapter {

    private static volatile boolean initialized;
    private static boolean efLoaded;

    // ── Cached EFR crop classes ───────────────────────────────────────────────
    private static Class<?> berryBushType;
    private static Class<?> baseCaveVinesType;

    /**
     * Resolves EFR crop classes once. Idempotent — subsequent calls are no-ops.
     */
    public static void init() {
        if (initialized) return;
        initialized = true;

        berryBushType = ClassNameCompatSupport.resolveClass("ganymedes01.etfuturum.blocks.BlockBerryBush");
        baseCaveVinesType = ClassNameCompatSupport.resolveClass("ganymedes01.etfuturum.blocks.BaseCaveVines");

        efLoaded = berryBushType != null || baseCaveVinesType != null;
    }

    /**
     * Returns {@code true} when at least one EFR crop class was successfully resolved.
     */
    public static boolean isLoaded() {
        return efLoaded;
    }

    // ── ICropAdapter implementation ───────────────────────────────────────────

    @Override
    public boolean isCrop(World world, int x, int y, int z) {
        if (!efLoaded) return false;
        Block block = world.getBlock(x, y, z);
        return ClassNameCompatSupport.isInstance(berryBushType, block)
            || ClassNameCompatSupport.isInstance(baseCaveVinesType, block);
    }

    @Override
    public boolean isMatureCrop(World world, int x, int y, int z) {
        if (!efLoaded) return false;
        Block block = world.getBlock(x, y, z);
        int meta = world.getBlockMetadata(x, y, z);

        // Berry bush: meta 2–3 have berries (meta 3 = fully grown, meta 2 = first berry stage)
        if (ClassNameCompatSupport.isInstance(berryBushType, block)) {
            return meta >= 2;
        }
        // Cave vines: meta 1 = has glow berries
        if (ClassNameCompatSupport.isInstance(baseCaveVinesType, block)) {
            return meta == 1;
        }
        return false;
    }

    @Override
    public boolean harvest(EntityPlayerMP player, int x, int y, int z) {
        if (!efLoaded) return false;
        World world = player.worldObj;
        Block block = world.getBlock(x, y, z);

        if (!isCrop(world, x, y, z)) return false;

        // EFR crops use right-click (onBlockActivated) for non-destructive harvest.
        // Since onBlockActivated is a public method on Block, we can call it directly
        // without reflection — we only need the Block reference from the world.
        return block.onBlockActivated(world, x, y, z, player, 0, 0.5F, 0.5F, 0.5F);
    }
}
