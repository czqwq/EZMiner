package com.czqwq.EZMiner.core;

import java.util.concurrent.LinkedBlockingQueue;

import net.minecraft.entity.player.EntityPlayer;

import org.joml.Vector3i;

import com.czqwq.EZMiner.core.founder.BasePositionFounder;
import com.czqwq.EZMiner.core.founder.BlastPositionFounder;
import com.czqwq.EZMiner.core.founder.ChainPositionFounder;
import com.czqwq.EZMiner.core.founder.LogFounder;
import com.czqwq.EZMiner.core.founder.OreFounder;
import com.czqwq.EZMiner.core.founder.ScreenBlastFounder;
import com.czqwq.EZMiner.core.founder.TunnelFounder;

/**
 * Tracks the current mining mode for a player (both client and server side).
 *
 * Main modes:
 * 0 = Blast mode (爆破模式)
 * 1 = Chain mode (连锁模式)
 *
 * Blast sub-modes (blastMode):
 * 0 = All-blocks blast (无差别爆破)
 * 1 = Same-type filter (筛选爆破)
 * 2 = Tunnel blast (隧道爆破)
 * 3 = Ore-only blast (矿石爆破)
 * 4 = Logging blast (爆破伐木)
 *
 * Chain sub-modes (chainMode):
 * 0 = Basic chain (基础连锁)
 */
public class MinerModeState {

    public static final String[] MAIN_MODES = { "ezminer.mode.blast", // 0 爆破模式
        "ezminer.mode.chain", // 1 连锁模式
    };

    public static final String[] BLAST_MODES = { "ezminer.mode.blast.allBlocks", // 0
        "ezminer.mode.blast.sameType", // 1
        "ezminer.mode.blast.tunnel", // 2
        "ezminer.mode.blast.oreOnly", // 3
        "ezminer.mode.blast.logging", // 4
    };

    public static final String[] CHAIN_MODES = { "ezminer.mode.chain.basic", // 0
    };

    public int mainMode = 1; // default: chain mode
    public int blastMode = 0;
    public int chainMode = 0;

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
        return nextChainMode();
    }

    public String previousSubMode() {
        if (mainMode == 0) return previousBlastMode();
        return previousChainMode();
    }

    public String currentSubMode() {
        if (mainMode == 0) return currentBlastMode();
        return currentChainMode();
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
    public String nextChainMode() {
        chainMode = (chainMode + 1) % CHAIN_MODES.length;
        return currentChainMode();
    }

    public String previousChainMode() {
        chainMode = (chainMode - 1 + CHAIN_MODES.length) % CHAIN_MODES.length;
        return currentChainMode();
    }

    public String currentChainMode() {
        return CHAIN_MODES[chainMode];
    }

    // ===== Factory =====
    public BasePositionFounder createPositionFounder(Vector3i center, LinkedBlockingQueue<Vector3i> results,
        EntityPlayer player, MinerConfig config) {
        switch (mainMode) {
            case 1: // chain mode
                switch (chainMode) {
                    default: // 0 basic chain
                        return new ChainPositionFounder(center, results, player, config);
                }
            default: // 0 blast mode
                switch (blastMode) {
                    case 1:
                        return new ScreenBlastFounder(center, results, player, config);
                    case 2:
                        return new TunnelFounder(center, results, player, config);
                    case 3:
                        return new OreFounder(center, results, player, config);
                    case 4:
                        return new LogFounder(center, results, player, config);
                    default: // 0 all-blocks
                        return new BlastPositionFounder(center, results, player, config);
                }
        }
    }
}
