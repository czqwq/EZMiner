package com.czqwq.EZMiner.chain.execution;

import java.lang.reflect.Field;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.FoodStats;

import org.joml.Vector3i;

import cpw.mods.fml.relauncher.ReflectionHelper;

/**
 * Harvest strategy that replaces vanilla exhaustion with configured exhaustion.
 */
public class ChainHarvestExhaustionStrategy {

    /**
     * Reflected access to {@code FoodStats.foodExhaustionLevel} (private in 1.7.10).
     * SRG name: {@code field_75126_c}.
     */
    private static final Field FOOD_EXHAUSTION_LEVEL = ReflectionHelper
        .findField(FoodStats.class, "foodExhaustionLevel", "field_75126_c");

    public boolean harvestWithConfiguredExhaustion(EntityPlayerMP player, Vector3i pos, float configuredExhaustion,
        ChainActionExecutor actionExecutor) {
        FoodStats food = player.getFoodStats();
        float exhaustionBefore;
        try {
            exhaustionBefore = FOOD_EXHAUSTION_LEVEL.getFloat(food);
        } catch (IllegalAccessException ex) {
            exhaustionBefore = 0f;
        }
        boolean harvested = actionExecutor.execute(pos, player);
        try {
            FOOD_EXHAUSTION_LEVEL.setFloat(food, exhaustionBefore + configuredExhaustion);
        } catch (IllegalAccessException ex) {
            player.addExhaustion(configuredExhaustion);
        }
        return harvested;
    }
}
