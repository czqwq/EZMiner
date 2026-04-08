package com.czqwq.EZMiner.chain.mode;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Mode definition: marker + configured sub-mode entries.
 */
public class ChainModeDefinition {

    public final ChainMode mode;
    public final String translationKey;
    private final List<ChainSubModeDefinition> subModes = new ArrayList<>();

    public ChainModeDefinition(ChainMode mode, String translationKey) {
        this.mode = mode;
        this.translationKey = translationKey;
    }

    public ChainModeDefinition addSubMode(ChainSubModeDefinition subMode) {
        subModes.add(subMode);
        return this;
    }

    public List<ChainSubModeDefinition> getSubModes() {
        return Collections.unmodifiableList(subModes);
    }
}
