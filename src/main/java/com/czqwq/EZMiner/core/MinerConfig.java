package com.czqwq.EZMiner.core;

import com.czqwq.EZMiner.Config;

import cpw.mods.fml.common.FMLCommonHandler;

/** Per-player miner configuration, validated and capped against server limits. */
public class MinerConfig {

    public int bigRadius = Config.clientBigRadius;
    public int blockLimit = Config.clientBlockLimit;
    public int smallRadius = Config.clientSmallRadius;
    public int tunnelWidth = Config.clientTunnelWidth;
    public boolean useChainDoneMessage = Config.useChainDoneMessage;
    /**
     * Exhaustion applied per chain block, replacing vanilla mining exhaustion.
     * This is a client-side preference sent to the server via {@code PacketMinerConfig}.
     * 0.0 = no food cost; negative = restore food; default mirrors vanilla (0.025).
     */
    public double addExhaustion = Config.addExhaustion;

    public MinerConfig() {
        if (!FMLCommonHandler.instance()
            .getEffectiveSide()
            .isClient()) {
            this.bigRadius = Config.bigRadius;
            this.blockLimit = Config.blockLimit;
            this.smallRadius = Config.smallRadius;
            this.tunnelWidth = Config.tunnelWidth;
        }
        this.addExhaustion = Config.addExhaustion;
    }

    public MinerConfig(int bigRadius, int blockLimit, int smallRadius, int tunnelWidth, boolean useChainDoneMessage,
        double addExhaustion) {
        this.bigRadius = bigRadius;
        this.blockLimit = blockLimit;
        this.smallRadius = smallRadius;
        this.tunnelWidth = tunnelWidth;
        this.useChainDoneMessage = useChainDoneMessage;
        this.addExhaustion = addExhaustion;
    }

    public MinerConfig updateFrom(MinerConfig other) {
        if (other == null) return this;
        this.bigRadius = other.bigRadius;
        this.blockLimit = other.blockLimit;
        this.smallRadius = other.smallRadius;
        this.tunnelWidth = other.tunnelWidth;
        this.useChainDoneMessage = other.useChainDoneMessage;
        this.addExhaustion = other.addExhaustion;
        return this;
    }
}
