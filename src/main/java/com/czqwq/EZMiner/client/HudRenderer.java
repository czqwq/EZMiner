package com.czqwq.EZMiner.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.resources.I18n;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.common.MinecraftForge;

import com.czqwq.EZMiner.ClientProxy;
import com.czqwq.EZMiner.Config;
import com.czqwq.EZMiner.EZMiner;
import com.czqwq.EZMiner.core.MinerModeState;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Draws a chain-style HUD overlay while the chain key is held.
 *
 * <pre>
 * §b[EZMiner] §a■ 连锁已启用
 * §7  └─ 客户端已渲染方块: §e{n}
 * §6  ○─ §e{主模式}
 * §7  └─ §f{子模式}
 * §7  └─ 已连锁方块: §e{count}  §7已连锁时间: §e{time}   (仅连锁进行中时显示)
 * </pre>
 */
@SideOnly(Side.CLIENT)
public class HudRenderer {

    @SubscribeEvent
    public void onRenderOverlay(RenderGameOverlayEvent.Post event) {
        if (event.type != RenderGameOverlayEvent.ElementType.ALL) return;
        if (!Config.usePreview) return;

        Minecraft mc = Minecraft.getMinecraft();
        if (mc.currentScreen != null) return; // hide when a GUI is open

        ClientStateContainer state = ((ClientProxy) EZMiner.proxy).clientState;
        if (!state.chainClientState.keyPressed) return;
        MinerModeState modeState = state.minerModeState;
        FontRenderer fr = mc.fontRenderer;

        String mainModeName = I18n.format(modeState.currentMainMode());
        String subModeName = I18n.format(modeState.currentSubMode());
        int chainCount = state.chainClientState.chainedCount;
        long chainElapsedMs = state.chainClientState.elapsedMs;
        int previewCount = state.previewRenderedCount;

        int x = Config.hudPosX;
        int y = Config.hudPosY;
        int lineH = fr.FONT_HEIGHT + 2;

        // Line 1: header
        fr.drawStringWithShadow("\u00a7b[EZMiner] \u00a7a\u25a0 " + I18n.format("ezminer.hud.active"), x, y, 0xFFFFFF);
        y += lineH;

        // Line 2: preview rendered count
        fr.drawStringWithShadow(
            "\u00a77  \u2514\u2500 " + I18n.format("ezminer.hud.previewCount") + ": \u00a7e" + previewCount,
            x,
            y,
            0xFFFFFF);
        y += lineH;

        // Line 3: main mode
        fr.drawStringWithShadow("\u00a76  \u25cb\u2500 \u00a7e" + mainModeName, x, y, 0xFFFFFF);
        y += lineH;

        // Line 4: sub mode
        fr.drawStringWithShadow("\u00a77  \u2514\u2500 \u00a7f" + subModeName, x, y, 0xFFFFFF);

        // Lines 5-6: chain count and elapsed time (only while a chain operation is running)
        if (state.chainClientState.inOperate) {
            y += lineH;
            fr.drawStringWithShadow(
                "\u00a77  \u2514\u2500 " + I18n.format("ezminer.hud.chainCount") + ": \u00a7e" + chainCount,
                x,
                y,
                0xFFFFFF);
            y += lineH;
            fr.drawStringWithShadow(
                "\u00a77  \u2514\u2500 " + I18n.format("ezminer.hud.chainElapsed")
                    + ": \u00a7e"
                    + formatElapsed(chainElapsedMs),
                x,
                y,
                0xFFFFFF);
        }
    }

    /**
     * Formats a duration in milliseconds for display.
     * Values under 60 s are shown as {@code X.Xs}; minutes are shown as {@code X:XX.Xs}.
     */
    private static String formatElapsed(long ms) {
        if (ms < 0) ms = 0;
        long totalSeconds = ms / 1000;
        long milliRemainder = ms % 1000;
        long minutes = totalSeconds / 60;
        long seconds = totalSeconds % 60;
        if (minutes > 0) {
            return String.format("%d:%02d.%ds", minutes, seconds, milliRemainder / 100);
        }
        return String.format("%d.%ds", seconds, milliRemainder / 100);
    }

    public void registry() {
        MinecraftForge.EVENT_BUS.register(this);
        FMLCommonHandler.instance()
            .bus()
            .register(this);
    }
}
