package com.czqwq.EZMiner.chain.execution;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.entity.player.EntityPlayerMP;

import com.czqwq.EZMiner.Config;

/**
 * Tracks the cooldown between successive chain-mining operations per player.
 *
 * <p>
 * Pattern follows FTB Ultimine's {@code CooldownTracker}: each time a chain
 * operation completes, the current timestamp (epoch ms) is recorded for the
 * player. Before a new chain can start, the elapsed time since the last
 * recorded timestamp is compared against the configured cooldown duration.
 * Creative-mode players are always exempt.
 *
 * <p>
 * Cooldown is measured in <strong>ticks</strong> (20 ticks = 1 second) and
 * configured via {@link Config#chainCooldownTicks}. A value of 0 disables
 * the cooldown entirely.
 */
public class CooldownTracker {

    private static final Map<UUID, Long> lastUseTime = new HashMap<>();

    /**
     * Returns {@code true} when the player is still within the cooldown window
     * and should not be allowed to start a new chain.
     */
    public static boolean isOnCooldown(EntityPlayerMP player) {
        if (player.capabilities.isCreativeMode) return false;
        int cooldownTicks = Config.chainCooldownTicks;
        if (cooldownTicks <= 0) return false;
        long cooldownMs = cooldownTicks * 50L;
        long lastUse = lastUseTime.getOrDefault(player.getUniqueID(), 0L);
        return System.currentTimeMillis() - lastUse < cooldownMs;
    }

    /**
     * Returns the remaining cooldown as a fraction from 0.0 (just started) to
     * 1.0 (fully expired / ready to use). Used for client-side HUD display.
     */
    public static float getCooldownRemaining(EntityPlayerMP player) {
        int cooldownTicks = Config.chainCooldownTicks;
        if (cooldownTicks <= 0) return 1.0f;
        long cooldownMs = cooldownTicks * 50L;
        long lastUse = lastUseTime.getOrDefault(player.getUniqueID(), 0L);
        if (lastUse == 0L) return 1.0f;
        long elapsed = System.currentTimeMillis() - lastUse;
        if (elapsed >= cooldownMs) return 1.0f;
        return (float) elapsed / (float) cooldownMs;
    }

    /**
     * Records the current time as the last-use timestamp for {@code player}.
     * Call this when a chain operation completes (in {@code unRegistry}).
     * No-op for creative players or when the cooldown is disabled.
     */
    public static void recordUse(EntityPlayerMP player) {
        if (player.capabilities.isCreativeMode) return;
        if (Config.chainCooldownTicks <= 0) return;
        lastUseTime.put(player.getUniqueID(), System.currentTimeMillis());
    }

    /** Removes the cooldown record for a player (called on logout). */
    public static void clear(UUID playerUUID) {
        lastUseTime.remove(playerUUID);
    }
}
