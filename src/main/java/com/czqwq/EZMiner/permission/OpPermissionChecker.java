package com.czqwq.EZMiner.permission;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.management.UserListOpsEntry;

/**
 * Centralized OP permission check for EZMiner server-config access.
 *
 * <p>
 * Replaces the scattered {@code player.canCommandSenderUseCommand(2, "EZMiner")} calls with a
 * single, well-defined policy that correctly excludes LAN guests and supports a whitelist bypass.
 */
public final class OpPermissionChecker {

    private OpPermissionChecker() {}

    /**
     * Returns {@code true} when {@code player} is allowed to view or modify EZMiner server settings.
     *
     * <p>
     * OP is granted when <strong>any</strong> of the following hold:
     * <ol>
     * <li>The player is explicitly in the server ops list with level ≥ 2.</li>
     * <li>On an integrated (single-player) server: the player <em>is</em> the server owner and
     * commands are currently enabled (world cheats or LAN cheats). LAN guests are
     * <strong>excluded</strong> — {@code commandsAllowedForAll} is only honored for the verified
     * server owner.</li>
     * <li>The player's name appears on the per-world {@link ServerOwnerWhitelist}
     * (checked case-insensitively).</li>
     * </ol>
     */
    public static boolean isOp(EntityPlayerMP player) {
        // 1. Explicit ops list entry with level >= 2 (works for both dedicated & integrated)
        UserListOpsEntry entry = (UserListOpsEntry) player.mcServer.getConfigurationManager()
            .func_152603_m() // getOppedPlayers()
            .func_152683_b(player.getGameProfile()); // getEntry()
        if (entry != null && entry.func_152644_a() >= 2) { // getPermissionLevel()
            return true;
        }

        // 2. Integrated server: the owner gets OP status when commands are enabled
        // (world cheats or LAN cheats). If commands are not enabled, fall through to
        // the whitelist check so /ezminer addserverowner still works for the owner.
        if (player.mcServer.isSinglePlayer() && player.mcServer.getServerOwner()
            .equalsIgnoreCase(
                player.getGameProfile()
                    .getName())) {
            if (player.mcServer.getConfigurationManager()
                .func_152596_g(player.getGameProfile())) { // canSendCommands
                return true;
            }
            // Fall through — owner without cheats may still be on the whitelist.
        }

        // 3. Per-world server owner whitelist bypass (case-insensitive)
        if (ServerOwnerWhitelist.contains(
            player.getGameProfile()
                .getName())) {
            return true;
        }

        return false;
    }
}
