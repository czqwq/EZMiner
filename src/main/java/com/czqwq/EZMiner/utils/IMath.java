package com.czqwq.EZMiner.utils;

public class IMath {

    public static int clamp(int value, int min, int max) {
        return Math.min(Math.max(value, min), max);
    }
}
