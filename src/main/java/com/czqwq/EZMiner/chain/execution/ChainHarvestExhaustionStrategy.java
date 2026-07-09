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

    public boolean harvestWithConfiguredExhaustion(EntityPlayerMP player, Vector3i pos, float configuredExhaustion,
        ChainActionExecutor actionExecutor) {
        FoodStats food = player.getFoodStats();
        float exhaustionBefore = getExhaustion(food);
        boolean harvested = actionExecutor.execute(pos, player);
        try {
            FOOD_EXHAUSTION_LEVEL.setFloat(food, exhaustionBefore + configuredExhaustion);
        } catch (IllegalAccessException ex) {
            player.addExhaustion(configuredExhaustion);
        }
        return harvested;
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
