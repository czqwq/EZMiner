package com.czqwq.EZMiner.chain.mode;

public class ChainModeBootstrap {

    public static void bootstrap(ChainModeRegistry modeRegistry) {
        modeRegistry
            .register(
                new ChainModeDefinition(ChainMode.BLAST, "ezminer.mode.blast")
                    .addSubMode(new ChainSubModeDefinition("blast_all", "ezminer.mode.blast.allBlocks"))
                    .addSubMode(new ChainSubModeDefinition("blast_same_type", "ezminer.mode.blast.sameType"))
                    .addSubMode(new ChainSubModeDefinition("blast_tunnel", "ezminer.mode.blast.tunnel"))
                    .addSubMode(new ChainSubModeDefinition("blast_ore", "ezminer.mode.blast.oreOnly"))
                    .addSubMode(new ChainSubModeDefinition("blast_log", "ezminer.mode.blast.logging"))
                    .addSubMode(new ChainSubModeDefinition("blast_crop", "ezminer.mode.blast.crop")));
        modeRegistry
            .register(
                new ChainModeDefinition(ChainMode.CHAIN, "ezminer.mode.chain")
                    .addSubMode(new ChainSubModeDefinition("chain_basic", "ezminer.mode.chain.basic")));
    }
}
