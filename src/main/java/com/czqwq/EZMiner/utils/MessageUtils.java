package com.czqwq.EZMiner.utils;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.IChatComponent;

import cpw.mods.fml.common.FMLCommonHandler;

public class MessageUtils {

    public static void printSelfMessage(String content) {
        Minecraft.getMinecraft().thePlayer.addChatMessage(new ChatComponentText(content));
    }

    public static void sendPlayerMessage(String content, EntityPlayer player) {
        player.addChatMessage(new ChatComponentText(content));
    }

    public static void serverSendPlayerMessage(String content, UUID playerUUID) {
        serverSendPlayerMessage(new ChatComponentText(content), playerUUID);
    }

    /** Sends a pre-built {@link IChatComponent} (e.g. a translatable component) to the player. */
    public static void serverSendPlayerMessage(IChatComponent component, UUID playerUUID) {
        List<EntityPlayer> players = FMLCommonHandler.instance()
            .getMinecraftServerInstance()
            .getEntityWorld().playerEntities;
        for (EntityPlayer player : new ArrayList<>(players)) {
            if (player.getUniqueID()
                .equals(playerUUID)) {
                player.addChatMessage(component);
                return;
            }
        }
    }
}
