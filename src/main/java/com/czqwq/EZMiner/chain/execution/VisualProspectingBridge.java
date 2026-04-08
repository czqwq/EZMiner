package com.czqwq.EZMiner.chain.execution;

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
 */
public class VisualProspectingBridge {

    private volatile boolean compatibilityChecked = false;
    private volatile boolean hasVpApi = false;
    private volatile Method vpProspectMethod = null;
    private volatile Method vpSendToClientMethod = null;

    public synchronized void checkCompatibility() {
        if (compatibilityChecked) return;
        try {
            Class<?> logicalServerClass = Class
                .forName("com.sinthoras.visualprospecting.VisualProspecting_API$LogicalServer");
            vpProspectMethod = logicalServerClass
                .getMethod("prospectOreVeinsWithinRadius", int.class, int.class, int.class, int.class);
            vpSendToClientMethod = logicalServerClass
                .getMethod("sendProspectionResultsToClient", EntityPlayerMP.class, List.class, List.class);
            hasVpApi = true;
            EZMiner.LOG.info("EZMiner: VisualProspecting_API detected – ore vein discovery enabled.");
        } catch (ClassNotFoundException e) {
            EZMiner.LOG.debug("EZMiner: VisualProspecting_API not found – ore vein discovery disabled.");
        } catch (NoSuchMethodException | SecurityException e) {
            EZMiner.LOG.warn(
                "EZMiner: VisualProspecting_API found but required methods could not be resolved: {}",
                e.getMessage());
        }
        compatibilityChecked = true;
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
}
