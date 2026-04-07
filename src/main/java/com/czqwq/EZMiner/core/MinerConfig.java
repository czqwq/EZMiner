package com.czqwq.EZMiner.core;

import com.czqwq.EZMiner.Config;

/** Per-player miner configuration, validated and capped against server limits. */
public class MinerConfig {

    public int bigRadius = Config.bigRadius;
    public int blockLimit = Config.blockLimit;
    public int smallRadius = Config.smallRadius;
    public int tunnelWidth = Config.tunnelWidth;
    public boolean useChainDoneMessage = Config.useChainDoneMessage;
    /**
     * Exhaustion applied per chain block, replacing vanilla mining exhaustion.
     * This is a client-side preference sent to the server via {@code PacketMinerConfig}.
     * 0.0 = no food cost; negative = restore food; default mirrors vanilla (0.025).
     */
    public double addExhaustion = Config.addExhaustion;

    public MinerConfig() {}

    public MinerConfig(int bigRadius, int blockLimit, int smallRadius, int tunnelWidth, boolean useChainDoneMessage,
        double addExhaustion) {
        this.bigRadius = bigRadius;
        this.blockLimit = blockLimit;
        this.smallRadius = smallRadius;
        this.tunnelWidth = tunnelWidth;
        this.useChainDoneMessage = useChainDoneMessage;
        this.addExhaustion = addExhaustion;
    }
}
