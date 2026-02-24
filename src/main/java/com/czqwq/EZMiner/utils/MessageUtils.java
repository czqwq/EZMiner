package com.czqwq.EZMiner.utils;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.ChatComponentText;

import cpw.mods.fml.common.FMLCommonHandler;

public class MessageUtils {

    public static void printSelfMessage(String content) {
        Minecraft.getMinecraft().thePlayer.addChatMessage(new ChatComponentText(content));
    }

    public static void sendPlayerMessage(String content, EntityPlayer player) {
        player.addChatMessage(new ChatComponentText(content));
    }

    public static void serverSendPlayerMessage(String content, UUID playerUUID) {
        List<EntityPlayer> players = FMLCommonHandler.instance()
            .getMinecraftServerInstance()
            .getEntityWorld().playerEntities;
        for (EntityPlayer player : new ArrayList<>(players)) {
            if (player.getUniqueID()
                .equals(playerUUID)) {
                player.addChatMessage(new ChatComponentText(content));
                return;
            }
        }
    }
}
