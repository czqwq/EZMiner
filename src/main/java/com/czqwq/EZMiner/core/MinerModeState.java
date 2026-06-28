package com.czqwq.EZMiner.core;

import com.czqwq.EZMiner.Config;

/**
 * Tracks the current mining mode for a player (both client and server side).
 *
 * Main modes:
 * 0 = Blast mode (爆破模式)
 * 1 = Chain mode (连锁模式)
 * 2 = Special mode (特殊连锁模式)
 *
 * Blast sub-modes (blastMode):
 * 0 = All-blocks blast (无差别爆破)
 * 1 = Same-type filter (筛选爆破)
 * 2 = Tunnel blast (隧道爆破)
 * 3 = Ore-only blast (矿石爆破)
 * 4 = Logging blast (爆破伐木)
 * 5 = Inverse chain (反向连锁)
 * 6 = GT vein ore precise (矿脉连锁精准)
 *
 * Chain sub-modes (chainMode):
 * 0 = Basic chain (基础连锁)
 * 1 = Fuzzy chain (模糊模式 – same block type, any metadata)
 *
 * Special sub-modes (specialMode):
 * 0 = Minesweeper (扫雷模式)
 * 1 = Crop harvest (一键收作物)
 * 2 = Sudoku assistant (数独助手)
 */
public class MinerModeState {

    public static final String[] MAIN_MODES = { "ezminer.mode.blast", // 0 爆破模式
        "ezminer.mode.chain", // 1 连锁模式
        "ezminer.mode.special", // 2 特殊模式
    };

    public static final String[] BLAST_MODES = { "ezminer.mode.blast.allBlocks", // 0
        "ezminer.mode.blast.sameType", // 1
        "ezminer.mode.blast.tunnel", // 2
        "ezminer.mode.blast.oreOnly", // 3
        "ezminer.mode.blast.logging", // 4
        "ezminer.mode.blast.inverse", // 5
        "ezminer.mode.blast.gtVeinOre", // 6
    };

    public static final String[] CHAIN_MODES = { "ezminer.mode.chain.basic", // 0
        "ezminer.mode.chain.fuzzy", // 1
        "ezminer.mode.chain.cached", // 2
        "ezminer.mode.chain.cached_fuzzy", // 3
    };
    public static final String[] SPECIAL_MODES = { "ezminer.mode.special.minesweeper", // 0
        "ezminer.mode.special.crop", // 1
        "ezminer.mode.special.sudoku", // 2
    };

    public int mainMode = 1; // default: chain mode
    public int blastMode = 0;
    public int chainMode = 0;
    public int specialMode = 0;

    // ===== Main mode =====
    public String nextMainMode() {
        mainMode = (mainMode + 1) % MAIN_MODES.length;
        return currentMainMode();
    }

    public String previousMainMode() {
        mainMode = (mainMode - 1 + MAIN_MODES.length) % MAIN_MODES.length;
        return currentMainMode();
    }

    public String currentMainMode() {
        return MAIN_MODES[mainMode];
    }

    // ===== Sub-mode (dispatched by main mode) =====
    public String nextSubMode() {
        if (mainMode == 0) return nextBlastMode();
        if (mainMode == 1) return nextChainMode();
        return nextSpecialMode();
    }

    public String previousSubMode() {
        if (mainMode == 0) return previousBlastMode();
        if (mainMode == 1) return previousChainMode();
        return previousSpecialMode();
    }

    public String currentSubMode() {
        if (mainMode == 0) return currentBlastMode();
        if (mainMode == 1) return currentChainMode();
        return currentSpecialMode();
    }

    public int currentSubModeIndex() {
        if (mainMode == 0) return blastMode;
        if (mainMode == 1) return chainMode;
        return specialMode;
    }

    // ===== Blast sub-mode =====
    public String nextBlastMode() {
        blastMode = (blastMode + 1) % BLAST_MODES.length;
        return currentBlastMode();
    }

    public String previousBlastMode() {
        blastMode = (blastMode - 1 + BLAST_MODES.length) % BLAST_MODES.length;
        return currentBlastMode();
    }

    public String currentBlastMode() {
        return BLAST_MODES[blastMode];
    }

    // ===== Chain sub-mode =====
    private int visibleChainModeCount() {
        return Config.enableCachedChain ? CHAIN_MODES.length : 2; // basic(0), fuzzy(1)
    }

    public String nextChainMode() {
        int cnt = visibleChainModeCount();
        chainMode = (chainMode + 1) % cnt;
        if (!Config.enableCachedChain && chainMode >= 2) chainMode = 0;
        return currentChainMode();
    }

    public String previousChainMode() {
        int cnt = visibleChainModeCount();
        chainMode = (chainMode - 1 + cnt) % cnt;
        if (!Config.enableCachedChain && chainMode >= 2) chainMode = 0;
        return currentChainMode();
    }

    public String currentChainMode() {
        if (!Config.enableCachedChain && chainMode >= 2) chainMode = 0;
        return CHAIN_MODES[chainMode];
    }

    /**
     * Returns true when the player is in a cached chain sub-mode (server-side
     * pre-calculation with cached preview). Used by HUD, renderer, and mining
     * manager — all callers reference this single method rather than duplicating
     * the index check.
     *
     * <p>
     * Always returns {@code false} when {@link Config#enableCachedChain} is
     * {@code false}.
     */
    public boolean isCachedChainMode() {
        return Config.enableCachedChain && mainMode == 1 && (chainMode == 2 || chainMode == 3);
    }

    // ===== Special sub-mode =====
    public String nextSpecialMode() {
        specialMode = (specialMode + 1) % SPECIAL_MODES.length;
        return currentSpecialMode();
    }

    public String previousSpecialMode() {
        specialMode = (specialMode - 1 + SPECIAL_MODES.length) % SPECIAL_MODES.length;
        return currentSpecialMode();
    }

    public String currentSpecialMode() {
        return SPECIAL_MODES[specialMode];
    }
}
