package com.czqwq.EZMiner.client;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;

import com.czqwq.EZMiner.compat.GT5ToolCompat;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Clears GT toolbox selections that would crash GT's item renderer (non-tool, empty or invalid selected slot),
 * covering toolbox NBT already corrupted by the pre-fix toolbox bridge in existing saves. Runs on client tick START,
 * before the world is rendered.
 */
@SideOnly(Side.CLIENT)
public class ToolboxSelectionSanitizer {

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;
        Minecraft mc = Minecraft.getMinecraft();
        EntityPlayer player = mc.thePlayer;
        if (player == null || player.inventory == null) return;
        ItemStack[] inventory = player.inventory.mainInventory;
        for (int slot = 0; slot < inventory.length; slot++) {
            ItemStack stack = inventory[slot];
            if (stack != null && GT5ToolCompat.isGTToolbox(stack)) {
                GT5ToolCompat.repairInvalidToolboxSelection(slot, stack);
            }
        }
    }

    public void registry() {
        FMLCommonHandler.instance()
            .bus()
            .register(this);
    }
}
