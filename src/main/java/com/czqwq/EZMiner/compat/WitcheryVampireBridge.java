package com.czqwq.EZMiner.compat;

import net.minecraft.entity.player.EntityPlayer;

import cpw.mods.fml.common.Loader;

/**
 * Bridge for Witchery vampire bare-hand mining ability.
 *
 * <p>
 * In Witchery, vampires at level 5+ gain the ability to break stone-level blocks
 * with their bare hands — as if they were holding a pickaxe. Vanilla
 * {@code Block.canHarvestBlock} returns {@code false} for bare hands regardless
 * of the vampire's ability, so EZMiner's harvest paths would skip drop generation.
 * This bridge tells EZMiner to treat vampire bare-hand mining the same as using a
 * proper tool.
 * </p>
 *
 * <p>
 * <strong>Decoupling:</strong> all Witchery class references are inside the nested
 * {@link Impl} holder, which the JVM classloads lazily on first use — never when
 * Witchery is absent. When Witchery is not installed, {@link #WITCHERY_LOADED} is
 * a constant {@code false} and the JIT eliminates the entire call.
 * </p>
 */
public final class WitcheryVampireBridge {

    private static final boolean WITCHERY_LOADED = Loader.isModLoaded("witchery");

    /** Vampire level at which bare-hand mining is granted. */
    private static final int VAMPIRE_MINING_LEVEL = 5;

    private WitcheryVampireBridge() {}

    /**
     * Returns {@code true} when the player is a Witchery vampire with the bare-hand
     * mining ability AND is currently holding nothing (bare hands). Should be OR'd
     * with {@code block.canHarvestBlock(player, meta)} in harvest paths so that
     * drops are generated for vampires.
     *
     * @param player the mining player
     * @return {@code true} if the player can harvest the block despite having no tool
     */
    public static boolean canHarvestWithBareHands(EntityPlayer player) {
        if (!WITCHERY_LOADED) return false;
        if (player == null) return false;
        // Only applies when the player's hand is empty (no tool equipped).
        if (player.getCurrentEquippedItem() != null) return false;
        return Impl.isVampireWithMiningAbility(player);
    }

    /**
     * Returns {@code true} if the player is a Witchery vampire (regardless of
     * equipped item). Useful for general gating — callers that want to know if
     * the player has the vampire mining ability at all.
     */
    public static boolean isVampireMiner(EntityPlayer player) {
        if (!WITCHERY_LOADED) return false;
        if (player == null) return false;
        return Impl.isVampireWithMiningAbility(player);
    }

    /** Holder for all Witchery class references — only classloaded when Witchery is present. */
    private static final class Impl {

        private Impl() {}

        static boolean isVampireWithMiningAbility(EntityPlayer player) {
            com.emoniph.witchery.common.ExtendedPlayer ext = com.emoniph.witchery.common.ExtendedPlayer.get(player);
            if (ext == null) return false;
            return ext.isVampire() && ext.getVampireLevel() >= VAMPIRE_MINING_LEVEL;
        }
    }
}
