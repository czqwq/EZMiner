package com.czqwq.EZMiner.compat;

import java.lang.reflect.Field;

import net.minecraft.block.Block;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

import com.czqwq.EZMiner.EZMiner;

/**
 * Reflection-based bridge for GT5U meta-block support in block-swap mode.
 *
 * <p>
 * GT5U uses a single {@code BlockMachines} class for all machines, pipes, and
 * cables. The variant is determined by the TileEntity's {@code mID} field (a
 * short), which indexes into {@code GregTechAPI.METATILEENTITIES[]}. Replacing
 * such a block requires using the correct base meta (from
 * {@code IMetaTileEntity.getTileEntityBaseType()}) and initialising the new
 * TileEntity with the replacement type's ID.
 *
 * <p>
 * For cables/pipes, the connection state is stored in a public {@code byte
 * mConnections} field on both {@code BaseMetaPipeEntity} and
 * {@code MetaPipeEntity}. This bridge saves that byte before the old block is
 * removed and restores it on the new block so connections are preserved.
 *
 * <p>
 * No compile-time dependency on GT5U — everything is accessed via reflection
 * with cached handles, following the same pattern as {@link GT5ToolCompat} and
 * {@link com.czqwq.EZMiner.core.founder.DeterminingIdentical}.
 */
public class GT5BlockSwapCompat {

    private static volatile boolean checked = false;
    private static volatile boolean available = false;

    // ── Cached reflection handles ──
    private static Class<?> gregTechAPIClass;
    private static Class<?> commonBaseMetaTileEntityClass;
    private static Class<?> baseMetaPipeEntityClass;
    private static Field sBlockMachinesField;
    private static Field metaTileEntitiesField;
    private static Field baseMetaPipeMConnectionsField;
    private static Field metaPipeMConnectionsField;
    private static Field coverableTileEntityMIdField;

    /** Call once during mod init or lazily on first use. */
    public static void checkCompatibility() {
        if (checked) return;
        checked = true;
        try {
            gregTechAPIClass = Class.forName("gregtech.api.GregTechAPI");
            commonBaseMetaTileEntityClass = Class.forName("gregtech.api.metatileentity.CommonBaseMetaTileEntity");

            // GregTechAPI.sBlockMachines (public static Block)
            sBlockMachinesField = gregTechAPIClass.getField("sBlockMachines");
            // GregTechAPI.METATILEENTITIES (public static final IMetaTileEntity[])
            metaTileEntitiesField = gregTechAPIClass.getField("METATILEENTITIES");

            // BaseMetaPipeEntity.mConnections (public byte)
            baseMetaPipeEntityClass = Class.forName("gregtech.api.metatileentity.BaseMetaPipeEntity");
            baseMetaPipeMConnectionsField = baseMetaPipeEntityClass.getField("mConnections");

            // MetaPipeEntity.mConnections (public byte) — separate field, must also sync
            Class<?> metaPipeClass = Class.forName("gregtech.api.metatileentity.MetaPipeEntity");
            metaPipeMConnectionsField = metaPipeClass.getField("mConnections");

            // CoverableTileEntity.mID (public short) — meta-tile-entity type identifier
            Class<?> coverableTileEntityClass = Class.forName("gregtech.api.metatileentity.CoverableTileEntity");
            coverableTileEntityMIdField = coverableTileEntityClass.getField("mID");

            available = true;
        } catch (Exception e) {
            EZMiner.LOG.debug("GT5BlockSwapCompat: GT5U not available — {}", e.getMessage());
        }
    }

    public static boolean isAvailable() {
        if (!checked) checkCompatibility();
        return available;
    }

    // ── Cable detection ──

    /** True if {@code te} is a GT cable/pipe (BaseMetaPipeEntity). */
    public static boolean isCable(TileEntity te) {
        if (te == null || !isAvailable()) return false;
        return baseMetaPipeEntityClass.isInstance(te);
    }

    /** Returns the meta-tile-entity ID (material+voltage+insulation key) of a GT cable. */
    public static int getCableMetaTileId(TileEntity te) {
        if (!isCable(te)) return -1;
        try {
            return coverableTileEntityMIdField.getShort(te) & 0xFFFF;
        } catch (Exception e) {
            return -1;
        }
    }

    /**
     * Reads the 6-directional connection byte and returns which sides are
     * connected as an array of {dx, dy, dz} offsets (matching ForgeDirection).
     */
    public static int[][] getConnectedDirections(TileEntity te) {
        if (!isCable(te)) return new int[0][];
        try {
            byte connections = baseMetaPipeMConnectionsField.getByte(te);
            // Count connected sides
            int count = Integer.bitCount(connections & 0x3F);
            int[][] result = new int[count][3];
            int idx = 0;
            // DOWN=0 (0,-1,0), UP=1 (0,1,0), NORTH=2 (0,0,-1), SOUTH=3 (0,0,1),
            // WEST=4 (-1,0,0), EAST=5 (1,0,0)
            int[][] dirMap = { { 0, -1, 0 }, { 0, 1, 0 }, { 0, 0, -1 }, { 0, 0, 1 }, { -1, 0, 0 }, { 1, 0, 0 } };
            for (int i = 0; i < 6; i++) {
                if ((connections & (1 << i)) != 0) {
                    result[idx++] = dirMap[i];
                }
            }
            return result;
        } catch (Exception e) {
            return new int[0][];
        }
    }

    // ── Block detection ──

    /** True if {@code block} is a GT {@code BlockMachines} instance. */
    public static boolean isGTBlock(Block block) {
        if (!isAvailable()) return false;
        try {
            Block machines = (Block) sBlockMachinesField.get(null);
            return machines != null && machines == block;
        } catch (Exception e) {
            return false;
        }
    }

    /** True if the item in {@code stack} is a GT {@code ItemMachines}. */
    public static boolean isGTItem(ItemStack stack) {
        if (stack == null || !isAvailable()) return false;
        try {
            Block machines = (Block) sBlockMachinesField.get(null);
            return machines != null && Block.getBlockFromItem(stack.getItem()) == machines;
        } catch (Exception e) {
            return false;
        }
    }

    // ── Correct placement ──

    /**
     * Returns the correct block metadata for placing a GT block whose item has
     * the given damage value (the meta-tile-entity ID). Returns {@code meta}
     * unchanged if GT is not available.
     */
    public static int getBaseMetaForItem(int itemDamage) {
        if (!isAvailable()) return itemDamage;
        try {
            Object[] entities = (Object[]) metaTileEntitiesField.get(null);
            if (entities == null || itemDamage < 0 || itemDamage >= entities.length) return itemDamage;
            Object prototype = entities[itemDamage];
            if (prototype == null) return itemDamage;
            // IMetaTileEntity.getTileEntityBaseType()
            byte baseType = (byte) prototype.getClass()
                .getMethod("getTileEntityBaseType")
                .invoke(prototype);
            return baseType & 0xFF;
        } catch (Exception e) {
            return itemDamage;
        }
    }

    /**
     * Initialises a freshly-created GT TileEntity with the given item-damage
     * (meta-tile-entity ID). Must be called after {@code world.setBlock()} with
     * the correct base meta.
     */
    public static void initGTMetaTileEntity(World world, int x, int y, int z, int itemDamage) {
        if (!isAvailable()) return;
        TileEntity te = world.getTileEntity(x, y, z);
        if (te == null) return;
        try {
            // CommonBaseMetaTileEntity.setInitialValuesAsNBT(null, (short) itemDamage)
            commonBaseMetaTileEntityClass
                .getMethod("setInitialValuesAsNBT", net.minecraft.nbt.NBTTagCompound.class, short.class)
                .invoke(te, null, (short) itemDamage);
        } catch (Exception e) {
            EZMiner.LOG.debug("GT5BlockSwapCompat: TE init failed — {}", e.getMessage());
        }
    }

    // ── Connection state preservation ──

    /**
     * Saves the cable/pipe connection byte from {@code te} before the block is
     * removed. Returns {@code -1} if the TE is not a pipe/cable.
     */
    public static int saveConnections(TileEntity te) {
        if (te == null || !isAvailable()) return -1;
        if (!baseMetaPipeEntityClass.isInstance(te)) return -1;
        try {
            return baseMetaPipeMConnectionsField.getByte(te) & 0xFF;
        } catch (Exception ignored) {}
        return -1;
    }

    /**
     * Restores the cable/pipe connection byte on the new TE at (x,y,z).
     * Must be called after the new block has been placed and initialised.
     */
    public static void restoreConnections(World world, int x, int y, int z, int savedConnections) {
        if (savedConnections < 0 || !isAvailable()) return;
        TileEntity te = world.getTileEntity(x, y, z);
        if (te == null || !baseMetaPipeEntityClass.isInstance(te)) return;
        try {
            byte val = (byte) savedConnections;
            baseMetaPipeMConnectionsField.setByte(te, val);
            // Also sync to MetaPipeEntity (the logic object)
            Object metaPipe = te.getClass()
                .getMethod("getMetaTileEntity")
                .invoke(te);
            if (metaPipe != null && metaPipeMConnectionsField.getDeclaringClass()
                .isInstance(metaPipe)) {
                metaPipeMConnectionsField.setByte(metaPipe, val);
            }
        } catch (Exception e) {
            EZMiner.LOG.debug("GT5BlockSwapCompat: connection restore failed — {}", e.getMessage());
        }
    }
}
