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
import com.czqwq.EZMiner.permission.OpPermissionChecker;
import com.czqwq.EZMiner.permission.ServerOwnerWhitelist;

@SuppressWarnings("unchecked")
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

    /**
     * Allows level-0 sub-commands (hud, active_mode) even when the player cannot send commands
     * (e.g. single-player no-cheats). OP-gated sub-commands ({@code reloadConfig},
     * {@code addserverowner}, etc.) still check internally via {@link OpPermissionChecker}.
     */
    @Override
    public boolean canCommandSenderUseCommand(ICommandSender sender) {
        // Non-players (console, RCon) are always allowed
        if (!(sender instanceof net.minecraft.entity.player.EntityPlayerMP)) return true;
        net.minecraft.entity.player.EntityPlayerMP player = (net.minecraft.entity.player.EntityPlayerMP) sender;
        // Standard check: player has command access AND sufficient permission level
        if (player.canCommandSenderUseCommand(getRequiredPermissionLevel(), getCommandName())) {
            return true;
        }
        // Fallback: player has no command access, but level-0 sub-commands (hud,
        // active_mode) don't need OP. OP-gated sub-commands do their own checks
        // in processCommand.
        return getRequiredPermissionLevel() <= 0;
    }

    @Override
    public List addTabCompletionOptions(ICommandSender sender, String[] args) {
        if (args.length == 1) {
            return getListOfStringsMatchingLastWord(
                args,
                "reloadConfig",
                "reloadClientConfig",
                "active_mode",
                "hud",
                "addserverowner",
                "removeserverowner",
                "listserverowner");
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
            // Non-player senders (console, RCON) are implicitly trusted.
            // Player senders must pass the stricter OpPermissionChecker (excludes LAN guests).
            if (sender instanceof net.minecraft.entity.player.EntityPlayerMP
                && !OpPermissionChecker.isOp((net.minecraft.entity.player.EntityPlayerMP) sender)) {
                sender.addChatMessage(new ChatComponentTranslation("ezminer.command.nopermission"));
                return;
            }
            Config.load();
            if (PlayerManager.instance != null) {
                for (Manager mgr : PlayerManager.instance.managers.values()) {
                    mgr.pConfig.updateFrom(new MinerConfig());
                    EZMiner.network.network.sendTo(new PacketMinerConfig(mgr.pConfig), mgr.player);
                    EZMiner.network.network.sendTo(PacketServerConfig.buildForPlayer(mgr.player), mgr.player);
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
            EZMiner.network.network.sendTo(PacketServerConfig.buildForPlayer(player), player);
            EZMiner.network.network.sendTo(new PacketReloadClientConfig(), player);
            sender.addChatMessage(new ChatComponentTranslation("ezminer.command.reloadclientconfig.success"));
            return;
        }

        // ── active_mode <0|1> ─────────────────────────────────────────────────
        if (sub.equalsIgnoreCase("active_mode")) {
            if (!this.canCommandSenderUseCommand(sender)) {
                sender.addChatMessage(new ChatComponentTranslation("ezminer.command.nopermission"));
                return;
            }
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
            if (!this.canCommandSenderUseCommand(sender)) {
                sender.addChatMessage(new ChatComponentTranslation("ezminer.command.nopermission"));
                return;
            }
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

        // ── addserverowner <player> (console / single-player owner only) ──────
        if (sub.equalsIgnoreCase("addserverowner")) {
            if (args.length < 2) {
                sender.addChatMessage(new ChatComponentTranslation("ezminer.command.addserverowner.usage"));
                return;
            }
            if (!canManageWhitelist(sender)) {
                sender.addChatMessage(new ChatComponentTranslation("ezminer.command.whitelist.nopermission"));
                return;
            }
            String name = args[1];
            if (ServerOwnerWhitelist.contains(name)) {
                sender.addChatMessage(new ChatComponentTranslation("ezminer.command.addserverowner.already", name));
                return;
            }
            ServerOwnerWhitelist.add(name);
            sender.addChatMessage(new ChatComponentTranslation("ezminer.command.addserverowner.success", name));
            EZMiner.LOG.info("EZMiner server owner whitelist: added {} by {}.", name, sender.getCommandSenderName());
            broadcastUpdatedOpStatus();
            return;
        }

        // ── removeserverowner <player> (console / single-player owner only) ────
        if (sub.equalsIgnoreCase("removeserverowner")) {
            if (args.length < 2) {
                sender.addChatMessage(new ChatComponentTranslation("ezminer.command.removeserverowner.usage"));
                return;
            }
            if (!canManageWhitelist(sender)) {
                sender.addChatMessage(new ChatComponentTranslation("ezminer.command.whitelist.nopermission"));
                return;
            }
            String name = args[1];
            if (!ServerOwnerWhitelist.contains(name)) {
                sender.addChatMessage(new ChatComponentTranslation("ezminer.command.removeserverowner.notfound", name));
                return;
            }
            ServerOwnerWhitelist.remove(name);
            sender.addChatMessage(new ChatComponentTranslation("ezminer.command.removeserverowner.success", name));
            EZMiner.LOG.info("EZMiner server owner whitelist: removed {} by {}.", name, sender.getCommandSenderName());
            broadcastUpdatedOpStatus();
            return;
        }

        // ── listserverowner (anyone) ─────────────────────────────────────────────
        if (sub.equalsIgnoreCase("listserverowner")) {
            java.util.Set<String> set = ServerOwnerWhitelist.getAll();
            if (set.isEmpty()) {
                sender.addChatMessage(new ChatComponentTranslation("ezminer.command.listserverowner.empty"));
            } else {
                sender
                    .addChatMessage(new ChatComponentTranslation("ezminer.command.listserverowner.header", set.size()));
                for (String name : set) {
                    sender.addChatMessage(new net.minecraft.util.ChatComponentText("  §e" + name));
                }
            }
            return;
        }

        // ── unknown sub-command ───────────────────────────────────────────────
        sendUsage(sender);
    }

    /**
     * Returns {@code true} when {@code sender} is allowed to manage the server-owner whitelist.
     * Non-player senders (console, RCON) are always allowed. On an integrated server the host is
     * allowed. On a dedicated server players are never allowed — only the console.
     */
    private static boolean canManageWhitelist(ICommandSender sender) {
        if (!(sender instanceof net.minecraft.entity.player.EntityPlayerMP)) return true;
        net.minecraft.entity.player.EntityPlayerMP player = (net.minecraft.entity.player.EntityPlayerMP) sender;
        return player.mcServer.isSinglePlayer() && player.mcServer.getServerOwner()
            .equalsIgnoreCase(
                player.getGameProfile()
                    .getName());
    }

    /** Re-sends fresh OP-status packets to all online players so their GUIs reflect whitelist changes. */
    private static void broadcastUpdatedOpStatus() {
        if (PlayerManager.instance == null) return;
        for (Manager mgr : PlayerManager.instance.managers.values()) {
            EZMiner.network.network.sendTo(PacketServerConfig.buildForPlayer(mgr.player), mgr.player);
        }
    }

    private static void sendUsage(ICommandSender sender) {
        sender.addChatMessage(new ChatComponentTranslation("ezminer.command.usage.reloadconfig"));
        sender.addChatMessage(new ChatComponentTranslation("ezminer.command.usage.reloadclientconfig"));
        sender.addChatMessage(new ChatComponentTranslation("ezminer.command.usage.active_mode"));
        sender.addChatMessage(new ChatComponentTranslation("ezminer.command.usage.hud_pos"));
        sender.addChatMessage(new ChatComponentTranslation("ezminer.command.addserverowner.usage"));
        sender.addChatMessage(new ChatComponentTranslation("ezminer.command.removeserverowner.usage"));
        sender.addChatMessage(new ChatComponentTranslation("ezminer.command.listserverowner.usage"));
    }

    private static void sendActiveModeUsage(ICommandSender sender) {
        sender.addChatMessage(new ChatComponentTranslation("ezminer.command.active_mode.usage.0"));
        sender.addChatMessage(new ChatComponentTranslation("ezminer.command.active_mode.usage.1"));
    }

    private static void sendHudPosUsage(ICommandSender sender) {
        sender.addChatMessage(new ChatComponentTranslation("ezminer.command.hud.pos.usage"));
    }
}
