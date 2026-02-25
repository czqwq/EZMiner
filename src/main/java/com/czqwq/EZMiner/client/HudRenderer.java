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
 * Draws a chain-style HUD overlay in the top-left corner while the chain key is held.
 *
 * <pre>
 * §b[EZMiner] §a✔ 连锁已启用
 * §6  ○ §e{主模式}
 * §7  └─ §f{子模式}
 * </pre>
 */
@SideOnly(Side.CLIENT)
public class HudRenderer {

    /** Set to true by {@link KeyListener} when chain key is held. */
    public boolean chainActive = false;

    @SubscribeEvent
    public void onRenderOverlay(RenderGameOverlayEvent.Post event) {
        if (event.type != RenderGameOverlayEvent.ElementType.ALL) return;
        if (!chainActive) return;
        if (!Config.usePreview) return;

        Minecraft mc = Minecraft.getMinecraft();
        if (mc.currentScreen != null) return; // hide when a GUI is open

        MinerModeState state = ((ClientProxy) EZMiner.proxy).clientState.minerModeState;
        FontRenderer fr = mc.fontRenderer;

        String mainModeName = I18n.format(state.currentMainMode());
        String subModeName = I18n.format(state.currentSubMode());

        int x = 5;
        int y = 5;
        int lineH = fr.FONT_HEIGHT + 2;

        // Line 1: header
        fr.drawStringWithShadow("\u00a7b[EZMiner] \u00a7a\u25a0 " + I18n.format("ezminer.hud.active"), x, y, 0xFFFFFF);
        y += lineH;

        // Line 2: main mode (chain link first node)
        fr.drawStringWithShadow("\u00a76  \u25cb\u2500 \u00a7e" + mainModeName, x, y, 0xFFFFFF);
        y += lineH;

        // Line 3: sub mode (chain link second node, indented)
        fr.drawStringWithShadow("\u00a77  \u2514\u2500 \u00a7f" + subModeName, x, y, 0xFFFFFF);
    }

    public void registry() {
        MinecraftForge.EVENT_BUS.register(this);
        FMLCommonHandler.instance()
            .bus()
            .register(this);
    }
}
