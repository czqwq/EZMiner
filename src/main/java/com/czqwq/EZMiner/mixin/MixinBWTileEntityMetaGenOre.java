package com.czqwq.EZMiner.mixin;

import java.util.ArrayList;
import java.util.Random;

import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.czqwq.EZMiner.Config;

import bartworks.system.material.BWTileEntityMetaGeneratedOre;
import bartworks.system.material.Werkstoff;
import gregtech.GTMod;
import gregtech.api.enums.Materials;
import gregtech.api.enums.OrePrefixes;
import gregtech.api.util.GTOreDictUnificator;

/**
 * Mixin for {@code BWTileEntityMetaGeneratedOre} that removes the Fortune III cap on BartWorks ore
 * drops.
 *
 * <p>
 * When {@link Config#enableUnlimitedOreFortune} is {@code false} (the default), this mixin is
 * completely inert and the original {@code getDrops} method runs unchanged.
 *
 * <p>
 * When enabled, Fortune levels up to {@link Config#maxFortuneLevel} are honoured. If
 * {@link Config#enableFortuneForPlacedOre} is also {@code true}, manually placed ores are treated
 * as naturally generated.
 *
 * <p>
 * <strong>This Mixin is applied once at JVM startup. Changes to the three fortune config options
 * cannot be picked up by {@code /EZMiner reloadConfig} — a full game restart is required.</strong>
 */
@Mixin(value = BWTileEntityMetaGeneratedOre.class, remap = false)
public abstract class MixinBWTileEntityMetaGenOre {

    @Shadow
    protected static boolean shouldFortune;

    @Shadow
    protected static boolean shouldSilkTouch;

    @Shadow
    public boolean mNatural;

    @Shadow
    protected abstract Block GetProperBlock();

    @Inject(method = "getDrops", at = @At("HEAD"), cancellable = true, remap = false)
    public void ezminer$injectGetDrops(int aFortune, CallbackInfoReturnable<ArrayList<ItemStack>> cir) {
        // Guard: only override when the feature is enabled.
        if (!Config.enableUnlimitedOreFortune) return;

        ArrayList<ItemStack> rList = new ArrayList<>();
        BWTileEntityMetaGeneratedOre self = (BWTileEntityMetaGeneratedOre) (Object) this;

        if (self.mMetaData <= 0) {
            rList.add(new ItemStack(Blocks.cobblestone, 1, 0));
            cir.setReturnValue(rList);
            return;
        }

        Materials aOreMaterial = Werkstoff.werkstoffHashMap.get(self.mMetaData)
            .getBridgeMaterial();

        if (shouldSilkTouch) {
            rList.add(new ItemStack(GetProperBlock(), 1, self.mMetaData));
        } else {
            switch (GTMod.gregtechproxy.oreDropSystem) {
                case Item -> {
                    rList.add(GTOreDictUnificator.get(OrePrefixes.rawOre, aOreMaterial, 1));
                }
                case FortuneItem -> {
                    if (Config.enableFortuneForPlacedOre) mNatural = true;
                    if (shouldFortune && this.mNatural && aFortune > 0) {
                        int aMinAmount = 1;
                        int cappedFortune = Math.min(aFortune, Config.maxFortuneLevel);
                        long amount = (long) new Random().nextInt(cappedFortune) + aMinAmount;
                        for (int i = 0; i < amount; i++) {
                            rList.add(GTOreDictUnificator.get(OrePrefixes.rawOre, aOreMaterial, 1));
                        }
                    } else {
                        rList.add(GTOreDictUnificator.get(OrePrefixes.rawOre, aOreMaterial, 1));
                    }
                }
                case UnifiedBlock -> {
                    rList.add(new ItemStack(GetProperBlock(), 1, self.mMetaData));
                }
                case PerDimBlock -> {
                    rList.add(new ItemStack(GetProperBlock(), 1, self.mMetaData));
                }
                case Block -> {
                    rList.add(new ItemStack(GetProperBlock(), 1, self.mMetaData));
                }
            }
        }

        cir.setReturnValue(rList);
    }
}
