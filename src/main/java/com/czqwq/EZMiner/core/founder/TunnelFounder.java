package com.czqwq.EZMiner.core.founder;

import java.util.ArrayList;
import java.util.concurrent.LinkedBlockingQueue;

import net.minecraft.entity.player.EntityPlayer;

import org.joml.Vector3i;

import com.czqwq.EZMiner.core.MinerConfig;

/** Blast mode – tunnel: digs a rectangular tunnel in the direction the player is facing. */
public class TunnelFounder extends BasePositionFounder {

    public TunnelFounder(Vector3i center, LinkedBlockingQueue<Vector3i> results, EntityPlayer player,
        MinerConfig minerConfig) {
        super(center, results, player, minerConfig);
        setName("EZMiner-BlastTunnel");
    }

    @Override
    public void run1() {
        Vector3i axisDir = getAxisAlignedLookDir();
        ArrayList<Vector3i> verticals = getVerticals(axisDir);
        Vector3i va = verticals.get(0);
        Vector3i vb = verticals.get(1);
        int tw = minerConfig.tunnelWidth;
        int curRadius = 0;
        while (curCount < minerConfig.blockLimit && curRadius < minerConfig.bigRadius) {
            Vector3i mainPt = new Vector3i(axisDir).mul(curRadius);
            for (int a = -tw; a <= tw; a++) {
                for (int b = -tw; b <= tw; b++) {
                    Vector3i pos = new Vector3i(center).add(mainPt)
                        .add(new Vector3i(va).mul(a))
                        .add(new Vector3i(vb).mul(b));
                    if (checkCanAdd(pos)) addResult(pos);
                    if (curCount >= minerConfig.blockLimit) return;
                    waitUntil();
                    if (Thread.currentThread()
                        .isInterrupted()) return;
                }
            }
            curRadius++;
        }
    }

    private Vector3i getAxisAlignedLookDir() {
        float yaw = ((player.rotationYaw % 360) + 360) % 360;
        float pitch = player.rotationPitch;
        if (pitch > 45) return new Vector3i(0, -1, 0);
        if (pitch < -45) return new Vector3i(0, 1, 0);
        if (yaw >= 315 || yaw < 45) return new Vector3i(0, 0, 1);
        if (yaw < 135) return new Vector3i(-1, 0, 0);
        if (yaw < 225) return new Vector3i(0, 0, -1);
        return new Vector3i(1, 0, 0);
    }

    private ArrayList<Vector3i> getVerticals(Vector3i axis) {
        ArrayList<Vector3i> result = new ArrayList<>();
        if (axis.x == 0) result.add(new Vector3i(1, 0, 0));
        if (axis.y == 0) result.add(new Vector3i(0, 1, 0));
        if (axis.z == 0) result.add(new Vector3i(0, 0, 1));
        return result;
    }
}
