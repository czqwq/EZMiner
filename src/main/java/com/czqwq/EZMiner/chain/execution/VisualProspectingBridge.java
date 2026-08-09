package com.czqwq.EZMiner.chain.execution;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import net.minecraft.entity.player.EntityPlayerMP;

import org.joml.Vector3i;

import com.czqwq.EZMiner.EZMiner;
import com.czqwq.EZMiner.core.founder.DeterminingIdentical;

/**
 * Optional Visual Prospecting integration bridge.
 *
 * <p>
 * VisualProspecting is an optional runtime-only dependency, so every interaction
 * goes through reflection. The compatibility probe is shared statically — the
 * class-path lookup is deterministic and must only run once per JVM, also for the
 * mode-visibility gate in {@code MinerModeState}.
 */
public class VisualProspectingBridge {

    private static volatile boolean compatibilityChecked = false;
    private static volatile boolean hasVpApi = false;
    private static volatile Method vpProspectMethod = null;
    private static volatile Method vpSendToClientMethod = null;
    private static volatile Method vpGetBlockXMethod = null;
    private static volatile Method vpGetBlockZMethod = null;
    private static volatile Field vpVeinTypeField = null;
    private static volatile Method vpGetVeinNameMethod = null;

    public static synchronized void checkCompatibility() {
        if (compatibilityChecked) return;
        try {
            Class<?> logicalServerClass = Class
                .forName("com.sinthoras.visualprospecting.VisualProspecting_API$LogicalServer");
            vpProspectMethod = logicalServerClass
                .getMethod("prospectOreVeinsWithinRadius", int.class, int.class, int.class, int.class);
            vpSendToClientMethod = logicalServerClass
                .getMethod("sendProspectionResultsToClient", EntityPlayerMP.class, List.class, List.class);
            Class<?> ovpClass = Class.forName("com.sinthoras.visualprospecting.database.OreVeinPosition");
            vpGetBlockXMethod = ovpClass.getMethod("getBlockX");
            vpGetBlockZMethod = ovpClass.getMethod("getBlockZ");
            vpVeinTypeField = ovpClass.getField("veinType");
            Class<?> veinTypeClass = Class.forName("com.sinthoras.visualprospecting.database.veintypes.VeinType");
            vpGetVeinNameMethod = veinTypeClass.getMethod("getVeinName");
            hasVpApi = true;
            EZMiner.LOG.info("EZMiner: VisualProspecting_API detected – ore vein discovery enabled.");
        } catch (ClassNotFoundException e) {
            EZMiner.LOG.debug("EZMiner: VisualProspecting_API not found – ore vein discovery disabled.");
        } catch (NoSuchMethodException | NoSuchFieldException | SecurityException e) {
            EZMiner.LOG.warn(
                "EZMiner: VisualProspecting_API found but required methods could not be resolved: {}",
                e.getMessage());
        }
        compatibilityChecked = true;
    }

    /** True when the VP API has been resolved on this side (lazy, cached). */
    public static boolean isVpAvailable() {
        if (!compatibilityChecked) checkCompatibility();
        return hasVpApi;
    }

    public void notifyOreDiscovery(EntityPlayerMP player, Vector3i pos, Set<Long> notifiedChunks) {
        if (!compatibilityChecked) checkCompatibility();
        if (!hasVpApi || vpProspectMethod == null || vpSendToClientMethod == null) return;
        if (!DeterminingIdentical.isGTOreBlock(pos, player)) return;

        long chunkKey = ((long) (pos.x >> 4) << 32) | ((pos.z >> 4) & 0xFFFFFFFFL);
        if (!notifiedChunks.add(chunkKey)) return;

        try {
            List<?> veins = (List<?>) vpProspectMethod.invoke(null, player.dimension, pos.x, pos.z, 0);
            if (!veins.isEmpty()) {
                vpSendToClientMethod.invoke(null, player, veins, Collections.emptyList());
            }
        } catch (Exception e) {
            EZMiner.LOG.debug("EZMiner: VP ore vein discovery call failed at {}: {}", pos, e.getMessage());
        }
    }

    /**
     * Probes the single chunk containing block ({@code blockX}, {@code blockZ})
     * with radius 0 (ServerCache already filters empty veins, so a non-empty
     * result means a vein is centered at that chunk). On a first-time discovery
     * the results are sent to the client — VP adds its waypoints — and a
     * {@link VeinHit} is returned for a chat notification; {@code null} when
     * nothing was found or the vein was already notified before.
     */
    public VeinHit prospectChunkAndNotify(EntityPlayerMP player, int blockX, int blockZ, Set<Long> notifiedVeins) {
        if (!isVpAvailable() || vpProspectMethod == null || vpSendToClientMethod == null) return null;
        try {
            List<?> veins = (List<?>) vpProspectMethod.invoke(null, player.dimension, blockX, blockZ, 0);
            if (veins.isEmpty()) return null;
            Object first = veins.get(0);
            int vBlockX = ((Number) vpGetBlockXMethod.invoke(first)).intValue();
            int vBlockZ = ((Number) vpGetBlockZMethod.invoke(first)).intValue();
            long veinKey = ((long) (vBlockX >> 4) << 32) | ((vBlockZ >> 4) & 0xFFFFFFFFL);
            if (!notifiedVeins.add(veinKey)) return null;
            vpSendToClientMethod.invoke(null, player, veins, Collections.emptyList());
            Object veinType = vpVeinTypeField.get(first);
            String veinName = (String) vpGetVeinNameMethod.invoke(veinType);
            return new VeinHit(veinName, vBlockX, vBlockZ);
        } catch (Exception e) {
            EZMiner.LOG.debug("EZMiner: VP chunk prospect call failed at {}, {}: {}", blockX, blockZ, e.getMessage());
            return null;
        }
    }

    /** Result of a first-time vein discovery: localized name and center block coords. */
    public static final class VeinHit {

        public final String veinName;
        public final int blockX;
        public final int blockZ;

        public VeinHit(String veinName, int blockX, int blockZ) {
            this.veinName = veinName;
            this.blockX = blockX;
            this.blockZ = blockZ;
        }
    }
}
