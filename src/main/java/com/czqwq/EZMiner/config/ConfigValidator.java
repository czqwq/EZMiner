package com.czqwq.EZMiner.config;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.czqwq.EZMiner.Config;

/**
 * Lightweight semantic validator for EZMiner configuration values.
 *
 * <p>
 * Runs after {@link Config#load()} completes and logs warnings for
 * out-of-range or inconsistent settings. Never throws — validation
 * failures are non-fatal. Gated behind {@link Config#enableConfigValidation}.
 * </p>
 *
 * <p>
 * Lightweight semantic config validator.
 * </p>
 */
public final class ConfigValidator {

    private static final Logger LOG = LogManager.getLogger("EZMiner/ConfigValidator");

    private ConfigValidator() {}

    /** Run all validation checks. Logs warnings for issues found. */
    public static void validate() {
        if (!Config.enableConfigValidation) return;
        validateRange();
        validateCrossField();
    }

    // ── Range checks ────────────────────────────────────────────────────────────

    private static void validateRange() {
        // Per-tick break rates — warn if suspiciously high
        if (Config.breakPerTick > 256) {
            LOG.warn(
                "breakPerTick={} is very high — may cause TPS drops on large veins. " + "Recommended: 16–64.",
                Config.breakPerTick);
        }
        if (Config.cachedBreakPerTick > 512) {
            LOG.warn(
                "cachedBreakPerTick={} is very high — may cause TPS drops on large cached chains. "
                    + "Recommended: 64–256.",
                Config.cachedBreakPerTick);
        }

        // Radius and limit — warn about extreme values
        if (Config.bigRadius > 64) {
            LOG.warn("bigRadius={} is very large — searches may consume significant CPU and memory.", Config.bigRadius);
        }
        if (Config.blockLimit > 16384) {
            LOG.warn(
                "blockLimit={} is very large — chain operations may cause significant server lag.",
                Config.blockLimit);
        }
        if (Config.smallRadius > 8) {
            LOG.warn(
                "smallRadius={} is large — chain mode will connect blocks across long distances, "
                    + "possibly linking unrelated veins.",
                Config.smallRadius);
        }

        // Search worker threads
        if (Config.searchWorkerThreads > 8) {
            LOG.warn("searchWorkerThreads={} exceeds maximum (8). Clamping to 8.", Config.searchWorkerThreads);
        }

        // Search budget per yield
        if (Config.searchBudgetPerYield > 4096) {
            LOG.warn(
                "searchBudgetPerYield={} is very high — search threads may not yield to tick boundaries promptly.",
                Config.searchBudgetPerYield);
        }

        // Fortune
        if (Config.enableUnlimitedOreFortune && Config.maxFortuneLevel > 100) {
            LOG.warn(
                "maxFortuneLevel={} with unlimited ore fortune enabled — extremely high levels may cause "
                    + "exponential drop multiplication.",
                Config.maxFortuneLevel);
        }
    }

    // ── Cross-field consistency ─────────────────────────────────────────────────

    private static void validateCrossField() {
        // cachedBreakPerTick should be >= breakPerTick (cached has no search overhead)
        if (Config.breakPerTick > Config.cachedBreakPerTick) {
            LOG.warn(
                "breakPerTick ({}) > cachedBreakPerTick ({}) — cached chain will be slower than regular chain. "
                    + "Consider increasing cachedBreakPerTick or lowering breakPerTick.",
                Config.breakPerTick,
                Config.cachedBreakPerTick);
        }

        // Crazy mode + high breakPerTick = potential freeze
        if (Config.crazyMode && Config.breakPerTick > 128) {
            LOG.warn(
                "crazyMode=true combined with breakPerTick={} — crazy mode already removes the per-tick "
                    + "limit; a high breakPerTick here is redundant.",
                Config.breakPerTick);
        }

        // Preview radius should not exceed mining radius
        if (Config.serverMaxPreviewBigRadius > Config.bigRadius) {
            LOG.warn(
                "serverMaxPreviewBigRadius ({}) > bigRadius ({}) — preview will show blocks the server "
                    + "won't actually mine.",
                Config.serverMaxPreviewBigRadius,
                Config.bigRadius);
        }

        // Block swap radius > bigRadius is suspicious
        if (Config.enableBlockSwapMode && Config.blockSwapRadius > Config.bigRadius) {
            LOG.warn(
                "blockSwapRadius ({}) > bigRadius ({}) — block swap can reach farther than blast mode.",
                Config.blockSwapRadius,
                Config.bigRadius);
        }

        // Chunk loading + extreme radius = dangerous
        if (Config.enableChainChunkLoading && Config.bigRadius > 32) {
            LOG.warn(
                "enableChainChunkLoading=true with bigRadius={} — may load many chunks and cause "
                    + "significant server lag.",
                Config.bigRadius);
        }

        // Drop immediately + drop to player at origin = contradictory message
        if (Config.dropImmediately && Config.dropToPlayer) {
            LOG.info(
                "dropImmediately=true makes dropToPlayer irrelevant — drops spawn per-block, "
                    + "not batched at chain end.");
        }

        // Cached chain enabled + useChunkCachedHarvest = both experimental
        if (Config.enableCachedChain && Config.useChunkCachedHarvest) {
            LOG.warn(
                "Both enableCachedChain and useChunkCachedHarvest are enabled. "
                    + "These are experimental features — consider enabling only one at a time.");
        }

        // Watchdog interval vs idle timeout (when watchdog is enabled)
        if (Config.enableChainWatchdog) {
            double watchdogSeconds = Config.chainWatchdogTimeoutTicks / 20.0;
            if (Config.chainIdleTimeoutSeconds > 0 && watchdogSeconds > Config.chainIdleTimeoutSeconds) {
                LOG.warn(
                    "chainWatchdogTimeoutTicks ({}) translates to {}s, which exceeds chainIdleTimeoutSeconds ({}s). "
                        + "The watchdog will fire after the wall-clock idle timeout, making it redundant.",
                    Config.chainWatchdogTimeoutTicks,
                    watchdogSeconds,
                    Config.chainIdleTimeoutSeconds);
            }
        }
    }
}
