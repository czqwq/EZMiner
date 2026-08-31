package com.czqwq.EZMiner.network;

import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.server.S09PacketHeldItemChange;

import com.czqwq.EZMiner.EZMiner;
import com.czqwq.EZMiner.compat.GT5ToolCompat;
import com.czqwq.EZMiner.toolswap.server.MinecraftToolSwapInventoryPort;
import com.czqwq.EZMiner.toolswap.server.ToolSwapServerService;
import com.czqwq.EZMiner.utils.ToolHarvestEligibility;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

/**
 * Client → Server: request a server-authoritative tool swap for the given target.
 *
 * <p>
 * The server is the only physical mutation owner: it validates the candidate
 * against the live target, performs the swap (or held-item change), records the
 * borrow in the server-side ledger, and syncs the inventory back to the client.
 * The client no longer mutates its own inventory for full-inventory borrows.
 */
public class PacketToolSwapRequest implements IMessage {

    private int targetX;
    private int targetY;
    private int targetZ;
    private int candidateSlot;

    public PacketToolSwapRequest() {}

    public PacketToolSwapRequest(int targetX, int targetY, int targetZ, int candidateSlot) {
        this.targetX = targetX;
        this.targetY = targetY;
        this.targetZ = targetZ;
        this.candidateSlot = candidateSlot;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        targetX = buf.readInt();
        targetY = buf.readInt();
        targetZ = buf.readInt();
        candidateSlot = buf.readByte();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(targetX);
        buf.writeInt(targetY);
        buf.writeInt(targetZ);
        buf.writeByte(candidateSlot);
    }

    public static class Handler implements IMessageHandler<PacketToolSwapRequest, IMessage> {

        @Override
        public IMessage onMessage(PacketToolSwapRequest msg, MessageContext ctx) {
            if (!ctx.side.isServer()) return null;
            EntityPlayerMP player = ctx.getServerHandler().playerEntity;
            if (player == null || player.isDead) return null;
            if (player.openContainer != player.inventoryContainer) return null;
            if (player.inventory.getItemStack() != null) return null;

            int candidate = msg.candidateSlot;
            if (candidate < 0 || candidate >= 36) return null;
            if (player.worldObj == null || !player.worldObj.blockExists(msg.targetX, msg.targetY, msg.targetZ)) {
                return null;
            }
            Block block = player.worldObj.getBlock(msg.targetX, msg.targetY, msg.targetZ);
            int meta = player.worldObj.getBlockMetadata(msg.targetX, msg.targetY, msg.targetZ);
            if (block == null || block == Blocks.air) return null;

            ItemStack candidateStack = player.inventory.mainInventory[candidate];
            if (candidateStack == null || candidateStack.getItem() == null) return null;

            // Server-side authority: the candidate must actually be able to harvest
            // the target (GT tools use isMinableBlock, TiC uses real NBT durability).
            boolean eligible = ToolHarvestEligibility.isEligible(candidateStack, block, meta);
            if (!eligible && GT5ToolCompat.isGTToolbox(candidateStack)) {
                eligible = GT5ToolCompat.findBestToolboxSlotForBlock(candidateStack, block, meta) >= 0;
            }
            if (!eligible) return null;

            int anchor = player.inventory.currentItem;
            if (candidate == anchor) return null;

            MinecraftToolSwapInventoryPort inv = new MinecraftToolSwapInventoryPort(player);
            if (!inv.isPlayerAlive() || !inv.hasPersonalInventoryWindow0() || !inv.isCursorEmpty()) return null;

            // For GT Toolbox, remember the internal tool slot so the client can
            // configure it after the server has physically moved the toolbox.
            boolean toolbox = GT5ToolCompat.isGTToolbox(candidateStack);
            int toolboxInternal = toolbox ? GT5ToolCompat.findBestToolboxSlotForBlock(candidateStack, block, meta) : -1;

            if (candidate < 9) {
                // Hotbar candidate: no physical swap, just change the held slot.
                player.inventory.currentItem = candidate;
                player.playerNetServerHandler.sendPacket(new S09PacketHeldItemChange(candidate));
            } else {
                // Inventory candidate: server performs the physical swap and records it.
                ItemStack aStack = inv.readInventorySlot(anchor);
                ItemStack bStack = inv.readInventorySlot(candidate);
                ToolSwapServerService.recordSwap(player, anchor, candidate, aStack, bStack);
                try {
                    inv.swapInventorySlotsAtomically(anchor, candidate);
                } catch (RuntimeException | LinkageError failure) {
                    // Mutation did not apply (pre-image): roll back the ledger entry.
                    ToolSwapServerService.removeSwap(player, anchor, candidate, aStack, bStack);
                    return null;
                }
                inv.syncInventoryDifference();
            }

            if (toolbox && toolboxInternal >= 0) {
                int hotbarSlot = candidate < 9 ? candidate : anchor;
                EZMiner.network.network.sendTo(new PacketToolSwapResult(hotbarSlot, toolboxInternal), player);
            }
            return null;
        }
    }
}
