package com.czqwq.EZMiner.mixin.early;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiIngameMenu;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.czqwq.EZMiner.client.gui.EZMinerModOptionsScreen;

/**
 * Replaces FML's broken in-game "Mod Options..." flow.
 *
 * <p>
 * In 1.7.10 FML the pause-menu button id 12 calls
 * {@code FMLClientHandler.showInGameModOptions()}, which opens the unfinished
 * {@code GuiIngameModOptions}/{@code GuiModOptionList} debug stub — what dev
 * environments show as the hardcoded "Test 1 / TEST 2 / DISABLED" rows. This
 * mixin intercepts that branch and opens {@link EZMinerModOptionsScreen} instead,
 * which actually lists config-capable mods (EZMiner included) and opens their
 * config GUIs.
 *
 * <p>
 * Only the id-12 branch is cancelled; every other pause-menu button keeps its
 * vanilla behavior.
 */
@Mixin(GuiIngameMenu.class)
public abstract class MixinGuiIngameMenu {

    @Inject(method = "actionPerformed", at = @At("HEAD"), cancellable = true)
    private void ezminer$openFunctionalModOptions(GuiButton button, CallbackInfo ci) {
        if (button.id == 12) {
            EZMinerModOptionsScreen.open((GuiIngameMenu) (Object) this);
            ci.cancel();
        }
    }
}
