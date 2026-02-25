package net.minecraftforge.common;

import java.io.File;

import net.minecraft.world.chunk.storage.IChunkLoader;
import net.minecraft.world.storage.IPlayerFileData;
import net.minecraft.world.storage.ISaveHandler;
import net.minecraft.world.MinecraftException;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.storage.WorldInfo;
import net.minecraft.world.WorldProvider;
import net.minecraft.world.WorldServer;

//Class used internally to provide the world specific data directories.

public class WorldSpecificSaveHandler implements ISaveHandler
{
    private WorldServer world;
    private ISaveHandler parent;
    private File dataDir;

    public WorldSpecificSaveHandler(WorldServer world, ISaveHandler parent)
    {
        this.world = world;
        this.parent = parent;
        dataDir = new File(world.getChunkSaveLocation(), "data");
        dataDir.mkdirs();
    }

    @Override public WorldInfo func_75757_d() { return parent.func_75757_d(); }
    @Override public void func_75762_c() throws MinecraftException { parent.func_75762_c(); }
    @Override public IChunkLoader func_75763_a(WorldProvider var1) { return parent.func_75763_a(var1); }
    @Override public void func_75755_a(WorldInfo var1, NBTTagCompound var2) { parent.func_75755_a(var1, var2); }
    @Override public void func_75761_a(WorldInfo var1){ parent.func_75761_a(var1); }
    @Override public IPlayerFileData func_75756_e() { return parent.func_75756_e(); }
    @Override public void func_75759_a() { parent.func_75759_a(); }
    @Override public String func_75760_g() { return parent.func_75760_g(); }
    @Override public File func_75765_b() { return parent.func_75765_b(); }

    @Override
    public File func_75758_b(String name)
    {
        return new File(dataDir, name + ".dat");
    }

}
