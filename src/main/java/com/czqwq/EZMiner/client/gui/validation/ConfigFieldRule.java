package com.czqwq.EZMiner.client.gui.validation;

import java.util.function.IntSupplier;

import net.minecraft.client.resources.I18n;

import com.czqwq.EZMiner.utils.ItemFilterExpression;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Validation rule for one text input field of {@code EZMinerConfigGui}.
 *
 * <p>
 * The bounds intentionally mirror what the Apply path really enforces — the
 * {@code Config} load clamps ({@code clampLogBigRadius}, {@code clampBlockSwapRadius},
 * {@code clampAddExhaustion}, …) and the ranges {@code PacketSaveServerConfig} clamps
 * server fields to — so {@link FieldValidationStatus#VALID} means "this text will be
 * applied exactly as typed". Client-tab options additionally carry the server cap they
 * are silently clamped to ({@link #cappedInteger}); that is the only case in which
 * {@link FieldValidationStatus#EXCEEDS_SERVER_CAP} is reported.
 *
 * <p>
 * The class is display-only: it never writes config values and never logs. The usage
 * help / range hint lines are localised here so callers only have to render the string
 * returned by {@link #tooltip(FieldValidationStatus)}.
 */
@SideOnly(Side.CLIENT)
public final class ConfigFieldRule {

    /** How the field's text has to be interpreted. */
    private enum Kind {
        /** Whole number, parsed exactly like the Apply path ({@code Integer.parseInt}). */
        INTEGER,
        /** Decimal number ({@code Double.parseDouble}); {@code NaN}/±∞ are rejected. */
        DECIMAL,
        /** Item filter expression, syntax-checked by {@link ItemFilterExpression#isValid(String)}. */
        ITEM_EXPRESSION,
        /** Comma-separated item registry names, e.g. {@code minecraft:diamond_pickaxe}. */
        REGISTRY_LIST
    }

    /** Marks the "no upper bound" side of a range; {@link Integer#MAX_VALUE} = Config's own limit. */
    public static final int NO_LIMIT = Integer.MAX_VALUE;

    private static final String COLOR_VALID = "§a";
    private static final String COLOR_INVALID = "§c";
    private static final String COLOR_HINT = "§7";

    private final Kind kind;
    private final double min;
    private final double max;
    /** Server-side cap; {@code null} for fields that have none (everything on the server tab). */
    private final IntSupplier serverCap;

    private ConfigFieldRule(Kind kind, double min, double max, IntSupplier serverCap) {
        this.kind = kind;
        this.min = min;
        this.max = max;
        this.serverCap = serverCap;
    }

    // ── Factories ─────────────────────────────────────────────────────────────

    /** Whole number in {@code [min, max]} with no server cap (server-tab option). */
    public static ConfigFieldRule integer(int min, int max) {
        return new ConfigFieldRule(Kind.INTEGER, min, unbounded(max), null);
    }

    /** Whole number in {@code [min, max]} that is additionally capped by the server. */
    public static ConfigFieldRule cappedInteger(int min, int max, IntSupplier serverCap) {
        return new ConfigFieldRule(Kind.INTEGER, min, unbounded(max), serverCap);
    }

    /** Decimal number in {@code [min, max]}. */
    public static ConfigFieldRule decimal(double min, double max) {
        return new ConfigFieldRule(Kind.DECIMAL, min, max, null);
    }

    /** Item blacklist expression (blank = filtering disabled = valid). */
    public static ConfigFieldRule itemExpression() {
        return new ConfigFieldRule(Kind.ITEM_EXPRESSION, 0, 0, null);
    }

    /** Comma-separated item registry names (blank = no preference = valid). */
    public static ConfigFieldRule registryList() {
        return new ConfigFieldRule(Kind.REGISTRY_LIST, 0, 0, null);
    }

    /**
     * {@link #NO_LIMIT} is Config's own "unbounded" sentinel; translate it to an actual
     * infinity so the range hint says {@code ≥ 0} instead of {@code 0 ~ 2147483647}.
     */
    private static double unbounded(int max) {
        return max == NO_LIMIT ? Double.POSITIVE_INFINITY : max;
    }

    // ── Validation ────────────────────────────────────────────────────────────

    /**
     * Cheap status check. Called for every visible field on every frame to decide
     * whether to draw the red outline, so it allocates nothing beyond the number parse.
     *
     * @param rawText current text of the input field, may be {@code null}
     * @return the validation state of {@code rawText}
     */
    public FieldValidationStatus status(String rawText) {
        String text = normalize(rawText);
        switch (kind) {
            case ITEM_EXPRESSION:
                return ItemFilterExpression.isValid(text) ? FieldValidationStatus.VALID : FieldValidationStatus.INVALID;
            case REGISTRY_LIST:
                return isValidRegistryList(text) ? FieldValidationStatus.VALID : FieldValidationStatus.INVALID;
            default:
                Double parsed = parseNumber(text);
                if (parsed == null) return FieldValidationStatus.INVALID;
                double value = parsed;
                if (value < min || value > max) return FieldValidationStatus.INVALID;
                if (serverCap != null && value > serverCap.getAsInt()) return FieldValidationStatus.EXCEEDS_SERVER_CAP;
                return FieldValidationStatus.VALID;
        }
    }

    /**
     * Builds the localised hover tooltip: the verdict line
     * ({@code ✓ 输入内容有效} / {@code × 输入内容无效} / {@code × 超出服务端配置})
     * followed, when it helps, by a grey hint line (allowed range, server cap, or the
     * field's usage help).
     *
     * <p>
     * Colours are baked in as {@code §}-codes because the tooltip renderer draws one
     * base colour per line and the verdict/mark must be green or red.
     * </p>
     *
     * @param status result of {@link #status(String)} for the field's current text
     * @return the tooltip text, {@code \n}-separated
     */
    public String tooltip(FieldValidationStatus status) {
        StringBuilder sb = new StringBuilder(96);
        sb.append(status.isValid() ? COLOR_VALID : COLOR_INVALID)
            .append(I18n.format(markKey(status)))
            .append(' ')
            .append(I18n.format(statusKey(status)));
        String hint = hint(status);
        if (!hint.isEmpty()) sb.append('\n')
            .append(hint);
        return sb.toString();
    }

    // ── Hint lines ────────────────────────────────────────────────────────────

    /** Grey hint line(s) appended below the verdict, or an empty string when none applies. */
    private String hint(FieldValidationStatus status) {
        switch (kind) {
            case ITEM_EXPRESSION:
                // The syntax help used to be the field's only tooltip; keep it next to the
                // verdict so a valid expression still shows what it can do.
                return I18n.format("ezminer.config.blacklistExpression.tooltip");
            case REGISTRY_LIST:
                return status.isValid() ? "" : COLOR_HINT + I18n.format("ezminer.gui.validation.hint.registryList");
            default:
                return numberHint(status);
        }
    }

    private String numberHint(FieldValidationStatus status) {
        if (status == FieldValidationStatus.EXCEEDS_SERVER_CAP) {
            return COLOR_HINT + I18n.format("ezminer.gui.validation.serverCap", formatNumber(serverCap.getAsInt()));
        }
        if (status == FieldValidationStatus.INVALID) {
            String range = rangeHint();
            return range.isEmpty() ? "" : COLOR_HINT + range;
        }
        return "";
    }

    /** Localised "allowed range" text, empty when the field has no bound at all. */
    private String rangeHint() {
        boolean hasMin = min != Double.NEGATIVE_INFINITY;
        boolean hasMax = max != Double.POSITIVE_INFINITY;
        if (hasMin && hasMax) {
            return I18n.format("ezminer.gui.validation.range", formatNumber(min), formatNumber(max));
        }
        if (hasMin) return I18n.format("ezminer.gui.validation.rangeMin", formatNumber(min));
        if (hasMax) return I18n.format("ezminer.gui.validation.rangeMax", formatNumber(max));
        return "";
    }

    // ── Parsing helpers ───────────────────────────────────────────────────────

    private static String normalize(String rawText) {
        return rawText == null ? "" : rawText.trim();
    }

    /** Parses {@code text} the same way the Apply path does; {@code null} when unusable. */
    private Double parseNumber(String text) {
        if (text.isEmpty()) return null;
        try {
            if (kind == Kind.INTEGER) return (double) Integer.parseInt(text);
            double value = Double.parseDouble(text);
            // Double.parseDouble happily accepts "NaN" / "Infinity", but the apply path
            // rejects them (Config.clampAddExhaustion falls back to the default).
            if (Double.isNaN(value) || Double.isInfinite(value)) return null;
            return value;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * {@code minecraft:diamond_pickaxe,modid:tool_name} — every non-empty entry needs a
     * namespace and a path and must not contain whitespace. Blank means "no preference".
     */
    private static boolean isValidRegistryList(String text) {
        if (text.isEmpty()) return true;
        for (String rawEntry : text.split(",")) {
            String entry = rawEntry.trim();
            if (entry.isEmpty()) continue; // tolerate "a,,b" and a trailing comma
            int colon = entry.indexOf(':');
            if (colon <= 0 || colon == entry.length() - 1) return false;
            for (int i = 0; i < entry.length(); i++) {
                char c = entry.charAt(i);
                if (c == ',' || Character.isWhitespace(c)) return false;
            }
        }
        return true;
    }

    /** Renders a bound without a pointless {@code .0} suffix. */
    private static String formatNumber(double value) {
        if (value == Math.rint(value) && Math.abs(value) < 1.0e15) return String.valueOf((long) value);
        return String.valueOf(value);
    }

    private static String markKey(FieldValidationStatus status) {
        return status.isValid() ? "ezminer.gui.validation.mark.valid" : "ezminer.gui.validation.mark.invalid";
    }

    private static String statusKey(FieldValidationStatus status) {
        switch (status) {
            case VALID:
                return "ezminer.gui.validation.valid";
            case EXCEEDS_SERVER_CAP:
                return "ezminer.gui.validation.exceedsServerCap";
            default:
                return "ezminer.gui.validation.invalid";
        }
    }
}
