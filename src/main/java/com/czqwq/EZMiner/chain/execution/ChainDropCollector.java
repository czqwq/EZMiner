package com.czqwq.EZMiner.chain.execution;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.item.ItemStack;

/**
 * Execution-layer drop aggregation holder.
 */
public class ChainDropCollector {

    private final List<ItemStack> drops = new ArrayList<>();

    public List<ItemStack> getDrops() {
        return drops;
    }

    public void clear() {
        drops.clear();
    }
}
