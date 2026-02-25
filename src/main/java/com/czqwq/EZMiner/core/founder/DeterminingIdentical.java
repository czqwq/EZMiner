package com.czqwq.EZMiner.core.founder;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

import net.minecraft.block.Block;
import net.minecraft.block.BlockOre;
import net.minecraft.block.BlockRedstoneOre;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;

import org.joml.Vector3i;

import com.czqwq.EZMiner.EZMiner;

/**
 * Utilities for comparing blocks and identifying ore blocks.
 * Uses reflection-based soft compatibility with GT5 / BartWorks / GTPlusPlus.
 */
public class DeterminingIdentical {

    private static boolean checked = false;

    // ===== Compatibility flags =====
    static boolean hasGTTileEntity = false;
    static boolean hasTileEntityOres = false;
    static boolean hasBWTileEntity = false;
    static boolean hasBlockOresAbstract = false;
    static boolean hasBWSmallOres = false;
    static boolean hasBWOres = false;
    static boolean hasBlockBaseOre = false;
    static boolean hasAEQuartz = false;
    static boolean hasAEQuartzCharged = false;

    public static void checkCompatibility() {
        if (checked) return;
        checked = true;
        hasGTTileEntity = classExists("gregtech.api.interfaces.tileentity.IGregTechTileEntity");
        hasTileEntityOres = classExists("gregtech.common.blocks.TileEntityOres");
        hasBWTileEntity = classExists("bartworks.system.material.TileEntityMetaGeneratedBlock");
        hasBlockOresAbstract = classExists("gregtech.common.blocks.BlockOresAbstract");
        hasBWSmallOres = classExists("bartworks.system.material.BWMetaGeneratedSmallOres");
        hasBWOres = classExists("bartworks.system.material.BWMetaGeneratedOres");
        hasBlockBaseOre = classExists("gtPlusPlus.core.block.base.BlockBaseOre");
        hasAEQuartz = classExists("appeng.block.solids.OreQuartz");
        hasAEQuartzCharged = classExists("appeng.block.solids.OreQuartzCharged");
    }

    private static boolean classExists(String name) {
        try {
            Class.forName(name);
            return true;
        } catch (ClassNotFoundException e) {
            EZMiner.LOG.debug("Optional class not found: {}", name);
            return false;
        }
    }

    /** Returns true if the block at pos matches the given sample. */
    public static boolean identical(Block sBlock, int sMeta, TileEntity sTile, Vector3i pos, EntityPlayer player) {
        Block tBlock = player.worldObj.getBlock(pos.x, pos.y, pos.z);
        int tMeta = player.worldObj.getBlockMetadata(pos.x, pos.y, pos.z);
        TileEntity tTile = player.worldObj.getTileEntity(pos.x, pos.y, pos.z);

        if (!sBlock.equals(tBlock) || sMeta != tMeta) return false;

        // GregTech ore tiles
        if (hasTileEntityOres && sTile != null && tTile != null) {
            try {
                Class<?> cls = Class.forName("gregtech.common.blocks.TileEntityOres");
                if (cls.isInstance(sTile) && cls.isInstance(tTile)) {
                    int sm = (int) cls.getMethod("getMeta")
                        .invoke(sTile);
                    int tm = (int) cls.getMethod("getMeta")
                        .invoke(tTile);
                    return sm == tm;
                }
            } catch (Exception ignored) {}
        }
        // BartWorks meta blocks
        if (hasBWTileEntity && sTile != null && tTile != null) {
            try {
                Class<?> cls = Class.forName("bartworks.system.material.TileEntityMetaGeneratedBlock");
                if (cls.isInstance(sTile) && cls.isInstance(tTile)) {
                    int sm = (int) cls.getField("mMetaData")
                        .get(sTile);
                    int tm = (int) cls.getField("mMetaData")
                        .get(tTile);
                    return sm == tm;
                }
            } catch (Exception ignored) {}
        }
        if (sTile != null && tTile != null) return sTile.getBlockMetadata() == tTile.getBlockMetadata();
        return true;
    }

    private static final Set<String> reportedOrePackages = new HashSet<>();

    /** Returns true if the block at pos is considered an ore block. */
    public static boolean isOreBlock(Vector3i pos, EntityPlayer player) {
        Block block = player.worldObj.getBlock(pos.x, pos.y, pos.z);
        if (block instanceof BlockOre || block instanceof BlockRedstoneOre) return true;

        // GT
        if (hasBlockOresAbstract) {
            try {
                Class<?> cls = Class.forName("gregtech.common.blocks.BlockOresAbstract");
                if (cls.isInstance(block)) return true;
            } catch (Exception ignored) {}
        }
        // BW
        if (hasBWSmallOres) {
            try {
                Class<?> cls = Class.forName("bartworks.system.material.BWMetaGeneratedSmallOres");
                if (cls.isInstance(block)) return true;
            } catch (Exception ignored) {}
        }
        if (hasBWOres) {
            try {
                Class<?> cls = Class.forName("bartworks.system.material.BWMetaGeneratedOres");
                if (cls.isInstance(block)) return true;
            } catch (Exception ignored) {}
        }
        // GTPlusPlus
        if (hasBlockBaseOre) {
            try {
                Class<?> cls = Class.forName("gtPlusPlus.core.block.base.BlockBaseOre");
                if (cls.isInstance(block)) return true;
            } catch (Exception ignored) {}
        }
        // AE2
        if (hasAEQuartz) {
            try {
                Class<?> cls = Class.forName("appeng.block.solids.OreQuartz");
                if (cls.isInstance(block)) return true;
            } catch (Exception ignored) {}
        }
        if (hasAEQuartzCharged) {
            try {
                Class<?> cls = Class.forName("appeng.block.solids.OreQuartzCharged");
                if (cls.isInstance(block)) return true;
            } catch (Exception ignored) {}
        }
        // Fallback: unlocalized name contains "ore"
        String unloc = block.getUnlocalizedName()
            .toLowerCase();
        if (unloc.contains("ore")) {
            String pkg = block.getClass()
                .getName();
            if (!reportedOrePackages.contains(pkg)) {
                reportedOrePackages.add(pkg);
                EZMiner.LOG.info("Detected possible unregistered ore class: {}", pkg);
            }
            return true;
        }
        return false;
    }

    /** Returns true if two ItemStacks are identical (type, damage, NBT). */
    public static boolean isSame(ItemStack a, ItemStack b) {
        if (a == null || b == null) return a == b;
        if (!Objects.equals(a.getItem(), b.getItem())) return false;
        if (a.getItemDamage() != b.getItemDamage()) return false;
        NBTTagCompound tagA = a.getTagCompound();
        NBTTagCompound tagB = b.getTagCompound();
        if (tagA == null && tagB == null) return true;
        if (tagA != null && tagB != null) return tagA.equals(tagB);
        return false;
    }
}
