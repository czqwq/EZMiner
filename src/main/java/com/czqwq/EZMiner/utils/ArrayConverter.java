package com.czqwq.EZMiner.utils;

import java.util.ArrayList;

public class ArrayConverter {

    public static float[] convertF(ArrayList<float[]> list) {
        int totalLength = 0;
        for (float[] array : list) {
            totalLength += array.length;
        }
        float[] result = new float[totalLength];
        int currentPos = 0;
        for (float[] array : list) {
            System.arraycopy(array, 0, result, currentPos, array.length);
            currentPos += array.length;
        }
        return result;
    }

    public static int[] convertI(ArrayList<int[]> list) {
        int totalLength = 0;
        for (int[] array : list) {
            totalLength += array.length;
        }
        int[] result = new int[totalLength];
        int offset = 0;
        for (int[] array : list) {
            System.arraycopy(array, 0, result, offset, array.length);
            offset += array.length;
        }
        return result;
    }
}
