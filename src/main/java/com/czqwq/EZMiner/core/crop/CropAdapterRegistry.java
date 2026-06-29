package com.czqwq.EZMiner.core.crop;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.world.World;

import com.czqwq.EZMiner.compat.EtFuturumCropCompat;

import cpw.mods.fml.common.Loader;

/**
 * Central registry for per-mod crop adapters.
 *
 * <p>
 * Call {@link #init()} once during FML post-initialisation (after all mods are loaded). Subsequent
 * calls to {@link #isCrop}, {@link #isMatureCrop} and {@link #harvest} iterate the registered
 * adapters in order and return the result of the first matching adapter.
 */
public class CropAdapterRegistry {

    private static List<ICropAdapter> adapters = Collections.emptyList();
    private static volatile boolean initialized = false;

    /**
     * Registers all crop adapters whose corresponding mods are present on the current classpath.
     * Must be called from FML post-initialisation so that {@link Loader#isModLoaded} returns stable
     * results.
     */
    public static void init() {
        if (initialized) return;
        List<ICropAdapter> list = new ArrayList<>();
        list.add(new VanillaCropAdapter());
        if (Loader.isModLoaded("IC2")) {
            list.add(new Ic2CropAdapter());
        }
        if (Loader.isModLoaded("Natura")) {
            list.add(new NaturaCropAdapter());
        }
        if (Loader.isModLoaded("cropsnh")) {
            list.add(new CropsNHCropAdapter());
        }
        // EFR – Et Futurum Requiem crops (berry bush, cave vines)
        EtFuturumCropCompat.init();
        if (EtFuturumCropCompat.isLoaded()) {
            list.add(new EtFuturumCropCompat());
        }
        adapters = Collections.unmodifiableList(list);
        initialized = true;
    }

    /**
     * Returns {@code true} if any registered adapter recognises the block at {@code (x, y, z)} as
     * a crop (mature or not). Used by the BFS scan in
     * {@link com.czqwq.EZMiner.core.founder.CropFounder}.
     */
    public static boolean isCrop(World world, int x, int y, int z) {
        for (ICropAdapter adapter : adapters) {
            if (adapter.isCrop(world, x, y, z)) return true;
        }
        return false;
    }

    /**
     * Returns {@code true} if any registered adapter considers the crop at {@code (x, y, z)} ready
     * to harvest.
     */
    public static boolean isMatureCrop(World world, int x, int y, int z) {
        for (ICropAdapter adapter : adapters) {
            if (adapter.isMatureCrop(world, x, y, z)) return true;
        }
        return false;
    }

    /**
     * Delegates the harvest to the first adapter that recognises the crop at {@code (x, y, z)}.
     *
     * @return {@code true} if a crop was successfully harvested.
     */
    public static boolean harvest(EntityPlayerMP player, int x, int y, int z) {
        World world = player.worldObj;
        for (ICropAdapter adapter : adapters) {
            if (adapter.isCrop(world, x, y, z)) {
                return adapter.harvest(player, x, y, z);
            }
        }
        return false;
    }
}
