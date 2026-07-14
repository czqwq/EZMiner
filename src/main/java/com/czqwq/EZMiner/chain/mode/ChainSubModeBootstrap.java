package com.czqwq.EZMiner.chain.mode;

public class ChainSubModeBootstrap {

    public static void bootstrap(ChainSubModeRegistry registry) {
        registry.register(new ChainSubModeDefinition("blast_all", "ezminer.mode.blast.allBlocks"));
        registry.register(new ChainSubModeDefinition("blast_same_type", "ezminer.mode.blast.sameType"));
        registry.register(new ChainSubModeDefinition("blast_tunnel", "ezminer.mode.blast.tunnel"));
        registry.register(new ChainSubModeDefinition("blast_ore", "ezminer.mode.blast.oreOnly"));
        registry.register(new ChainSubModeDefinition("blast_log", "ezminer.mode.blast.logging"));
        registry.register(new ChainSubModeDefinition("chain_basic", "ezminer.mode.chain.basic"));
        registry.register(new ChainSubModeDefinition("special_minesweeper", "ezminer.mode.special.minesweeper"));
        registry.register(new ChainSubModeDefinition("special_crop", "ezminer.mode.special.crop"));
        registry.register(new ChainSubModeDefinition("special_sudoku", "ezminer.mode.special.sudoku"));
        registry.register(new ChainSubModeDefinition("special_block_swap", "ezminer.mode.special.blockSwap"));
    }
}
