package com.czqwq.EZMiner.chain.planning;

import java.util.concurrent.LinkedBlockingQueue;

import net.minecraft.entity.player.EntityPlayer;

import org.joml.Vector3i;

import com.czqwq.EZMiner.core.MinerConfig;
import com.czqwq.EZMiner.core.MinerModeState;
import com.czqwq.EZMiner.core.founder.BasePositionFounder;
import com.czqwq.EZMiner.core.founder.BlastPositionFounder;
import com.czqwq.EZMiner.core.founder.ChainPositionFounder;
import com.czqwq.EZMiner.core.founder.CropFounder;
import com.czqwq.EZMiner.core.founder.FuzzyChainPositionFounder;
import com.czqwq.EZMiner.core.founder.GtVeinOreFounder;
import com.czqwq.EZMiner.core.founder.InverseBlastFounder;
import com.czqwq.EZMiner.core.founder.LogFounder;
import com.czqwq.EZMiner.core.founder.NoOpPositionFounder;
import com.czqwq.EZMiner.core.founder.OreFounder;
import com.czqwq.EZMiner.core.founder.ScreenBlastFounder;
import com.czqwq.EZMiner.core.founder.TunnelFounder;

/**
 * Phase-B compatibility bridge:
 * centralize founder assembly in planning layer while old founder executors remain.
 */
public class LegacyFounderPlanningFactory {

    public BasePositionFounder createFounder(MinerModeState modeState, Vector3i center,
        LinkedBlockingQueue<Vector3i> results, EntityPlayer player, MinerConfig config) {
        if (modeState.mainMode == 1) {
            // Chain mode 0 = basic, 1 = fuzzy, 2 = cached, 3 = cached fuzzy.
            // Cached modes use the same BFS algorithm; caching is orchestrated by Manager.
            if (modeState.chainMode == 1 || modeState.chainMode == 3) {
                return new FuzzyChainPositionFounder(center, results, player, config);
            }
            return new ChainPositionFounder(center, results, player, config);
        }
        if (modeState.mainMode == 2) {
            // Special sub-modes
            if (modeState.specialMode == 1) {
                return new CropFounder(center, results, player, config);
            }
            // Block swap mode: use chain founder for client preview
            if (modeState.specialMode == 3) {
                return new ChainPositionFounder(center, results, player, config);
            }
            return new NoOpPositionFounder(center, results, player, config);
        }
        switch (modeState.blastMode) {
            case 1:
                return new ScreenBlastFounder(center, results, player, config);
            case 2:
                return new TunnelFounder(center, results, player, config);
            case 3:
                return new OreFounder(center, results, player, config);
            case 4:
                return new LogFounder(center, results, player, config);
            case 5:
                return new InverseBlastFounder(center, results, player, config);
            case 6:
                return new GtVeinOreFounder(center, results, player, config);
            default:
                return new BlastPositionFounder(center, results, player, config);
        }
    }
}
