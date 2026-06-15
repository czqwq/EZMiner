package com.czqwq.EZMiner.utils;

import com.czqwq.EZMiner.Config;

// Compat code was from the Qzminer, which is licensed under MIT License(template?!)
public final class FortuneCompatHelper {

    private FortuneCompatHelper() {}

    public static boolean shouldTreatOreAsNatural(boolean natural) {
        return natural || Config.enableFortuneForPlacedOre;
    }

    public static boolean shouldKeepFortuneCapCheck(boolean original) {
        return original && !Config.enableUnlimitedOreFortune;
    }
}
