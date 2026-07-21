package com.czqwq.EZMiner.client.gui.sync;

import java.util.function.IntSupplier;

import com.czqwq.EZMiner.Config;

/**
 * Maps every numeric client-config option shown on the client tab of the config
 * GUI to its authoritative server-side maximum, so a "sync" action can pull the
 * server cap into the corresponding input field.
 *
 * <p>
 * Each entry knows its content-row index on the client tab (for button
 * placement) and how to resolve the effective server maximum (runtime-synced
 * value with a local fallback while the {@link Integer#MAX_VALUE} sentinel is
 * still set). The value is only written into the GUI text field — persisting it
 * stays with the regular Apply/Save flow. Keeping this mapping here decouples
 * the sync feature from the GUI layout code.
 */
public enum ClientCapSyncTarget {

    BIG_RADIUS(0, () -> effectiveServerMax(Config.runtimeServerMaxBigRadius, Config.bigRadius)),
    BLOCK_LIMIT(1, () -> effectiveServerMax(Config.runtimeServerMaxBlockLimit, Config.blockLimit)),
    SMALL_RADIUS(2, () -> effectiveServerMax(Config.runtimeServerMaxSmallRadius, Config.smallRadius)),
    TUNNEL_WIDTH(3, () -> effectiveServerMax(Config.runtimeServerMaxTunnelWidth, Config.tunnelWidth)),
    PREVIEW_BIG_RADIUS(4,
        () -> effectiveServerMax(Config.runtimeServerMaxPreviewBigRadius, Config.serverMaxPreviewBigRadius)),
    PREVIEW_BLOCK_LIMIT(5,
        () -> effectiveServerMax(Config.runtimeServerMaxPreviewBlockLimit, Config.serverMaxPreviewBlockLimit)),
    BLOCK_SWAP_RADIUS(18, () -> effectiveServerMax(Config.runtimeServerMaxBlockSwapRadius, Config.blockSwapRadius)),
    BLOCK_SWAP_LIMIT(19, () -> effectiveServerMax(Config.runtimeServerMaxBlockSwapLimit, Config.blockSwapLimit));

    private final int row;
    private final IntSupplier serverMax;

    ClientCapSyncTarget(int row, IntSupplier serverMax) {
        this.row = row;
        this.serverMax = serverMax;
    }

    /** Content-row index of this option on the client tab of the config GUI. */
    public int row() {
        return row;
    }

    /** Effective server-side maximum for this option. */
    public int serverMax() {
        return serverMax.getAsInt();
    }

    /**
     * Resolves the runtime-synced server cap, falling back to the client-local
     * server config value while the runtime field is still at its
     * {@link Integer#MAX_VALUE} sentinel (before the first sync, or in
     * single-player where both match anyway).
     */
    private static int effectiveServerMax(int runtimeValue, int localFallback) {
        return runtimeValue == Integer.MAX_VALUE ? localFallback : runtimeValue;
    }
}
