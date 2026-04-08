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
import com.czqwq.EZMiner.core.founder.LogFounder;
import com.czqwq.EZMiner.core.founder.OreFounder;
import com.czqwq.EZMiner.core.founder.ScreenBlastFounder;
import com.czqwq.EZMiner.core.founder.TunnelFounder;

/**
 * Phase-B compatibility bridge:
 * centralize founder assembly in planning layer while old founder executors remain.
 */
public class LegacyFounderPlanningFactory {

    public BasePositionFounder create(MinerModeState modeState, Vector3i center, LinkedBlockingQueue<Vector3i> results,
        EntityPlayer player, MinerConfig config) {
        if (modeState.mainMode == 1) {
            return new ChainPositionFounder(center, results, player, config);
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
                return new CropFounder(center, results, player, config);
            default:
                return new BlastPositionFounder(center, results, player, config);
        }
    }
}
