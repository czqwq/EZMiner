package com.czqwq.EZMiner.command;

import java.util.List;

import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumChatFormatting;

import com.czqwq.EZMiner.Config;
import com.czqwq.EZMiner.EZMiner;
import com.czqwq.EZMiner.core.Manager;
import com.czqwq.EZMiner.core.MinerConfig;
import com.czqwq.EZMiner.core.PlayerManager;
import com.czqwq.EZMiner.network.PacketMinerConfig;

/**
 * Root {@code /EZMiner} command with two sub-commands:
 * <ul>
 * <li>{@code reloadConfig} – hot-reloads the config file (OP only).</li>
 * <li>{@code active_mode <0|1>} – switches the chain-key activation mode
 * (any player; change is persisted to the config file).</li>
 * </ul>
 */
public class ReloadConfigCommand extends CommandBase {

    @Override
    public String getCommandName() {
        return "EZMiner";
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "/EZMiner <reloadConfig | active_mode <0|1>>";
    }

    /** Allow all players to run /EZMiner (active_mode is a personal setting). */
    @Override
    public int getRequiredPermissionLevel() {
        return 0;
    }

    @Override
    @SuppressWarnings("unchecked")
    public List addTabCompletionOptions(ICommandSender sender, String[] args) {
        if (args.length == 1) {
            return getListOfStringsMatchingLastWord(args, "reloadConfig", "active_mode");
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("active_mode")) {
            return getListOfStringsMatchingLastWord(args, "0", "1");
        }
        return java.util.Collections.emptyList();
    }

    @Override
    public void processCommand(ICommandSender sender, String[] args) {
        if (args.length == 0) {
            sendUsage(sender);
            return;
        }

        String sub = args[0];

        // ── reloadConfig (OP only) ────────────────────────────────────────────
        if (sub.equalsIgnoreCase("reloadConfig")) {
            if (!sender.canCommandSenderUseCommand(2, getCommandName())) {
                sender.addChatMessage(
                    new ChatComponentText(EnumChatFormatting.RED + "You need OP permission to reload the config."));
                return;
            }
            Config.load();
            if (PlayerManager.instance != null) {
                for (Manager mgr : PlayerManager.instance.managers.values()) {
                    mgr.pConfig = new MinerConfig();
                    EZMiner.network.network.sendTo(new PacketMinerConfig(mgr.pConfig), mgr.player);
                }
            }
            EZMiner.LOG.info("EZMiner config reloaded.");
            sender.addChatMessage(new ChatComponentText("EZMiner config reloaded successfully."));
            return;
        }

        // ── active_mode <0|1> ─────────────────────────────────────────────────
        if (sub.equalsIgnoreCase("active_mode")) {
            if (args.length < 2) {
                sendActiveModeUsage(sender);
                return;
            }
            int mode;
            try {
                mode = Integer.parseInt(args[1]);
            } catch (NumberFormatException e) {
                sendActiveModeUsage(sender);
                return;
            }
            if (mode != 0 && mode != 1) {
                sendActiveModeUsage(sender);
                return;
            }
            Config.saveChainActivationMode(mode);
            String desc = mode == 0 ? "Hold to activate" : "Click to toggle";
            sender.addChatMessage(
                new ChatComponentText(
                    "EZMiner: chain activation mode set to " + EnumChatFormatting.YELLOW
                        + mode
                        + EnumChatFormatting.RESET
                        + " ("
                        + desc
                        + "). Saved to config."));
            return;
        }

        // ── unknown sub-command ───────────────────────────────────────────────
        sendUsage(sender);
    }

    private static void sendUsage(ICommandSender sender) {
        sender.addChatMessage(
            new ChatComponentText(
                EnumChatFormatting.YELLOW + "/EZMiner reloadConfig"
                    + EnumChatFormatting.RESET
                    + " – reload config from disk (OP only)"));
        sender.addChatMessage(
            new ChatComponentText(
                EnumChatFormatting.YELLOW + "/EZMiner active_mode <0|1>"
                    + EnumChatFormatting.RESET
                    + " – set chain key activation mode"));
    }

    private static void sendActiveModeUsage(ICommandSender sender) {
        sender.addChatMessage(
            new ChatComponentText(
                EnumChatFormatting.YELLOW + "/EZMiner active_mode 0"
                    + EnumChatFormatting.RESET
                    + " – Hold: keep the chain key held to mine, release to stop"));
        sender.addChatMessage(
            new ChatComponentText(
                EnumChatFormatting.YELLOW + "/EZMiner active_mode 1"
                    + EnumChatFormatting.RESET
                    + " – Toggle: press once to start mining, press again to stop"));
    }
}
