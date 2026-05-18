package com.czqwq.EZMiner.mixin;

import java.util.ArrayList;

import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.czqwq.EZMiner.Config;

import gregtech.GTMod;
import gregtech.api.GregTechAPI;
import gregtech.api.enums.Materials;
import gregtech.api.enums.OrePrefixes;
import gregtech.api.util.GTOreDictUnificator;
import gregtech.common.blocks.TileEntityOres;

/**
 * Mixin for {@link TileEntityOres} that removes the hardcoded Fortune III cap on GT ore drops.
 *
 * <p>
 * When {@link Config#enableUnlimitedOreFortune} is {@code false} (the default), this mixin is
 * completely inert and the original {@code getDrops} method runs unchanged.
 *
 * <p>
 * When enabled, Fortune levels up to {@link Config#maxFortuneLevel} are honoured instead of the
 * vanilla cap of 3. If {@link Config#enableFortuneForPlacedOre} is also {@code true}, ores that
 * were placed by a player are treated as naturally generated and therefore also benefit from the
 * Fortune bonus.
 *
 * <p>
 * <strong>Small ores</strong> ({@code mMetaData >= 16000}) use their own gem/crushed/dust drop
 * table in GT5 and already scale with Fortune without a hard cap. This mixin therefore does not
 * replace that logic; it only applies {@link Config#enableFortuneForPlacedOre} (if set) and then
 * delegates back to the original method.
 *
 * <p>
 * <strong>This Mixin is applied once at JVM startup. Changes to the three fortune config options
 * cannot be picked up by {@code /EZMiner reloadConfig} — a full game restart is required.</strong>
 */
@Mixin(value = TileEntityOres.class, remap = false)
public abstract class MixinTileEntityOres {

    @Shadow
    public short mMetaData;

    @Shadow
    public boolean mNatural;

    @Shadow
    protected static boolean shouldSilkTouch;

    @Shadow
    protected static boolean shouldFortune;

    @Inject(method = "getDrops", at = @At("HEAD"), cancellable = true, remap = false)
    public void ezminer$injectGetDrops(Block aDroppedOre, int aFortune,
        CallbackInfoReturnable<ArrayList<ItemStack>> cir) {
        // Guard: only override when the feature is enabled.
        if (!Config.enableUnlimitedOreFortune) return;

        ArrayList<ItemStack> rList = new ArrayList<>();

        if (this.mMetaData <= 0) {
            rList.add(new ItemStack(Blocks.cobblestone, 1, 0));
            cir.setReturnValue(rList);
            return;
        }

        Materials aOreMaterial = GregTechAPI.sGeneratedMaterials[(this.mMetaData % 1000)];

        if (this.mMetaData < 16000) {
            boolean tIsRich = false;

            // Nether ore
            if (GTMod.gregtechproxy.mNetherOreYieldMultiplier && !tIsRich) {
                tIsRich = (this.mMetaData >= 1000 && this.mMetaData < 2000);
            }
            // End ore
            if (GTMod.gregtechproxy.mEndOreYieldMultiplier && !tIsRich) {
                tIsRich = (this.mMetaData >= 2000 && this.mMetaData < 3000);
            }

            if (shouldSilkTouch) {
                rList.add(new ItemStack(aDroppedOre, 1, this.mMetaData));
            } else {
                switch (GTMod.gregtechproxy.oreDropSystem) {
                    case Item -> {
                        rList.add(GTOreDictUnificator.get(OrePrefixes.rawOre, aOreMaterial, (tIsRich ? 2 : 1)));
                    }
                    case FortuneItem -> {
                        if (Config.enableFortuneForPlacedOre) mNatural = true;
                        if (shouldFortune && this.mNatural && aFortune > 0) {
                            int aMinAmount = 1;
                            int cappedFortune = Math.min(aFortune, Config.maxFortuneLevel);
                            int amount = aMinAmount + Math.max(
                                ((TileEntityOres) (Object) this).getWorldObj().rand
                                    .nextInt(cappedFortune * (tIsRich ? 2 : 1) + 2) - 1,
                                0);
                            for (int i = 0; i < amount; i++) {
                                rList.add(GTOreDictUnificator.get(OrePrefixes.rawOre, aOreMaterial, 1));
                            }
                        } else {
                            for (int i = 0; i < (tIsRich ? 2 : 1); i++) {
                                rList.add(GTOreDictUnificator.get(OrePrefixes.rawOre, aOreMaterial, 1));
                            }
                        }
                    }
                    case UnifiedBlock -> {
                        for (int i = 0; i < (tIsRich ? 2 : 1); i++) {
                            rList.add(new ItemStack(aDroppedOre, 1, this.mMetaData % 1000));
                        }
                    }
                    case PerDimBlock -> {
                        if (tIsRich) {
                            rList.add(new ItemStack(aDroppedOre, 1, this.mMetaData));
                        } else {
                            rList.add(new ItemStack(aDroppedOre, 1, this.mMetaData % 1000));
                        }
                    }
                    case Block -> {
                        rList.add(new ItemStack(aDroppedOre, 1, this.mMetaData));
                    }
                }
            }
        } else {
            // Small ore (mMetaData >= 16000): the original GT5 small-ore drop system uses its own
            // gem/crushed/dust table and already scales with fortune without a hard cap, so there
            // is nothing to override here. We only need to honour enableFortuneForPlacedOre by
            // marking placed ores as natural before delegating back to the original method.
            if (Config.enableFortuneForPlacedOre) {
                mNatural = true;
            }
            // Do NOT cancel — let the original GT5 small-ore drop logic run.
            return;
        }

        cir.setReturnValue(rList);
    }
}
