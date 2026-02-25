package net.minecraftforge.common.util;

import com.mojang.authlib.GameProfile;

import cpw.mods.fml.common.FMLCommonHandler;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.play.client.C15PacketClientSettings;
import net.minecraft.server.management.ItemInWorldManager;
import net.minecraft.stats.StatBase;
import net.minecraft.util.ChunkCoordinates;
import net.minecraft.util.DamageSource;
import net.minecraft.util.IChatComponent;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;

//Preliminary, simple Fake Player class 
public class FakePlayer extends EntityPlayerMP
{
    public FakePlayer(WorldServer world, GameProfile name)
    {
        super(FMLCommonHandler.instance().getMinecraftServerInstance(), world, name, new ItemInWorldManager(world));
    }

    @Override public boolean func_70003_b(int i, String s){ return false; }
    @Override public ChunkCoordinates func_82114_b()
    {
        return new ChunkCoordinates(0,0,0);
    }

    @Override public void func_146105_b(IChatComponent chatmessagecomponent){}
    @Override public void func_71064_a(StatBase par1StatBase, int par2){}
    @Override public void openGui(Object mod, int modGuiId, World world, int x, int y, int z){}
    @Override public boolean func_85032_ar(){ return true; }
    @Override public boolean func_96122_a(EntityPlayer player){ return false; }
    @Override public void func_70645_a(DamageSource source){ return; }
    @Override public void func_70071_h_(){ return; }
    @Override public void func_71027_c(int dim){ return; }
    @Override public void func_147100_a(C15PacketClientSettings pkt){ return; }
}
