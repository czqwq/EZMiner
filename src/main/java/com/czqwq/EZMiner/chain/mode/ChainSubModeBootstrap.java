package com.czqwq.EZMiner.chain.mode;

public class ChainSubModeBootstrap {

    public static void bootstrap(ChainSubModeRegistry registry) {
        registry.register(new ChainSubModeDefinition("blast_all", "ezminer.mode.blast.allBlocks"));
        registry.register(new ChainSubModeDefinition("blast_same_type", "ezminer.mode.blast.sameType"));
        registry.register(new ChainSubModeDefinition("blast_tunnel", "ezminer.mode.blast.tunnel"));
        registry.register(new ChainSubModeDefinition("blast_ore", "ezminer.mode.blast.oreOnly"));
        registry.register(new ChainSubModeDefinition("blast_log", "ezminer.mode.blast.logging"));
        registry.register(new ChainSubModeDefinition("blast_crop", "ezminer.mode.blast.crop"));
        registry.register(new ChainSubModeDefinition("chain_basic", "ezminer.mode.chain.basic"));
    }
}
