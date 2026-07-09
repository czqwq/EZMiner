package com.czqwq.EZMiner.utils;

import net.minecraft.client.resources.I18n;
import net.minecraft.util.StatCollector;

/**
 * Decoupled time-formatting utility.
 * <p>
 * Formats a millisecond duration into a localized human-readable string.
 * Units only appear when their value is non-zero, except seconds and
 * milliseconds which always show. A total-seconds summary is appended in
 * parentheses. Spacing between units is locale-dependent: Chinese locales
 * have no spaces, others have spaces.
 *
 * <pre>
 * English:
 *      0 ms  → 0s 0ms(0.0s)
 *  10 000 ms → 10s 0ms(10.0s)
 *  70 500 ms → 1min 10s 500ms(70.5s)
 *
 * Chinese:
 *  10 000 ms → 10秒0毫秒(10.0秒)
 *  70 500 ms → 1分10秒500毫秒(70.5秒)
 * </pre>
 */
public final class TimeFormatUtils {

    private TimeFormatUtils() {}

    /**
     * Formats a duration for client-side display (uses {@link I18n}).
     *
     * @param ms duration in milliseconds
     * @return formatted time string
     */
    public static String formatElapsed(long ms) {
        return formatElapsedImpl(ms, true);
    }

    /**
     * Formats a duration for server-side use (uses {@link StatCollector}).
     * Safe to call from server-side code via {@link net.minecraft.util.ChatComponentTranslation}.
     *
     * @param ms duration in milliseconds
     * @return formatted time string
     */
    public static String formatElapsedServer(long ms) {
        return formatElapsedImpl(ms, false);
    }

    private static String formatElapsedImpl(long ms, boolean client) {
        if (ms < 0) ms = 0;

        long totalSeconds = ms / 1000;
        long millis = ms % 1000;
        double totalSecs = ms / 1000.0;

        String secLabel = translate("ezminer.hud.time.second", client);
        String msLabel = translate("ezminer.hud.time.millisecond", client);

        if (totalSeconds == 0 && millis == 0) {
            return "0" + secLabel
                + sp(client)
                + "0"
                + msLabel
                + "("
                + String.format("%.1f", totalSecs)
                + secLabel
                + ")";
        }

        long days = totalSeconds / 86400;
        long hours = (totalSeconds % 86400) / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;

        String dayLabel = translate("ezminer.hud.time.day", client);
        String hourLabel = translate("ezminer.hud.time.hour", client);
        String minLabel = translate("ezminer.hud.time.minute", client);
        String space = sp(client);

        StringBuilder sb = new StringBuilder();
        if (days > 0) {
            sb.append(days)
                .append(dayLabel)
                .append(space);
        }
        if (hours > 0) {
            sb.append(hours)
                .append(hourLabel)
                .append(space);
        }
        if (minutes > 0) {
            sb.append(minutes)
                .append(minLabel)
                .append(space);
        }
        sb.append(seconds)
            .append(secLabel)
            .append(space);
        sb.append(millis)
            .append(msLabel);
        sb.append("(")
            .append(String.format("%.1f", totalSecs))
            .append(secLabel)
            .append(")");
        return sb.toString();
    }

    private static String translate(String key, boolean client) {
        return client ? I18n.format(key) : StatCollector.translateToLocal(key);
    }

    /** Detected once per classload. */
    private static Boolean chineseLocale = null;

    private static boolean isChineseLocale(boolean client) {
        if (chineseLocale == null) {
            chineseLocale = translate("ezminer.hud.time.second", client).equals("秒"); // 秒
        }
        return chineseLocale;
    }

    /** Space separator — empty for Chinese, " " for other locales. */
    private static String sp(boolean client) {
        return isChineseLocale(client) ? "" : " ";
    }
}
