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
 *
 * <p>
 * The "[EZMiner]" brand in the header line renders with a left-to-right rainbow sweep:
 * one character at a time is coloured with the next rainbow hue and simultaneously bounces
 * upward by {@value #BOUNCE_HEIGHT_PX} pixels before returning to the baseline.
 */
@SideOnly(Side.CLIENT)
public class HudRenderer {

    // ── Rainbow animation constants ──────────────────────────────────────────────────────────
    /** Minecraft § colour-codes that form the rainbow sequence (red → pink). */
    private static final String[] RAINBOW_CODES = { "\u00a7c", // red
        "\u00a76", // orange
        "\u00a7e", // yellow
        "\u00a7a", // green
        "\u00a7b", // cyan
        "\u00a79", // blue
        "\u00a75", // purple
        "\u00a7d", // pink
    };
    /** Text whose characters are individually animated. */
    private static final String BRAND = "EZMiner";
    /** Milliseconds each character holds the spotlight before advancing to the next. */
    private static final int CHAR_PERIOD_MS = 300;
    /** Peak upward displacement (in screen pixels) of the bouncing character. */
    private static final int BOUNCE_HEIGHT_PX = 3;

    // ── Wave animation constants ─────────────────────────────────────────────────────────────
    /** Milliseconds each character holds the spotlight in the wave phase. */
    private static final long WAVE_LETTER_MS = 250;
    /**
     * Milliseconds between each character turning white in the fill phase.
     * 7 letters × 114 ms ≈ 800 ms total fill duration.
     */
    private static final long FILL_LETTER_MS = 114;
    /** Milliseconds all characters remain fully white before the cycle resets (1 s). */
    private static final long HOLD_MS = 1000;

    @SubscribeEvent
    public void onRenderOverlay(RenderGameOverlayEvent.Post event) {
        if (event.type != RenderGameOverlayEvent.ElementType.ALL) return;
        if (!Config.isPreviewEnabled()) return;

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

        // Smart-tool-switch status — shown above the main HUD when the chain key is pressed.
        // Green when the feature is enabled, red when disabled.
        if (Config.smartToolSwitchEnabled) {
            SmartToolSwitchHandler stsHandler = ((ClientProxy) EZMiner.proxy).smartToolSwitchHandler;
            String statusKey = stsHandler.isActive() ? "ezminer.hud.smartToolSwitch.enabled"
                : "ezminer.hud.smartToolSwitch.disabled";
            String statusColor = stsHandler.isActive() ? "§a" : "§7";
            fr.drawStringWithShadow(
                "§b◆ " + I18n.format("ezminer.hud.smartToolSwitch") + ": " + statusColor + I18n.format(statusKey),
                x,
                y,
                0xFFFFFF);
            y += lineH;
        }

        // Line 1: animated header – "[EZMiner] ■ 连锁已启用"
        drawAnimatedHeader(fr, x, y, "\u00a7a\u25a0 " + I18n.format("ezminer.hud.active"));
        y += lineH;

        // Line 2: preview / cached count (label and value differ by mode).
        // In cached chain mode the server pre-calculates and syncs positions;
        // the HUD shows "Cached Blocks" with the server-authoritative count.
        // In all other modes the HUD shows "Rendered Blocks" from the local preview.
        if (modeState.isCachedChainMode()) {
            java.util.List<org.joml.Vector3i> cachedPositions = state.cachedPreviewPositions;
            int cachedCount = cachedPositions != null ? cachedPositions.size() : 0;
            fr.drawStringWithShadow(
                "\u00a77  \u2514\u2500 " + I18n.format("ezminer.hud.cachedCount") + ": \u00a7e" + cachedCount,
                x,
                y,
                0xFFFFFF);
        } else {
            fr.drawStringWithShadow(
                "\u00a77  \u2514\u2500 " + I18n.format("ezminer.hud.previewCount") + ": \u00a7e" + previewCount,
                x,
                y,
                0xFFFFFF);
        }
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

        // Minesweeper mode: show probe cooldown countdown
        if (modeState.mainMode == 2 && modeState.specialMode == 0) {
            y += lineH;
            long remainingMs = state.minesweeperNextProbeClientMs - System.currentTimeMillis();
            if (remainingMs > 0) {
                fr.drawStringWithShadow(
                    "\u00a77  \u2514\u2500 " + I18n.format("ezminer.hud.minesweeper.cooldown")
                        + ": \u00a7e"
                        + formatElapsed(remainingMs),
                    x,
                    y,
                    0xFFFFFF);
            } else {
                fr.drawStringWithShadow(
                    "\u00a77  \u2514\u2500 \u00a7a" + I18n.format("ezminer.hud.minesweeper.ready"),
                    x,
                    y,
                    0xFFFFFF);
            }
        }
        // Sudoku mode: show probe cooldown countdown (independent from minesweeper)
        if (modeState.mainMode == 2 && modeState.specialMode == 2) {
            y += lineH;
            long remainingMs = state.sudokuNextProbeClientMs - System.currentTimeMillis();
            if (remainingMs > 0) {
                fr.drawStringWithShadow(
                    "\u00a77  \u2514\u2500 " + I18n.format("ezminer.hud.minesweeper.cooldown")
                        + ": \u00a7e"
                        + formatElapsed(remainingMs),
                    x,
                    y,
                    0xFFFFFF);
            } else {
                fr.drawStringWithShadow(
                    "\u00a77  \u2514\u2500 \u00a7a" + I18n.format("ezminer.hud.minesweeper.ready"),
                    x,
                    y,
                    0xFFFFFF);
            }
        }
    }

    /**
     * Dispatches to the configured brand animation: either the original
     * {@link #drawRainbowBounceHeader} or the new {@link #drawWaveHighlightHeader},
     * depending on {@link Config#hudAnimationStyle}.
     */
    private static void drawAnimatedHeader(FontRenderer fr, int x, int y, String suffix) {
        if (Config.hudAnimationStyle == 1) {
            drawWaveHighlightHeader(fr, x, y, suffix);
        } else {
            drawRainbowBounceHeader(fr, x, y, suffix);
        }
    }

    /**
     * Original rainbow-bounce animation.
     *
     * <p>
     * Each character in {@value #BRAND} is coloured with its own rainbow hue for one
     * {@value #CHAR_PERIOD_MS} ms slot. During that slot the character oscillates upward
     * (sine-wave) by up to {@value #BOUNCE_HEIGHT_PX} px before returning to baseline.
     * All other characters are rendered in the default cyan ({@code §b}) without offset.
     *
     * @param fr     the {@link FontRenderer} to use
     * @param x      left pixel coordinate of the header line
     * @param y      top pixel coordinate of the header line
     * @param suffix the text rendered after {@code ]} (e.g. {@code §a■ 连锁已启用})
     */
    private static void drawRainbowBounceHeader(FontRenderer fr, int x, int y, String suffix) {
        long now = System.currentTimeMillis();
        int numChars = BRAND.length();

        int activeIdx = (int) ((now / CHAR_PERIOD_MS) % numChars);
        double phase = (now % CHAR_PERIOD_MS) / (double) CHAR_PERIOD_MS; // 0.0 → 1.0
        // Sine arch: peaks at phase=0.5, returns to 0 at phase=0 and phase=1.
        int bounceOffset = (int) (BOUNCE_HEIGHT_PX * Math.sin(phase * Math.PI));

        // Render "§b[" prefix
        String openBracket = "\u00a7b[";
        fr.drawStringWithShadow(openBracket, x, y, 0xFFFFFF);
        int curX = x + fr.getStringWidth(openBracket);

        // Render each character of BRAND individually
        for (int i = 0; i < numChars; i++) {
            String ch = String.valueOf(BRAND.charAt(i));
            if (i == activeIdx) {
                String color = RAINBOW_CODES[i % RAINBOW_CODES.length];
                fr.drawStringWithShadow(color + ch, curX, y - bounceOffset, 0xFFFFFF);
            } else {
                fr.drawStringWithShadow("\u00a7b" + ch, curX, y, 0xFFFFFF);
            }
            curX += fr.getStringWidth(ch);
        }

        // Render "§b] " + suffix
        fr.drawStringWithShadow("\u00a7b] " + suffix, curX, y, 0xFFFFFF);
    }

    /**
     * Wave-highlight animation — three phases per cycle:
     *
     * <ol>
     * <li><b>Wave phase</b> ({@value #WAVE_LETTER_MS} ms × n letters): each letter
     * lights up as white italic ({@code §f§o}) for its slot, then returns to cyan
     * ({@code §b}) before the next letter takes its turn — a rolling spotlight.</li>
     * <li><b>Fill phase</b> ({@value #FILL_LETTER_MS} ms × n letters): letters
     * turn white ({@code §f}) left-to-right, each <em>staying</em> white as the
     * next one turns — an accumulating fill until all are white.</li>
     * <li><b>Hold phase</b> ({@value #HOLD_MS} ms): all letters remain white,
     * then the cycle resets to phase 1.</li>
     * </ol>
     *
     * @param fr     the {@link FontRenderer} to use
     * @param x      left pixel coordinate of the header line
     * @param y      top pixel coordinate of the header line
     * @param suffix the text rendered after {@code ]}
     */
    private static void drawWaveHighlightHeader(FontRenderer fr, int x, int y, String suffix) {
        long now = System.currentTimeMillis();
        int n = BRAND.length();
        long wavePhaseDuration = WAVE_LETTER_MS * n;
        long fillPhaseDuration = FILL_LETTER_MS * n;
        long cycleDuration = wavePhaseDuration + fillPhaseDuration + HOLD_MS;
        long t = now % cycleDuration;

        // Render "§b[" prefix
        String openBracket = "\u00a7b[";
        fr.drawStringWithShadow(openBracket, x, y, 0xFFFFFF);
        int curX = x + fr.getStringWidth(openBracket);

        for (int i = 0; i < n; i++) {
            String ch = String.valueOf(BRAND.charAt(i));
            final String code;
            if (t < wavePhaseDuration) {
                // Wave phase: only the current spotlight letter is white italic.
                int spotlight = (int) (t / WAVE_LETTER_MS);
                code = (i == spotlight) ? "\u00a7f\u00a7o" : "\u00a7b";
            } else if (t < wavePhaseDuration + fillPhaseDuration) {
                // Fill phase: letters 0..fillFront turn white and stay white.
                int fillFront = (int) ((t - wavePhaseDuration) / FILL_LETTER_MS);
                code = (i <= fillFront) ? "\u00a7f" : "\u00a7b";
            } else {
                // Hold phase: all letters are white.
                code = "\u00a7f";
            }
            fr.drawStringWithShadow(code + ch, curX, y, 0xFFFFFF);
            curX += fr.getStringWidth(ch);
        }

        // Render "§b] " + suffix
        fr.drawStringWithShadow("\u00a7b] " + suffix, curX, y, 0xFFFFFF);
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
