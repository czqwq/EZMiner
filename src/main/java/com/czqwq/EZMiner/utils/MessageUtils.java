package com.czqwq.EZMiner.utils;

import java.util.ArrayList;
import java.util.UUID;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.management.ServerConfigurationManager;
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

    /**
     * Sends a pre-built {@link IChatComponent} (e.g. a translatable component) to the player.
     *
     * <p>
     * Resolves the target through the server-wide player list
     * ({@link ServerConfigurationManager#playerEntityList}), not through a single world's
     * {@code playerEntities}. In 1.7.10 {@link MinecraftServer#getEntityWorld()} returns the
     * <b>Overworld</b> ({@code worldServers[0]}) and {@code World.playerEntities} only holds the
     * players currently in that dimension, so resolving through a world silently dropped every
     * message for a player in the Nether, the End or any modded dimension (issue #74: the chain
     * completion summary was only ever received in the Overworld).
     */
    public static void serverSendPlayerMessage(IChatComponent component, UUID playerUUID) {
        if (component == null || playerUUID == null) return;
        MinecraftServer server = FMLCommonHandler.instance()
            .getMinecraftServerInstance();
        if (server == null) return;
        ServerConfigurationManager playerList = server.getConfigurationManager();
        if (playerList == null) return;
        // Copy the list: the server list is shared state, and addChatMessage can run listeners.
        for (EntityPlayerMP player : new ArrayList<>(playerList.playerEntityList)) {
            if (playerUUID.equals(player.getUniqueID())) {
                player.addChatMessage(component);
                return;
            }
        }
    }
}
