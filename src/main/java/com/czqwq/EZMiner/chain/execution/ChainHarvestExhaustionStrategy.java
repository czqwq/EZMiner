package com.czqwq.EZMiner.chain.execution;

import java.lang.reflect.Field;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.FoodStats;

import org.joml.Vector3i;

import cpw.mods.fml.relauncher.ReflectionHelper;

/**
 * Replaces vanilla exhaustion with configured per-block exhaustion.
 * For batched harvests, use {@link #getExhaustion} / {@link #setExhaustion}
 * around the batch to avoid per-block reflection overhead.
 */
public class ChainHarvestExhaustionStrategy {

    /** Reflected {@code FoodStats.foodExhaustionLevel} (SRG: {@code field_75126_c}). */
    private static final Field FOOD_EXHAUSTION_LEVEL = ReflectionHelper
        .findField(FoodStats.class, "foodExhaustionLevel", "field_75126_c");

    /**
     * Harvests one position and charges the configured exhaustion <strong>only when the harvest
     * actually happened</strong>.
     *
     * <p>
     * The previous body applied {@code exhaustionBefore + configuredExhaustion} unconditionally and
     * merely returned the {@code harvested} flag, so a crop position whose adapter declined the
     * harvest (e.g. a mature crop that cannot be re-planted, or a non-crop block reached through the
     * crop path) still drained the player's hunger. The batched block paths never did this — they
     * multiply by the number of blocks actually removed.
     * </p>
     */
    public boolean harvestWithConfiguredExhaustion(EntityPlayerMP player, Vector3i pos, float configuredExhaustion,
        ChainActionExecutor actionExecutor) {
        FoodStats food = player.getFoodStats();
        float exhaustionBefore = getExhaustion(food);
        boolean harvested = actionExecutor.execute(pos, player);
        if (!harvested) return false;
        try {
            // Clamp at 4.0: FoodStats.addExhaustion decrements the food level as soon as the level
            // exceeds 4.0, and this is an absolute write that discards the vanilla 0.025/block the
            // harvest itself already added. Writing a value above 4.0 would let the next harvest
            // decrement hunger a second time for the same exhaustion.
            FOOD_EXHAUSTION_LEVEL.setFloat(food, Math.min(exhaustionBefore + configuredExhaustion, 4.0F));
        } catch (IllegalAccessException ex) {
            player.addExhaustion(configuredExhaustion);
        }
        return true;
    }

    public float getExhaustion(FoodStats food) {
        try {
            return FOOD_EXHAUSTION_LEVEL.getFloat(food);
        } catch (IllegalAccessException ex) {
            return 0f;
        }
    }

    public void setExhaustion(FoodStats food, float newValue) {
        try {
            FOOD_EXHAUSTION_LEVEL.setFloat(food, newValue);
        } catch (IllegalAccessException ignored) {}
    }
}
