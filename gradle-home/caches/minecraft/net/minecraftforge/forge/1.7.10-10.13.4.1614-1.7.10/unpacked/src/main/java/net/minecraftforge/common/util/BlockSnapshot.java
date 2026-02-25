package net.minecraftforge.common.util;


import java.io.Serializable;

import net.minecraft.block.Block;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraftforge.common.DimensionManager;
import cpw.mods.fml.common.registry.GameRegistry;
import cpw.mods.fml.common.registry.GameRegistry.UniqueIdentifier;

/**
 * Represents a captured snapshot of a block which will not change
 * automatically.
 * <p>
 * Unlike Block, which only one object can exist per coordinate, BlockSnapshot
 * can exist multiple times for any given Block.
 */
@SuppressWarnings("serial")
public class BlockSnapshot implements Serializable
{
    private static final boolean DEBUG = Boolean.parseBoolean(System.getProperty("forge.debugBlockSnapshot", "false"));

    public final int x;
    public final int y;
    public final int z;
    public final int dimId;
    public transient Block replacedBlock;
    public final int meta;
    public int flag;
    private final NBTTagCompound nbt;
    public transient World world;
    public final UniqueIdentifier blockIdentifier;

    public BlockSnapshot(World world, int x, int y, int z, Block block, int meta)
    {
        this.world = world;
        this.dimId = world.field_73011_w.field_76574_g;
        this.x = x;
        this.y = y;
        this.z = z;
        this.replacedBlock = block;
        this.blockIdentifier = GameRegistry.findUniqueIdentifierFor(block);
        this.meta = meta;
        this.flag = 3;
        TileEntity te = world.func_147438_o(x, y, z);
        if (te != null)
        {
            nbt = new NBTTagCompound();
            te.func_145841_b(nbt);
        }
        else nbt = null;
        if (DEBUG)
        {
            System.out.printf("Created BlockSnapshot - [World: %s ][Location: %d,%d,%d ][Block: %s ][Meta: %d ]", world.func_72912_H().func_76065_j(), x, y, z, block, meta);
        }
    }

    public BlockSnapshot(World world, int x, int y, int z, Block block, int meta, NBTTagCompound nbt)
    {
        this.world = world;
        this.dimId = world.field_73011_w.field_76574_g;
        this.x = x;
        this.y = y;
        this.z = z;
        this.replacedBlock = block;
        this.blockIdentifier = GameRegistry.findUniqueIdentifierFor(block);
        this.meta = meta;
        this.flag = 3;
        this.nbt = nbt;
        if (DEBUG)
        {
            System.out.printf("Created BlockSnapshot - [World: %s ][Location: %d,%d,%d ][Block: %s ][Meta: %d ]", world.func_72912_H().func_76065_j(), x, y, z, block, meta);
        }
    }

    public BlockSnapshot(World world, int x, int y, int z, Block block, int meta, int flag) 
    {
        this(world, x, y, z, block, meta);
        this.flag = flag;
    }
    
    /**
     * Raw constructor designed for serialization usages.
     */
    public BlockSnapshot(int dimension, int x, int y, int z, String modid, String blockName, int meta, int flag, NBTTagCompound nbt) 
    {
        this.dimId = dimension;
        this.x = x;
        this.y = y;
        this.z = z;
        this.meta = meta;
        this.flag = flag;
        this.blockIdentifier = new UniqueIdentifier(modid + ":" + blockName);
        this.nbt = nbt;
    }

    public static BlockSnapshot getBlockSnapshot(World world, int x, int y, int z)
    {
        return new BlockSnapshot(world, x, y, z, world.func_147439_a(x, y, z), world.func_72805_g(x, y,z));
    }

    public static BlockSnapshot getBlockSnapshot(World world, int x, int y, int z, int flag)
    {
        return new BlockSnapshot(world, x, y, z, world.func_147439_a(x, y, z), world.func_72805_g(x, y,z), flag);
    }

    public static BlockSnapshot readFromNBT(NBTTagCompound tag)
    {
        NBTTagCompound nbt = tag.func_74767_n("hasTE") ? null : tag.func_74775_l("tileEntity");

        return new BlockSnapshot(
                tag.func_74762_e("dimension"),
                tag.func_74762_e("posX"),
                tag.func_74762_e("posY"),
                tag.func_74762_e("posZ"),
                tag.func_74779_i("blockMod"),
                tag.func_74779_i("blockName"),
                tag.func_74762_e("metadata"),
                tag.func_74762_e("flag"),
                nbt);
    }

    public Block getCurrentBlock() 
    {
        return world.func_147439_a(x, y, z);
    }

    public World getWorld() 
    {
        if (this.world == null) 
        {
            this.world = DimensionManager.getWorld(dimId);
        }
        return this.world;
    }

    public Block getReplacedBlock() 
    {
        if (this.replacedBlock == null) 
        {
            this.replacedBlock = GameRegistry.findBlock(this.blockIdentifier.modId, this.blockIdentifier.name);
        }
        return this.replacedBlock;
    }

    public TileEntity getTileEntity()
    {
        if (nbt != null)
            return TileEntity.func_145827_c(nbt);
        else return null;
    }

    public boolean restore() 
    {
        return restore(false);
    }

    public boolean restore(boolean force) 
    {
        return restore(force, true);
    }

    public boolean restore(boolean force, boolean applyPhysics) 
    {
        if (getCurrentBlock() != getReplacedBlock() || world.func_72805_g(x & 15, y, z & 15) != meta) 
        {
            if (force)
            {
                world.func_147465_d(x, y, z, getReplacedBlock(), meta, applyPhysics ? 3 : 2);
            }
            else 
            {
                return false;
            }
        }

        world.func_72921_c(x, y, z, meta, applyPhysics ? 3 : 2);
        world.func_147471_g(x, y, z);
        TileEntity te = null;
        if (nbt != null)
        {
            te = world.func_147438_o(x, y, z);
            if (te != null)
            {
                te.func_145839_a(nbt);
            }
        }

        if (DEBUG)
        {
            System.out.printf("Restored BlockSnapshot with data [World: %s ][Location: %d,%d,%d ][Meta: %d ][Block: %s ][TileEntity: %s ][force: %s ][applyPhysics: %s]", world.func_72912_H().func_76065_j(), x, y, z, meta, getReplacedBlock(), te, force, applyPhysics);
        }
        return true;
    }

    public boolean restoreToLocation(World world, int x, int y, int z, boolean force, boolean applyPhysics)
    {
        if (getCurrentBlock() != getReplacedBlock() || world.func_72805_g(x & 15, y, z & 15) != meta)
        {
            if (force)
            {
                world.func_147465_d(x, y, z, getReplacedBlock(), meta, applyPhysics ? 3 : 2);
            } 
            else
            {
                return false;
            }
        }

        world.func_72921_c(x, y, z, meta, applyPhysics ? 3 : 2);
        world.func_147471_g(x, y, z);
        TileEntity te = null;
        if (nbt != null)
        {
            te = world.func_147438_o(x, y, z);
            if (te != null)
            {
                te.func_145839_a(nbt);
            }
        }

        if (DEBUG)
        {
            System.out.printf("Restored BlockSnapshot with data [World: %s ][Location: %d,%d,%d ][Meta: %d ][Block: %s ][TileEntity: %s ][force: %s ][applyPhysics: %s]", world.func_72912_H().func_76065_j(), x, y, z, meta, getReplacedBlock(), te, force, applyPhysics);
        }
        return true;
    }

    public void writeToNBT(NBTTagCompound compound)
    {
        compound.func_74778_a("blockMod", blockIdentifier.modId);
        compound.func_74778_a("blockName", blockIdentifier.name);
        compound.func_74768_a("posX", x);
        compound.func_74768_a("posY", y);
        compound.func_74768_a("posZ", z);
        compound.func_74768_a("flag", flag);
        compound.func_74768_a("dimension", dimId);
        compound.func_74768_a("metadata", meta);

        compound.func_74757_a("hasTE", nbt != null);

        if (nbt != null)
        {
            compound.func_74782_a("tileEntity", nbt);
        }
    }

    @Override
    public boolean equals(Object obj) 
    {
        if (obj == null)
        {
            return false;
        }
        if (getClass() != obj.getClass())
        {
            return false;
        }
        final BlockSnapshot other = (BlockSnapshot) obj;
        if (this.x != other.x)
        {
            return false;
        }
        if (this.y != other.y)
        {
            return false;
        }
        if (this.z != other.z) 
        {
            return false;
        }
        if (this.meta != other.meta)
        {
            return false;
        }
        if (this.dimId != other.dimId)
        {
            return false;
        }
        if (this.nbt != other.nbt && (this.nbt == null || !this.nbt.equals(other.nbt)))
        {
            return false;
        }
        if (this.world != other.world && (this.world == null || !this.world.equals(other.world)))
        {
            return false;
        }
        if (this.blockIdentifier != other.blockIdentifier && (this.blockIdentifier == null || !this.blockIdentifier.equals(other.blockIdentifier)))
        {
            return false;
        }
        return true;
    }

    @Override
    public int hashCode()
    {
        int hash = 7;
        hash = 73 * hash + this.x;
        hash = 73 * hash + this.y;
        hash = 73 * hash + this.z;
        hash = 73 * hash + this.meta;
        hash = 73 * hash + this.dimId;
        hash = 73 * hash + (this.nbt != null ? this.nbt.hashCode() : 0);
        hash = 73 * hash + (this.world != null ? this.world.hashCode() : 0);
        hash = 73 * hash + (this.blockIdentifier != null ? this.blockIdentifier.hashCode() : 0);
        return hash;
    }
}