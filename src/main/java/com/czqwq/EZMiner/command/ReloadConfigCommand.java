package com.czqwq.EZMiner.command;

import java.util.Collections;
import java.util.List;

import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.util.ChatComponentText;

import com.czqwq.EZMiner.Config;
import com.czqwq.EZMiner.EZMiner;
import com.czqwq.EZMiner.core.Manager;
import com.czqwq.EZMiner.core.MinerConfig;
import com.czqwq.EZMiner.core.PlayerManager;
import com.czqwq.EZMiner.network.PacketMinerConfig;

/**
 * /EZMiner reloadConfig – hot-reloads the EZMiner configuration file.
 */
public class ReloadConfigCommand extends CommandBase {

    @Override
    public String getCommandName() {
        return "EZMiner";
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "/EZMiner reloadConfig";
    }

    @Override
    public List addTabCompletionOptions(ICommandSender sender, String[] args) {
        if (args.length == 1) return getListOfStringsMatchingLastWord(args, "reloadConfig");
        return Collections.emptyList();
    }

    @Override
    public void processCommand(ICommandSender sender, String[] args) {
        if (args.length == 1 && args[0].equalsIgnoreCase("reloadConfig")) {
            Config.load();
            // Sync the updated server config limits to all online players.
            // In single-player the client and server share the same config file, so Config.load()
            // already updated Config.* in the same JVM. In multiplayer the server config file is
            // separate from each client's file, so we must push the new limits via packet.
            // Either way we reset pConfig to the freshly-loaded defaults so that increased limits
            // also take effect immediately (receiveClientConfig only caps downward).
            if (PlayerManager.instance != null) {
                for (Manager mgr : PlayerManager.instance.managers.values()) {
                    mgr.pConfig = new MinerConfig();
                    EZMiner.network.network.sendTo(new PacketMinerConfig(mgr.pConfig), mgr.player);
                }
            }
            EZMiner.LOG.info("EZMiner config reloaded.");
            sender.addChatMessage(new ChatComponentText("EZMiner config reloaded successfully."));
        } else {
            sender.addChatMessage(new ChatComponentText(getCommandUsage(sender)));
        }
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 2;
    }
}
