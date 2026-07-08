package com.czqwq.EZMiner.compat;

import net.minecraft.block.Block;

/**
 * Et Futurum Requiem ore-block compat — reflection-based, zero compile-time dep.
 * Called from {@link com.czqwq.EZMiner.core.founder.DeterminingIdentical}.
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

    /** Resolves all EFR ore classes once. Idempotent. */
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

    public static boolean isLoaded() { return efLoaded; }

    /** True if block is an EFR ore (class check → registry-name fallback). */
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

    /** Registry-name fallback: etfuturum:*_ore or etfuturum:ancient_debris. */
    private static boolean isOreByRegistryName(Block block) {
        Object registryName = Block.blockRegistry.getNameForObject(block);
        if (!(registryName instanceof String)) return false;

        String name = (String) registryName;
        if (!name.startsWith(ET_FUTURUM_MODID + ":")) return false;

        String path = name.substring(ET_FUTURUM_MODID.length() + 1);
        return path.endsWith("_ore") || "ancient_debris".equals(path);
    }

    static String getRegistryName(Block block) {
        Object name = Block.blockRegistry.getNameForObject(block);
        return name instanceof String ? (String) name : null;
    }
}
