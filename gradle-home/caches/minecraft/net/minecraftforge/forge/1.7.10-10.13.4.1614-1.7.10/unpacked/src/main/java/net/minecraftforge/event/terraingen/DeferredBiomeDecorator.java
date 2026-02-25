package net.minecraftforge.event.terraingen;

import java.util.Random;

import net.minecraft.world.World;
import net.minecraft.world.biome.BiomeDecorator;
import net.minecraft.world.biome.BiomeGenBase;
import net.minecraftforge.common.MinecraftForge;

public class DeferredBiomeDecorator extends BiomeDecorator {
    private BiomeDecorator wrapped;

    public DeferredBiomeDecorator(BiomeDecorator wrappedOriginal)
    {
        this.wrapped = wrappedOriginal;
    }

    @Override
    public void func_150512_a(World par1World, Random par2Random, BiomeGenBase biome, int par3, int par4)
    {
        fireCreateEventAndReplace(biome);
        // On first call to decorate, we fire and substitute ourselves, if we haven't already done so
        biome.field_76760_I.func_150512_a(par1World, par2Random, biome, par3, par4);
    }
    public void fireCreateEventAndReplace(BiomeGenBase biome)
    {
        // Copy any configuration from us to the real instance.
        wrapped.field_76807_J = field_76807_J;
        wrapped.field_76800_F = field_76800_F;
        wrapped.field_76806_I = field_76806_I;
        wrapped.field_76804_C = field_76804_C;
        wrapped.field_76802_A = field_76802_A;
        wrapped.field_76808_K = field_76808_K;
        wrapped.field_76803_B = field_76803_B;
        wrapped.field_76798_D = field_76798_D;
        wrapped.field_76799_E = field_76799_E;
        wrapped.field_76801_G = field_76801_G;
        wrapped.field_76805_H = field_76805_H;
        wrapped.field_76832_z = field_76832_z;
        wrapped.field_76833_y = field_76833_y;
        
        BiomeEvent.CreateDecorator event = new BiomeEvent.CreateDecorator(biome, wrapped);
        MinecraftForge.TERRAIN_GEN_BUS.post(event);
        biome.field_76760_I = event.newBiomeDecorator;
    }
}
