package com.czqwq.EZMiner.command;

import java.util.List;

import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.util.ChatComponentTranslation;

import com.czqwq.EZMiner.Config;
import com.czqwq.EZMiner.EZMiner;
import com.czqwq.EZMiner.core.Manager;
import com.czqwq.EZMiner.core.MinerConfig;
import com.czqwq.EZMiner.core.PlayerManager;
import com.czqwq.EZMiner.network.PacketHudPos;
import com.czqwq.EZMiner.network.PacketMinerConfig;
import com.czqwq.EZMiner.network.PacketReloadClientConfig;
import com.czqwq.EZMiner.network.PacketServerConfig;

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
        return "/EZMiner <reloadConfig | reloadClientConfig | active_mode <0|1> | hud pos <x> <y>>";
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
            return getListOfStringsMatchingLastWord(args, "reloadConfig", "reloadClientConfig", "active_mode", "hud");
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("active_mode")) {
            return getListOfStringsMatchingLastWord(args, "0", "1");
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("hud")) {
            return getListOfStringsMatchingLastWord(args, "pos");
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
                sender.addChatMessage(new ChatComponentTranslation("ezminer.command.nopermission"));
                return;
            }
            Config.load();
            if (PlayerManager.instance != null) {
                for (Manager mgr : PlayerManager.instance.managers.values()) {
                    mgr.pConfig = new MinerConfig();
                    EZMiner.network.network.sendTo(new PacketMinerConfig(mgr.pConfig), mgr.player);
                    EZMiner.network.network.sendTo(
                        new PacketServerConfig(
                            Config.bigRadius,
                            Config.blockLimit,
                            Config.smallRadius,
                            Config.tunnelWidth,
                            Config.breakPerTick),
                        mgr.player);
                    EZMiner.network.network.sendTo(new PacketReloadClientConfig(), mgr.player);
                }
            }
            EZMiner.LOG.info("EZMiner config reloaded.");
            sender.addChatMessage(new ChatComponentTranslation("ezminer.command.reloadconfig.success"));
            return;
        }

        // ── reloadClientConfig (any player) ───────────────────────────────────
        if (sub.equalsIgnoreCase("reloadClientConfig")) {
            if (!(sender instanceof net.minecraft.entity.player.EntityPlayerMP)) {
                sender.addChatMessage(new ChatComponentTranslation("ezminer.command.reloadclientconfig.player_only"));
                return;
            }
            net.minecraft.entity.player.EntityPlayerMP player = (net.minecraft.entity.player.EntityPlayerMP) sender;
            EZMiner.network.network.sendTo(
                new PacketServerConfig(
                    Config.bigRadius,
                    Config.blockLimit,
                    Config.smallRadius,
                    Config.tunnelWidth,
                    Config.breakPerTick),
                player);
            EZMiner.network.network.sendTo(new PacketReloadClientConfig(), player);
            sender.addChatMessage(new ChatComponentTranslation("ezminer.command.reloadclientconfig.success"));
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
            sender.addChatMessage(
                new ChatComponentTranslation(
                    "ezminer.command.active_mode.set",
                    mode,
                    new ChatComponentTranslation("ezminer.command.active_mode.desc." + mode)));
            return;
        }

        // ── hud pos <x> <y> ────────────────────────────────────────────────────
        if (sub.equalsIgnoreCase("hud")) {
            if (args.length != 4 || !args[1].equalsIgnoreCase("pos")) {
                sendHudPosUsage(sender);
                return;
            }
            int x, y;
            try {
                x = Integer.parseInt(args[2]);
                y = Integer.parseInt(args[3]);
            } catch (NumberFormatException e) {
                sendHudPosUsage(sender);
                return;
            }
            Config.saveHudPos(x, y);
            sender.addChatMessage(new ChatComponentTranslation("ezminer.command.hud.pos.set", x, y));
            if (sender instanceof net.minecraft.entity.player.EntityPlayerMP) {
                net.minecraft.entity.player.EntityPlayerMP player = (net.minecraft.entity.player.EntityPlayerMP) sender;
                EZMiner.network.network.sendTo(new PacketHudPos(x, y), player);
            }
            return;
        }

        // ── unknown sub-command ───────────────────────────────────────────────
        sendUsage(sender);
    }

    private static void sendUsage(ICommandSender sender) {
        sender.addChatMessage(new ChatComponentTranslation("ezminer.command.usage.reloadconfig"));
        sender.addChatMessage(new ChatComponentTranslation("ezminer.command.usage.reloadclientconfig"));
        sender.addChatMessage(new ChatComponentTranslation("ezminer.command.usage.active_mode"));
        sender.addChatMessage(new ChatComponentTranslation("ezminer.command.usage.hud_pos"));
    }

    private static void sendActiveModeUsage(ICommandSender sender) {
        sender.addChatMessage(new ChatComponentTranslation("ezminer.command.active_mode.usage.0"));
        sender.addChatMessage(new ChatComponentTranslation("ezminer.command.active_mode.usage.1"));
    }

    private static void sendHudPosUsage(ICommandSender sender) {
        sender.addChatMessage(new ChatComponentTranslation("ezminer.command.hud.pos.usage"));
    }
}
