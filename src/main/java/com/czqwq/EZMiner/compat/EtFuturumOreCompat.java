package com.czqwq.EZMiner.compat;

import net.minecraft.block.Block;

/**
 * Et Futurum Requiem ore-block compatibility detector.
 *
 * <p>
 * Uses reflection via {@link ClassNameCompatSupport} so that EZMiner has zero
 * compile-time dependency on EFR. When EFR is not installed every method returns
 * {@code false}.
 * </p>
 *
 * <p>
 * Called from {@link com.czqwq.EZMiner.core.founder.DeterminingIdentical#computeIsOreBlock(Block)}
 * to extend the ore detection chain.
 * </p>
 *
 * <p>
 * Ported from QZMiner {@code EtFuturumOreCompatAdapter}.
 * </p>
 */
public final class EtFuturumOreCompat {

    private static final String ET_FUTURUM_MODID = "etfuturum";

    private static volatile boolean initialized;
    private static boolean efLoaded;

    // ── Cached EFR ore classes ────────────────────────────────────────────────
    private static Class<?> netherGoldOreType;
    private static Class<?> ancientDebrisType;
    private static Class<?> deepslateOreType;
    private static Class<?> moddedDeepslateOreType;

    private EtFuturumOreCompat() {}

    /**
     * Resolves all EFR ore classes once. Idempotent — subsequent calls are no-ops.
     */
    public static void init() {
        if (initialized) return;
        initialized = true;

        netherGoldOreType = ClassNameCompatSupport.resolveClass("ganymedes01.etfuturum.blocks.ores.BlockOreNetherGold");
        ancientDebrisType = ClassNameCompatSupport.resolveClass("ganymedes01.etfuturum.blocks.BlockAncientDebris");
        deepslateOreType = ClassNameCompatSupport.resolveClass("ganymedes01.etfuturum.blocks.ores.BaseDeepslateOre");
        moddedDeepslateOreType = ClassNameCompatSupport
            .resolveClass("ganymedes01.etfuturum.blocks.ores.modded.BlockGeneralModdedDeepslateOre");

        efLoaded = netherGoldOreType != null || ancientDebrisType != null
            || deepslateOreType != null
            || moddedDeepslateOreType != null;
    }

    /**
     * Returns {@code true} when at least one EFR ore class was successfully resolved.
     */
    public static boolean isLoaded() {
        return efLoaded;
    }

    /**
     * Returns {@code true} if {@code block} is an EFR ore block.
     *
     * <p>
     * Checks registered EFR classes first, then falls back to registry-name matching
     * ({@code etfuturum:*_ore} or {@code etfuturum:ancient_debris}).
     * </p>
     */
    public static boolean isOreBlock(Block block) {
        if (block == null || !efLoaded) return false;

        // Direct class checks (cached Class<?> references)
        if (ClassNameCompatSupport.isInstance(netherGoldOreType, block)) return true;
        if (ClassNameCompatSupport.isInstance(ancientDebrisType, block)) return true;
        if (ClassNameCompatSupport.isInstance(deepslateOreType, block)) return true;
        if (ClassNameCompatSupport.isInstance(moddedDeepslateOreType, block)) return true;

        // Registry-name fallback for any EFR ore not covered by explicit class checks
        return isOreByRegistryName(block);
    }

    /**
     * Registry-name-based ore detection for EFR blocks not covered by explicit class checks.
     */
    private static boolean isOreByRegistryName(Block block) {
        Object registryName = Block.blockRegistry.getNameForObject(block);
        if (!(registryName instanceof String)) return false;

        String name = (String) registryName;
        if (!name.startsWith(ET_FUTURUM_MODID + ":")) return false;

        String path = name.substring(ET_FUTURUM_MODID.length() + 1);
        return path.endsWith("_ore") || "ancient_debris".equals(path);
    }

    /**
     * Returns the registry name of {@code block} as a String, or {@code null}.
     */
    static String getRegistryName(Block block) {
        Object name = Block.blockRegistry.getNameForObject(block);
        return name instanceof String ? (String) name : null;
    }
}
