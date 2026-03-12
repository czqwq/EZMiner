package com.czqwq.EZMiner.core.founder;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

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
 *
 * <p>
 * All reflection objects (Class, Method, Field) are cached once during
 * {@link #checkCompatibility()} to avoid repeated {@code Class.forName()} /
 * {@code getMethod()} / {@code getField()} lookups on every block check.
 *
 * <p>
 * Ore-block type classification is cached per {@link Block} instance so that
 * the reflection-heavy path in {@link #isOreBlock} is only evaluated once per
 * unique block type encountered during a mining operation.
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

    // ===== Cached reflection objects (set once in checkCompatibility) =====
    private static volatile Class<?> gtTileEntityOresClass;
    private static volatile Method gtGetMetaMethod;
    private static volatile Class<?> bwTileEntityClass;
    private static volatile Field bwMetaDataField;
    private static volatile Class<?> gtBlockOresAbstractClass;
    private static volatile Class<?> bwSmallOresClass;
    private static volatile Class<?> bwOresClass;
    private static volatile Class<?> gtPlusPlusBlockBaseOreClass;
    private static volatile Class<?> aeQuartzClass;
    private static volatile Class<?> aeQuartzChargedClass;

    // ===== Per-block-type ore cache =====
    /** Maps Block instance → isOreBlock result; populated lazily and shared across threads. */
    private static final ConcurrentHashMap<Block, Boolean> oreBlockCache = new ConcurrentHashMap<>();

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

        // Cache reflection references so identical() / isOreBlock() never call Class.forName()
        if (hasTileEntityOres) {
            try {
                gtTileEntityOresClass = Class.forName("gregtech.common.blocks.TileEntityOres");
                gtGetMetaMethod = gtTileEntityOresClass.getMethod("getMeta");
            } catch (Exception e) {
                EZMiner.LOG.debug("Failed to cache GT TileEntityOres reflection: {}", e.getMessage());
                hasTileEntityOres = false;
            }
        }
        if (hasBWTileEntity) {
            try {
                bwTileEntityClass = Class.forName("bartworks.system.material.TileEntityMetaGeneratedBlock");
                bwMetaDataField = bwTileEntityClass.getField("mMetaData");
            } catch (Exception e) {
                EZMiner.LOG.debug("Failed to cache BW TileEntityMetaGeneratedBlock reflection: {}", e.getMessage());
                hasBWTileEntity = false;
            }
        }
        if (hasBlockOresAbstract) {
            try {
                gtBlockOresAbstractClass = Class.forName("gregtech.common.blocks.BlockOresAbstract");
            } catch (Exception ignored) {
                hasBlockOresAbstract = false;
            }
        }
        if (hasBWSmallOres) {
            try {
                bwSmallOresClass = Class.forName("bartworks.system.material.BWMetaGeneratedSmallOres");
            } catch (Exception ignored) {
                hasBWSmallOres = false;
            }
        }
        if (hasBWOres) {
            try {
                bwOresClass = Class.forName("bartworks.system.material.BWMetaGeneratedOres");
            } catch (Exception ignored) {
                hasBWOres = false;
            }
        }
        if (hasBlockBaseOre) {
            try {
                gtPlusPlusBlockBaseOreClass = Class.forName("gtPlusPlus.core.block.base.BlockBaseOre");
            } catch (Exception ignored) {
                hasBlockBaseOre = false;
            }
        }
        if (hasAEQuartz) {
            try {
                aeQuartzClass = Class.forName("appeng.block.solids.OreQuartz");
            } catch (Exception ignored) {
                hasAEQuartz = false;
            }
        }
        if (hasAEQuartzCharged) {
            try {
                aeQuartzChargedClass = Class.forName("appeng.block.solids.OreQuartzCharged");
            } catch (Exception ignored) {
                hasAEQuartzCharged = false;
            }
        }
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
        return identical(sBlock, sMeta, sTile, tBlock, tMeta, pos, player);
    }

    /**
     * Returns true if the target block matches the given sample, using pre-fetched
     * {@code tBlock} and {@code tMeta} to avoid duplicate world lookups when the
     * caller has already read the block data.
     */
    public static boolean identical(Block sBlock, int sMeta, TileEntity sTile, Block tBlock, int tMeta, Vector3i pos,
        EntityPlayer player) {
        if (!sBlock.equals(tBlock) || sMeta != tMeta) return false;
        // No tile entity on the sample block: block+meta match is sufficient.
        if (sTile == null) return true;

        TileEntity tTile = player.worldObj.getTileEntity(pos.x, pos.y, pos.z);
        if (tTile == null) return true;

        // GregTech ore tiles – use cached Class/Method references
        if (hasTileEntityOres && gtTileEntityOresClass != null
            && gtTileEntityOresClass.isInstance(sTile)
            && gtTileEntityOresClass.isInstance(tTile)) {
            try {
                int sm = (int) gtGetMetaMethod.invoke(sTile);
                int tm = (int) gtGetMetaMethod.invoke(tTile);
                return sm == tm;
            } catch (Exception ignored) {}
        }
        // BartWorks meta blocks – use cached Class/Field references
        if (hasBWTileEntity && bwTileEntityClass != null
            && bwTileEntityClass.isInstance(sTile)
            && bwTileEntityClass.isInstance(tTile)) {
            try {
                int sm = (int) bwMetaDataField.get(sTile);
                int tm = (int) bwMetaDataField.get(tTile);
                return sm == tm;
            } catch (Exception ignored) {}
        }
        return sTile.getBlockMetadata() == tTile.getBlockMetadata();
    }

    private static final Set<String> reportedOrePackages = new HashSet<>();

    /**
     * Returns true if the block at pos is considered an ore block.
     *
     * <p>
     * Results are cached per {@link Block} instance so the reflection path is
     * only executed once per unique block type.
     */
    public static boolean isOreBlock(Vector3i pos, EntityPlayer player) {
        Block block = player.worldObj.getBlock(pos.x, pos.y, pos.z);
        return oreBlockCache.computeIfAbsent(block, DeterminingIdentical::computeIsOreBlock);
    }

    private static boolean computeIsOreBlock(Block block) {
        if (block instanceof BlockOre || block instanceof BlockRedstoneOre) return true;

        // GT – uses cached class reference
        if (hasBlockOresAbstract && gtBlockOresAbstractClass != null && gtBlockOresAbstractClass.isInstance(block))
            return true;
        // BW – uses cached class references
        if (hasBWSmallOres && bwSmallOresClass != null && bwSmallOresClass.isInstance(block)) return true;
        if (hasBWOres && bwOresClass != null && bwOresClass.isInstance(block)) return true;
        // GTPlusPlus – uses cached class reference
        if (hasBlockBaseOre && gtPlusPlusBlockBaseOreClass != null && gtPlusPlusBlockBaseOreClass.isInstance(block))
            return true;
        // AE2 – uses cached class references
        if (hasAEQuartz && aeQuartzClass != null && aeQuartzClass.isInstance(block)) return true;
        if (hasAEQuartzCharged && aeQuartzChargedClass != null && aeQuartzChargedClass.isInstance(block)) return true;

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

    /**
     * Returns true if the block at pos is specifically a GregTech ore ({@code BlockOresAbstract}).
     * Used to gate Visual Prospecting ore-vein discovery calls.
     */
    public static boolean isGTOreBlock(Vector3i pos, EntityPlayer player) {
        if (!hasBlockOresAbstract || gtBlockOresAbstractClass == null) return false;
        Block block = player.worldObj.getBlock(pos.x, pos.y, pos.z);
        return gtBlockOresAbstractClass.isInstance(block);
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
