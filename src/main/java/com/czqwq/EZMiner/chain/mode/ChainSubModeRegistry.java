package com.czqwq.EZMiner.chain.mode;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

public class ChainSubModeRegistry {

    private final Map<String, ChainSubModeDefinition> definitions = new LinkedHashMap<>();

    public void register(ChainSubModeDefinition definition) {
        definitions.put(definition.id, definition);
    }

    public ChainSubModeDefinition get(String id) {
        return definitions.get(id);
    }

    public Collection<ChainSubModeDefinition> all() {
        return definitions.values();
    }
}
