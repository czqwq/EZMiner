package com.czqwq.EZMiner.core.founder;

import java.lang.reflect.Field;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraftforge.oredict.OreDictionary;

import org.joml.Vector3i;

import com.czqwq.EZMiner.EZMiner;
import com.czqwq.EZMiner.compat.EtFuturumOreCompat;
import com.czqwq.EZMiner.compat.ore.OreCompatRegistry;

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
    private static volatile Class<?> gtBlockOreClass;
    /** Legacy ore class — both large and small ores; distinguished via TileEntityOres.mMetaData. */
    private static volatile Class<?> gtBlockOresAbstractLegacyClass;

    // ===== Ore cache =====
    /**
     * Per-{@link Block} ore metadata mask: bit {@code i} is set when metadata {@code i} of
     * that block is an ore.
     *
     * <p>
     * The mask is essential: a single block can mix ore and non-ore metadata variants.
     * Galacticraft's {@code blockMoon} (one Block, 16 metadata values) registers metadata
     * 0/1/2 as {@code oreCopper}/{@code oreTin}/{@code oreCheese}, while metadata 3/4/5..13/14
     * are moon dirt / moon rock (月球石头) / moon turf / dungeon bricks. The previous
     * block-wide {@code Boolean} made <em>every</em> metadata of such a block look like an
     * ore, so the blast "ore only" mode chain-mined plain moon rock around a GT ore.
     */
    private static final ConcurrentHashMap<Block, Integer> oreBlockCache = new ConcurrentHashMap<>();
    /** Number of metadata values the mask covers (vanilla metadata range). */
    private static final int ORE_META_BITS = 16;
    /** Mask meaning "every metadata of this block is an ore" (block-class detectors). */
    private static final int ALL_META_ORE = (1 << ORE_META_BITS) - 1;

    public static void checkCompatibility() {
        if (checked) return;
        checked = true;
        hasGTTileEntity = classExists("gregtech.api.interfaces.tileentity.IGregTechTileEntity");
        hasTileEntityOres = classExists("gregtech.common.blocks.TileEntityOres");
        hasBWTileEntity = classExists("bartworks.system.material.TileEntityMetaGeneratedBlock");
        hasBlockOresAbstract = classExists("gregtech.common.blocks.BlockOresAbstract");
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

    /** Thread-safe set of ore package names already logged. Uses CHM key set for lock-free reads. */
    private static final Set<String> reportedOrePackages = ConcurrentHashMap.newKeySet();

    /** True if the block at {@code pos} is an ore. Metadata aware; cached per Block. */
    public static boolean isOreBlock(Vector3i pos, EntityPlayer player) {
        Block block = player.worldObj.getBlock(pos.x, pos.y, pos.z);
        if (block == null) return false;
        int meta = player.worldObj.getBlockMetadata(pos.x, pos.y, pos.z);
        return isOreBlock(block, meta, pos, player);
    }

    /**
     * Overload with pre-fetched block/metadata — the blast "ore only" scan already has
     * both, so this avoids two redundant world lookups per candidate.
     */
    public static boolean isOreBlock(Block block, int meta, Vector3i pos, EntityPlayer player) {
        if (block == null) return false;
        if (isOreMeta(block, meta)) return true;
        // TE-only ore families (e.g. BartWorks ore TileEntities) need the live TE.
        // Only pay for the getTileEntity lookup when such an adapter is present,
        // and only scan TE-only adapters (block-only adapters already missed).
        if (!OreCompatRegistry.hasTileEntityOnlyDetectors()) return false;
        TileEntity tileEntity = player.worldObj.getTileEntity(pos.x, pos.y, pos.z);
        return OreCompatRegistry.isOreBlockByTileEntity(tileEntity);
    }

    /** Metadata-aware lookup against the cached per-Block ore mask. */
    private static boolean isOreMeta(Block block, int meta) {
        int mask = oreBlockCache.computeIfAbsent(block, DeterminingIdentical::computeOreMetaMask);
        if (mask == 0) return false;
        // Extended (NEID) metadata outside the vanilla range: only block-wide detectors
        // (block class / unlocalized name) can speak for it, never a metadata variant.
        if (meta < 0 || meta >= ORE_META_BITS) return mask == ALL_META_ORE;
        return (mask & (1 << meta)) != 0;
    }

    /**
     * Builds the per-metadata ore mask of {@code block}.
     *
     * <p>
     * Block-class detectors and the unlocalized-name fallback are metadata-agnostic (the
     * whole block is that ore family → {@link #ALL_META_ORE}); the OreDictionary fallback is
     * evaluated per metadata so a mixed block only exposes its real ore variants.
     */
    private static int computeOreMetaMask(Block block) {
        // Vanilla + all block-class based optional-mod ores; TE-only adapters are
        // handled by the TE-aware path in isOreBlock(Block, int, Vector3i, EntityPlayer).
        if (OreCompatRegistry.isOreBlock(block, null)) return ALL_META_ORE;

        // ── Fallback 1: OreDictionary "ore*" registration, per metadata ───────────────────
        // Precise — machines are never registered under "ore*". Covers mods whose ore blocks
        // are plain Block subclasses but register ore dict (e.g. Forestry resources).
        // GTNH's getOreIDs resolves both wildcard and exact-metadata registrations for the
        // queried stack, so a WILDCARD_VALUE registration sets every bit while a
        // meta-specific one (e.g. Galacticraft oreCopper on blockMoon meta 0) sets one.
        int mask = 0;
        for (int meta = 0; meta < ORE_META_BITS; meta++) {
            for (int oreID : OreDictionary.getOreIDs(new ItemStack(block, 1, meta))) {
                if (OreDictionary.getOreName(oreID)
                    .startsWith("ore")) {
                    mask |= 1 << meta;
                    break;
                }
            }
        }

        // ── Fallback 2: "ore" starts a dot-separated name segment ─────────────────────────
        // e.g. "tile.oreCopper", "tile.forestry.oreApatite". A bare substring check is wrong:
        // "tile.for.core" (Forestry escritoire/analyzer) contains "ore" only as the "core"
        // suffix, which made the ore blast mode chain-mine writing desks (写字台).
        if (block.getUnlocalizedName()
            .toLowerCase()
            .contains(".ore")) {
            String pkg = block.getClass()
                .getName();
            if (!reportedOrePackages.contains(pkg)) {
                reportedOrePackages.add(pkg);
                EZMiner.LOG.info("Detected possible unregistered ore class: {}", pkg);
            }
            return ALL_META_ORE;
        }
        return mask;
    }

    /**
     * True if the block at {@code pos} is a GregTech ore block — gates VisualProspecting
     * vein discovery.
     *
     * <p>
     * Version-agnostic across every GT5U generation:
     * <ul>
     * <li>old GT5U &lt; 5.10 ({@code BlockOresAbstract}, e.g. 5.09.51.482),</li>
     * <li>newer GT5U TE-based legacy ores ({@code BlockOresAbstractLegacy}), and</li>
     * <li>newer GT5U metadata-based ores ({@code GTBlockOre}).</li>
     * </ul>
     */
    public static boolean isGTOreBlock(Vector3i pos, EntityPlayer player) {
        Block block = player.worldObj.getBlock(pos.x, pos.y, pos.z);
        return block != null && isGTOreBlock(block);
    }

    /** Overload with a pre-fetched block — avoids a redundant world lookup. */
    public static boolean isGTOreBlock(Block block) {
        if (block == null) return false;
        if (hasBlockOresAbstract && gtBlockOresAbstractClass != null && gtBlockOresAbstractClass.isInstance(block)) {
            return true;
        }
        if (hasBlockOresAbstractLegacy && gtBlockOresAbstractLegacyClass != null
            && gtBlockOresAbstractLegacyClass.isInstance(block)) {
            return true;
        }
        return hasGTBlockOre && gtBlockOreClass != null && gtBlockOreClass.isInstance(block);
    }

    /**
     * True if {@code block} is a GregTech ore container that always carries a
     * {@link TileEntity} holding the real ore identity in {@code mMetaData}.
     *
     * <p>
     * This covers both ore generations:
     * <ul>
     * <li>old GT5U &lt; 5.10 ({@code gregtech.common.blocks.BlockOresAbstract} —
     * e.g. 5.09.51.482), and</li>
     * <li>newer GT5U ({@code gregtech.common.blocks.BlockOresAbstractLegacy}).
     * </ul>
     *
     * <p>
     * The harvest executors use this as a <em>second</em> "must use the vanilla
     * {@code tryHarvestBlock} path" gate, consulted only for blocks whose per-meta
     * {@code hasTileEntity(meta)} is already false (the fast-path candidates). It
     * guarantees such GT ore containers are always broken through
     * {@code breakBlock}/{@code removeTileEntity}, so they can never be reduced to
     * a stale {@code air + leftover metadata/TE} state that renders as
     * {@code name.0}. Because the two hierarchy classes live on different GT5U
     * versions, at most one cached reference is non-null at runtime, so the check
     * is a single {@code isInstance} in practice (near-zero cost on the hot path;
     * modern metadata-only {@code GTBlockOre} is intentionally NOT matched and
     * keeps the fast path).
     */
    public static boolean isGTTileEntityCarrier(Block block) {
        if (block == null) return false;
        if (hasBlockOresAbstract && gtBlockOresAbstractClass != null && gtBlockOresAbstractClass.isInstance(block)) {
            return true;
        }
        return hasBlockOresAbstractLegacy && gtBlockOresAbstractLegacyClass != null
            && gtBlockOresAbstractLegacyClass.isInstance(block);
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
