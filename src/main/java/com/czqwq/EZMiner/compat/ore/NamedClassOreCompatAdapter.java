package com.czqwq.EZMiner.compat.ore;

import net.minecraft.block.Block;
import net.minecraft.tileentity.TileEntity;

import com.czqwq.EZMiner.compat.ClassNameCompatSupport;

/**
 * Class-name based ore adapter: matches a block class and/or a tile-entity class.
 *
 * <p>
 * The classes are resolved lazily and safely through
 * {@link ClassNameCompatSupport}, so this adapter never causes a crash when the
 * optional mod (GT5U, BartWorks, GT++, AE2, …) is absent.
 */
public final class NamedClassOreCompatAdapter implements OreCompatAdapter {

    private final Class<?> blockType;
    private final Class<?> tileEntityType;

    public NamedClassOreCompatAdapter(String blockClassName, String tileEntityClassName) {
        this.blockType = ClassNameCompatSupport.resolveClass(blockClassName);
        this.tileEntityType = ClassNameCompatSupport.resolveClass(tileEntityClassName);
    }

    @Override
    public boolean isAvailable() {
        return blockType != null || tileEntityType != null;
    }

    @Override
    public boolean isTileEntityOnly() {
        return blockType == null && tileEntityType != null;
    }

    @Override
    public boolean isOreBlock(Block block, TileEntity tileEntity) {
        return ClassNameCompatSupport.isInstance(blockType, block)
            || ClassNameCompatSupport.isInstance(tileEntityType, tileEntity);
    }
}
