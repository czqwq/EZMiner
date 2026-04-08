package com.czqwq.EZMiner.chain.mode;

/**
 * Sub-mode definition with stable id and i18n key.
 */
public class ChainSubModeDefinition {

    public final String id;
    public final String translationKey;

    public ChainSubModeDefinition(String id, String translationKey) {
        this.id = id;
        this.translationKey = translationKey;
    }
}
