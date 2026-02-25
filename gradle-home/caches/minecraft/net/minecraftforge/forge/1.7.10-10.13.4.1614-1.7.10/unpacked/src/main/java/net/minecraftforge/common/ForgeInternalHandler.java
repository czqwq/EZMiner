package net.minecraftforge.common;

import cpw.mods.fml.common.FMLLog;
import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.event.entity.*;
import net.minecraftforge.event.world.WorldEvent;

public class ForgeInternalHandler
{
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onEntityJoinWorld(EntityJoinWorldEvent event)
    {
        if (!event.world.field_72995_K)
        {
            ForgeChunkManager.loadEntity(event.entity);
        }

        Entity entity = event.entity;
        if (entity.getClass().equals(EntityItem.class))
        {
            ItemStack stack = entity.func_70096_w().func_82710_f(10);

            if (stack == null)
            {
                //entity.setDead();
                //event.setCanceled(true);
                return;
            }

            Item item = stack.func_77973_b();
            if (item == null)
            {
                FMLLog.warning("Attempted to add a EntityItem to the world with a invalid item at " +
                    "(%2.2f,  %2.2f, %2.2f), this is most likely a config issue between you and the server. Please double check your configs",
                    entity.field_70165_t, entity.field_70163_u, entity.field_70161_v);
                entity.func_70106_y();
                event.setCanceled(true);
                return;
            }

            if (item.hasCustomEntity(stack))
            {
                Entity newEntity = item.createEntity(event.world, entity, stack);
                if (newEntity != null)
                {
                    entity.func_70106_y();
                    event.setCanceled(true);
                    event.world.func_72838_d(newEntity);
                }
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onDimensionLoad(WorldEvent.Load event)
    {
        ForgeChunkManager.loadWorld(event.world);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onDimensionSave(WorldEvent.Save event)
    {
    	ForgeChunkManager.saveWorld(event.world);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onDimensionUnload(WorldEvent.Unload event)
    {
        ForgeChunkManager.unloadWorld(event.world);
        if (event.world instanceof WorldServer)
            FakePlayerFactory.unloadWorld((WorldServer)event.world);
    }
}
