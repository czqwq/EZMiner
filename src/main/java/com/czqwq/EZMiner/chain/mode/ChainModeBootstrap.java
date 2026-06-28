package com.czqwq.EZMiner.chain.mode;

public class ChainModeBootstrap {

    public static void bootstrap(ChainModeRegistry modeRegistry) {
        modeRegistry.register(
            new ChainModeDefinition(ChainMode.BLAST, "ezminer.mode.blast")
                .addSubMode(new ChainSubModeDefinition("blast_all", "ezminer.mode.blast.allBlocks"))
                .addSubMode(new ChainSubModeDefinition("blast_same_type", "ezminer.mode.blast.sameType"))
                .addSubMode(new ChainSubModeDefinition("blast_tunnel", "ezminer.mode.blast.tunnel"))
                .addSubMode(new ChainSubModeDefinition("blast_ore", "ezminer.mode.blast.oreOnly"))
                .addSubMode(new ChainSubModeDefinition("blast_log", "ezminer.mode.blast.logging")));
        modeRegistry.register(
            new ChainModeDefinition(ChainMode.CHAIN, "ezminer.mode.chain")
                .addSubMode(new ChainSubModeDefinition("chain_basic", "ezminer.mode.chain.basic"))
                .addSubMode(new ChainSubModeDefinition("chain_fuzzy", "ezminer.mode.chain.fuzzy"))
                .addSubMode(new ChainSubModeDefinition("chain_cached", "ezminer.mode.chain.cached"))
                .addSubMode(new ChainSubModeDefinition("chain_cached_fuzzy", "ezminer.mode.chain.cached_fuzzy")));
        modeRegistry.register(
            new ChainModeDefinition(ChainMode.SPECIAL, "ezminer.mode.special")
                .addSubMode(new ChainSubModeDefinition("special_minesweeper", "ezminer.mode.special.minesweeper"))
                .addSubMode(new ChainSubModeDefinition("special_crop", "ezminer.mode.special.crop")));
    }
}
