package com.czqwq.EZMiner.core.founder;

import java.util.HashSet;
import java.util.concurrent.LinkedBlockingQueue;

import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.tileentity.TileEntity;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.joml.Vector3i;

import com.czqwq.EZMiner.core.MinerConfig;
import com.czqwq.EZMiner.thread.Pauseable;

/**
 * Base position finder: collects all non-air, non-liquid, non-bedrock blocks
 * within bigRadius that can be harvested by the player.
 */
public class BasePositionFounder extends Pauseable {

    public static final Logger LOG = LogManager.getLogger();

    public Vector3i center;
    public EntityPlayer player;
    public MinerConfig minerConfig;
    /** External queue consumed by the operator. */
    public LinkedBlockingQueue<Vector3i> positions;
    /** Internal dedup set. */
    public HashSet<Vector3i> foundedPositions = new HashSet<>();

    public int curCount = 0;

    // Sample block at center
    public final Block sampleBlock;
    public final int sampleBlockMeta;
    public final TileEntity sampleTileEntity;

    public BasePositionFounder(Vector3i center, LinkedBlockingQueue<Vector3i> results, EntityPlayer player,
        MinerConfig minerConfig) {
        setName("EZMiner-BlastSearch");
        DeterminingIdentical.checkCompatibility();

        this.center = center;
        this.player = player;
        this.positions = results;
        this.minerConfig = minerConfig;

        sampleBlock = player.worldObj.getBlock(center.x, center.y, center.z);
        sampleBlockMeta = player.worldObj.getBlockMetadata(center.x, center.y, center.z);
        sampleTileEntity = player.worldObj.getTileEntity(center.x, center.y, center.z);

        addResult(center);
    }

    @Override
    public void run1() {
        int curRadius = 1;
        while (curCount < minerConfig.blockLimit && curRadius <= minerConfig.bigRadius) {
            for (int x = center.x - curRadius; x <= center.x + curRadius; x++) {
                for (int y = center.y - curRadius; y <= center.y + curRadius; y++) {
                    for (int z = center.z - curRadius; z <= center.z + curRadius; z++) {
                        Vector3i pos = new Vector3i(x, y, z);
                        if (checkCanAdd(pos)) addResult(pos);
                        if (curCount >= minerConfig.blockLimit) return;
                        waitUntil();
                        if (Thread.currentThread()
                            .isInterrupted()) return;
                    }
                }
            }
            curRadius++;
        }
    }

    public boolean checkCanAdd(Vector3i pos) {
        if (foundedPositions.contains(pos)) return false;
        Block block = player.worldObj.getBlock(pos.x, pos.y, pos.z);
        if (block.equals(Blocks.air) || block.getMaterial()
            .isLiquid() || block.equals(Blocks.bedrock)) return false;
        int blockMeta = player.worldObj.getBlockMetadata(pos.x, pos.y, pos.z);
        Vector3i playerPos = playerFloorPos();
        // Protect the block directly under the player's feet
        if (pos.x == playerPos.x && pos.y == (playerPos.y - 1) && pos.z == playerPos.z) return false;
        if (player.capabilities.isCreativeMode) return true;
        return block.canHarvestBlock(player, blockMeta);
    }

    public void addResult(Vector3i pos) {
        try {
            positions.put(pos);
            foundedPositions.add(pos);
            curCount++;
        } catch (InterruptedException e) {
            Thread.currentThread()
                .interrupt();
        }
    }

    protected Vector3i playerFloorPos() {
        return new Vector3i(
            (int) Math.floor(player.posX),
            (int) Math.floor(player.posY),
            (int) Math.floor(player.posZ));
    }
}
