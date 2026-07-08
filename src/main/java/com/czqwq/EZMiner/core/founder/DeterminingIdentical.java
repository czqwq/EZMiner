package com.czqwq.EZMiner.core.founder;

import java.lang.reflect.Field;
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
import net.minecraft.world.World;

import org.joml.Vector3i;

import com.czqwq.EZMiner.EZMiner;
import com.czqwq.EZMiner.compat.EtFuturumOreCompat;

/**
 * Block comparison and ore detection utilities.
 * All reflection objects cached once in {@link #checkCompatibility()};
 * ore-block results cached per {@link Block} instance.
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
    /** GT5 >= 5.09: new-style GTBlockOre. */
    static boolean hasGTBlockOre = false;
    /** Legacy BlockOresAbstractLegacy (GT5-Unofficial). */
    static boolean hasBlockOresAbstractLegacy = false;

    /** Meta >= 16000 → small/surface ore (贫瘠矿). Mirrors GTBlockOre.SMALL_ORE_META_OFFSET. */
    private static final int GT_SMALL_ORE_META_OFFSET = 16000;

    // ===== Cached reflection objects =====
    private static volatile Class<?> gtTileEntityOresClass;
    /** TileEntityOres.mMetaData — raw field access (getMeta() removed in GT5-Unofficial). */
    private static volatile Field tileEntityMMetaDataField;
    private static volatile Class<?> bwTileEntityClass;
    private static volatile Field bwMetaDataField;
    private static volatile Class<?> gtBlockOresAbstractClass;
    private static volatile Class<?> bwSmallOresClass;
    private static volatile Class<?> bwOresClass;
    private static volatile Class<?> gtPlusPlusBlockBaseOreClass;
    private static volatile Class<?> aeQuartzClass;
    private static volatile Class<?> aeQuartzChargedClass;
    private static volatile Class<?> gtBlockOreClass;
    /** Legacy ore class — both large and small ores; distinguished via TileEntityOres.mMetaData. */
    private static volatile Class<?> gtBlockOresAbstractLegacyClass;

    // ===== Ore cache =====
    private static final ConcurrentHashMap<Block, Boolean> oreBlockCache = new ConcurrentHashMap<>();

    public static void checkCompatibility() {
        if (checked) return;
        checked = true;
        hasGTTileEntity = classExists("gregtech.api.interfaces.tileentity.IGregTechTileEntity");
        hasTileEntityOres = classExists("gregtech.common.blocks.TileEntityOres");
        hasBWTileEntity = classExists("bartworks.system.material.TileEntityMetaGeneratedBlock");
        hasBlockOresAbstract = classExists("gregtech.common.blocks.BlockOresAbstract");
        hasBWSmallOres = classExists("bartworks.system.material.BWMetaGeneratedSmallOres");
        hasBlockBaseOre = classExists("gtPlusPlus.core.block.base.BlockBaseOre");
        hasAEQuartz = classExists("appeng.block.solids.OreQuartz");
        hasAEQuartzCharged = classExists("appeng.block.solids.OreQuartzCharged");
        hasGTBlockOre = classExists("gregtech.common.blocks.GTBlockOre");
        hasBlockOresAbstractLegacy = classExists("gregtech.common.blocks.BlockOresAbstractLegacy");

        // EFR – delegate to dedicated compat class (Et Futurum Requiem)
        EtFuturumOreCompat.init();

        // Cache reflection references so identical() / isOreBlock() never call Class.forName()
        if (hasTileEntityOres) {
            try {
                gtTileEntityOresClass = Class.forName("gregtech.common.blocks.TileEntityOres");
                // getMeta() was removed in GT5-Unofficial; the ore type is stored in the
                // public field mMetaData (short). Cache the field for direct access.
                tileEntityMMetaDataField = gtTileEntityOresClass.getField("mMetaData");
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
        if (hasGTBlockOre) {
            try {
                gtBlockOreClass = Class.forName("gregtech.common.blocks.GTBlockOre");
            } catch (Exception e) {
                EZMiner.LOG.debug("Failed to cache GTBlockOre reflection: {}", e.getMessage());
                hasGTBlockOre = false;
            }
        }
        if (hasBlockOresAbstractLegacy) {
            try {
                gtBlockOresAbstractLegacyClass = Class.forName("gregtech.common.blocks.BlockOresAbstractLegacy");
            } catch (Exception e) {
                EZMiner.LOG.debug("Failed to cache BlockOresAbstractLegacy reflection: {}", e.getMessage());
                hasBlockOresAbstractLegacy = false;
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

    /** True if block at pos matches sample (block + meta + optional tile entity). */
    public static boolean identical(Block sBlock, int sMeta, TileEntity sTile, Vector3i pos, EntityPlayer player) {
        Block tBlock = player.worldObj.getBlock(pos.x, pos.y, pos.z);
        int tMeta = player.worldObj.getBlockMetadata(pos.x, pos.y, pos.z);
        return identical(sBlock, sMeta, sTile, tBlock, tMeta, pos, player);
    }

    /** Same as above with pre-fetched tBlock/tMeta to avoid duplicate world lookups. */
    public static boolean identical(Block sBlock, int sMeta, TileEntity sTile, Block tBlock, int tMeta, Vector3i pos,
        EntityPlayer player) {
        if (!sBlock.equals(tBlock) || sMeta != tMeta) return false;
        // No tile entity on the sample block: block+meta match is sufficient.
        if (sTile == null) return true;

        TileEntity tTile = player.worldObj.getTileEntity(pos.x, pos.y, pos.z);
        if (tTile == null) return true;

        // GregTech ore tiles – compare via mMetaData field (getMeta() was removed in GT5-Unofficial)
        if (hasTileEntityOres && gtTileEntityOresClass != null
            && gtTileEntityOresClass.isInstance(sTile)
            && gtTileEntityOresClass.isInstance(tTile)
            && tileEntityMMetaDataField != null) {
            try {
                return readUnsignedMeta(tileEntityMMetaDataField, sTile)
                    == readUnsignedMeta(tileEntityMMetaDataField, tTile);
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

    /** True if block at pos is an ore. Results cached per Block instance. */
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

        // EFR – Et Futurum Requiem ores (delegated to compat class)
        if (EtFuturumOreCompat.isOreBlock(block)) return true;

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

    /** True if GregTech BlockOresAbstract — gates VisualProspecting vein discovery. */
    public static boolean isGTOreBlock(Vector3i pos, EntityPlayer player) {
        if (!hasBlockOresAbstract || gtBlockOresAbstractClass == null) return false;
        Block block = player.worldObj.getBlock(pos.x, pos.y, pos.z);
        return gtBlockOresAbstractClass.isInstance(block);
    }

    /** Convenience overload — prefers world-less check (conservative for legacy). */
    public static boolean isGTLargeVeinOre(Block block, int meta) {
        return isGTLargeVeinOre(block, meta, null, 0, 0, 0);
    }

    /**
     * True if GT large-vein ore, excluding surface small ores (贫瘠矿).
     *
     * <p>
     * New system (GTBlockOre): meta &lt; 16000 = large vein (NEID required).
     * Legacy (BlockOresAbstractLegacy/BlockOresAbstract): checks TileEntityOres.mMetaData.
     * When world is null, conservatively returns true for legacy ore blocks.
     * </p>
     */
    public static boolean isGTLargeVeinOre(Block block, int meta, World world, int x, int y, int z) {
        // ── New ore system: GTBlockOre ─────────────────────────────────────────────────────
        // Small ores have NEID-extended metadata >= GT_SMALL_ORE_META_OFFSET (16000).
        // We use the constant directly to avoid a reflective method call.
        if (hasGTBlockOre && gtBlockOreClass != null && gtBlockOreClass.isInstance(block)) {
            return meta < GT_SMALL_ORE_META_OFFSET;
        }
        // ── Legacy ore system: BlockOresAbstractLegacy ─────────────────────────────────────
        // Both large-vein and small ores share BlockOresLegacy (extends BlockOresAbstractLegacy).
        // Distinguish them via TileEntityOres.mMetaData: >= 16000 means small ore.
        if (hasBlockOresAbstractLegacy && gtBlockOresAbstractLegacyClass != null
            && gtBlockOresAbstractLegacyClass.isInstance(block)) {
            return isLargeVeinByTileEntity(world, x, y, z);
        }
        // ── Really-old legacy system: BlockOresAbstract ────────────────────────────────────
        if (hasBlockOresAbstract && gtBlockOresAbstractClass != null && gtBlockOresAbstractClass.isInstance(block)) {
            return isLargeVeinByTileEntity(world, x, y, z);
        }
        return false;
    }

    /** Check TileEntityOres.mMetaData < 16000. Returns true on any failure (safe side). */
    private static boolean isLargeVeinByTileEntity(World world, int x, int y, int z) {
        if (world == null || !hasTileEntityOres || gtTileEntityOresClass == null || tileEntityMMetaDataField == null) {
            return true; // conservative: accept when we cannot verify
        }
        TileEntity te = world.getTileEntity(x, y, z);
        if (te == null || !gtTileEntityOresClass.isInstance(te)) {
            return true; // no ore tile entity — conservative accept
        }
        try {
            return readUnsignedMeta(tileEntityMMetaDataField, te) < GT_SMALL_ORE_META_OFFSET;
        } catch (Exception ignored) {
            return true; // reflection failure — conservative accept
        }
    }

    /** Read public short field as unsigned int [0, 65535] via reflection. */
    private static int readUnsignedMeta(Field field, TileEntity te) throws ReflectiveOperationException {
        return ((Short) field.get(te)) & 0xFFFF;
    }

    /** True if two ItemStacks match (item type, damage, NBT). */
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
