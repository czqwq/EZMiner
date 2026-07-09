package com.czqwq.EZMiner.mixin;

import gregtech.api.enums.Mods;

@SuppressWarnings({ "unused", "SpellCheckingInspection" })
public enum TargetMod {

    HodgePodge("HodgePodge", Mods.HodgePodge.ID);

    private final String modId;
    public final String modName;

    TargetMod(String modName, String modId) {
        this.modName = modName;
        this.modId = modId;
    }

    public String getModId() {
        return modId;
    }

    public String getModName() {
        return modName;
    }
}
