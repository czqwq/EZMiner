package com.czqwq.EZMiner.chain.mode;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

public class ChainModeRegistry {

    private final Map<ChainMode, ChainModeDefinition> definitions = new LinkedHashMap<>();

    public void register(ChainModeDefinition definition) {
        definitions.put(definition.mode, definition);
    }

    public ChainModeDefinition get(ChainMode mode) {
        return definitions.get(mode);
    }

    public Collection<ChainModeDefinition> all() {
        return definitions.values();
    }
}
