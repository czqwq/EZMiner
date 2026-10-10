package com.czqwq.EZMiner.mixin.early;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.czqwq.EZMiner.utils.FortuneCompatHelper;
import com.llamalad7.mixinextras.expression.Definition;
import com.llamalad7.mixinextras.expression.Expression;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;

import gregtech.common.ores.GTPPOreAdapter;

/**
 * Removes the {@code fortune > 3} clamp in GT++'s new-generation ore adapter.
 *
 * <p>
 * <strong>Generation scope:</strong> targets {@code gregtech.common.ores.GTPPOreAdapter},
 * which only exists on GT5U generations shipping the new ore system
 * ({@code gregtech.common.ores.*}). The older 5.09.x line clamps fortune inside
 * {@code gtPlusPlus.core.block.base.BlockBaseOre} instead and has no
 * {@code gregtech.common.ores} package, so on that line the feature is a silent no-op.
 * {@code mixins.EZMiner.json} keeps {@code "required": false} and the injections carry no
 * {@code require}, so a missing target degrades to a logged skip instead of a startup failure.
 * </p>
 */
@Mixin(value = GTPPOreAdapter.class, remap = false)
public abstract class MixinGTPPOreAdapter {

    @Definition(id = "fortuneLevel", local = @Local(type = int.class, argsOnly = true))
    @Expression("fortuneLevel > 3")
    @ModifyExpressionValue(
        method = "getBigOreDrops(Ljava/util/Random;Lgregtech/common/GTProxy$OreDropSystem;Lgregtech/common/ores/OreInfo;I)Ljava/util/ArrayList;",
        at = @At(value = "MIXINEXTRAS:EXPRESSION"))
    private boolean ezminer$removeOreFortuneCap(boolean original) {
        return FortuneCompatHelper.shouldKeepFortuneCapCheck(original);
    }
}
