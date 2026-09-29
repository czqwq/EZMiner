package com.czqwq.EZMiner.network;

import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.MovingObjectPosition;

import com.czqwq.EZMiner.ClientProxy;
import com.czqwq.EZMiner.Config;
import com.czqwq.EZMiner.EZMiner;
import com.czqwq.EZMiner.compat.GT5ToolCompat;
import com.czqwq.EZMiner.utils.ToolHarvestEligibility;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import io.netty.buffer.ByteBuf;

/**
 * Server → Client: the current tool is about to break during chain mining.
 * The client should switch to the next best available tool in the hotbar.
 *
 * <p>
 * The handler is self-contained (no dependency on {@code SmartToolSwitchHandler})
 * so that it can be loaded on both sides during {@code NetworkMain.registry()}
 * without triggering client-only class loading on the dedicated server.
 * </p>
 */
public class PacketToolBreakHandoff implements IMessage {

    public PacketToolBreakHandoff() {}

    @Override
    public void fromBytes(ByteBuf buf) {
        // No payload needed — the signal itself is the message.
    }

    @Override
    public void toBytes(ByteBuf buf) {
        // No payload.
    }

    /**
     * Handler registered with {@link Side#CLIENT} — invoked only on the client.
     * Must be loadable on the server because {@code SimpleNetworkWrapper#registerMessage}
     * instantiates it immediately (lazy instantiation is not guaranteed in all
     * Forge 1.7.10 builds). This class therefore avoids any reference to
     * {@code @SideOnly(Side.CLIENT)} types.
     */
    public static class Handler implements IMessageHandler<PacketToolBreakHandoff, IMessage> {

        @Override
        @SideOnly(Side.CLIENT)
        public IMessage onMessage(PacketToolBreakHandoff msg, MessageContext ctx) {
            // Do NOT re-gate on Config.enableToolBreakHandoff here. The packet only exists because
            // the SERVER decided the handoff was warranted, and the client's copy of that field can
            // be stale on a dedicated server (before the first PacketServerConfig sync). Re-gating
            // on it made the server wait out toolBreakHandoffTimeoutTicks and then cancel the chain
            // while the client discarded every handoff packet. The client-local
            // smartToolSwitchEnabled / isActive() gates below are the only ones that belong here.
            if (!Config.smartToolSwitchEnabled) return null;

            Minecraft mc = Minecraft.getMinecraft();
            if (mc.thePlayer == null) return null;
            EntityPlayer player = mc.thePlayer;

            // Only auto-switch when smart-tool-switch mode is actually active.
            // The config flag defaults to true, so checking it alone would hijack
            // the hotbar even for players who never activated the mode.
            if (!((ClientProxy) EZMiner.proxy).smartToolSwitchHandler.isActive()) return null;

            // Capture the current look target (if any) so candidate tools must be
            // able to actually harvest it. When no target is available, fall back
            // to the generic "plausible mining tool" heuristic.
            Block targetBlock = null;
            int targetMeta = 0;
            MovingObjectPosition mop = mc.objectMouseOver;
            if (mop != null && mop.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK) {
                targetBlock = player.worldObj.getBlock(mop.blockX, mop.blockY, mop.blockZ);
                targetMeta = player.worldObj.getBlockMetadata(mop.blockX, mop.blockY, mop.blockZ);
            }
            final Block block = targetBlock;
            final int meta = targetMeta;

            int current = player.inventory.currentItem;
            int bestSlot = -1;
            int bestRemaining = 0;

            // Phase 1: scan hotbar for the best usable replacement mining tool.
            // Non-tool items (arrows, seeds, blocks) are rejected — and when a
            // target block is known the candidate must actually be able to harvest it.
            for (int i = 0; i < InventoryPlayer.getHotbarSize(); i++) {
                if (i == current) continue;
                ItemStack stack = player.inventory.mainInventory[i];
                if (!isUsableCandidate(stack, player, block, meta)) continue;
                // Toolboxes are a last-resort fallback: give them a low score so a
                // real direct tool (remaining >= 2) always wins when present.
                int remaining = GT5ToolCompat.isGTToolbox(stack) ? 1
                    : ToolHarvestEligibility.remainingDurability(stack);
                if (remaining > bestRemaining) {
                    bestRemaining = remaining;
                    bestSlot = i;
                }
            }

            // Phase 2: if no hotbar tool and full-inventory mode, scan main inventory
            if (bestSlot < 0 && Config.smartToolSwitchFullInventory) {
                for (int i = InventoryPlayer.getHotbarSize(); i < player.inventory.mainInventory.length; i++) {
                    ItemStack stack = player.inventory.mainInventory[i];
                    if (!isUsableCandidate(stack, player, block, meta)) continue;
                    int remaining = GT5ToolCompat.isGTToolbox(stack) ? 1
                        : ToolHarvestEligibility.remainingDurability(stack);
                    if (remaining > bestRemaining) {
                        bestRemaining = remaining;
                        bestSlot = i;
                    }
                }
                // If found in main inventory, ask the server to perform the swap
                // authoritatively (server records the borrow and syncs inventory).
                if (bestSlot >= 0) {
                    if (block != null && block != net.minecraft.init.Blocks.air && mop != null) {
                        EZMiner.network.network.sendToServer(
                            new com.czqwq.EZMiner.network.PacketToolSwapRequest(
                                mop.blockX,
                                mop.blockY,
                                mop.blockZ,
                                bestSlot));
                    }
                    return null;
                }
            }

            if (bestSlot >= 0) {
                player.inventory.currentItem = bestSlot;
                // GT Toolbox: select the correct internal tool for the target.
                ItemStack selected = player.inventory.mainInventory[bestSlot];
                if (block != null && block != net.minecraft.init.Blocks.air && GT5ToolCompat.isGTToolbox(selected)) {
                    int internal = GT5ToolCompat.findBestToolboxSlotForBlock(selected, block, meta);
                    if (internal >= 0) {
                        GT5ToolCompat.setToolboxSelectedTool(bestSlot, internal);
                    }
                }
            }
            return null;
        }

        /** True when the stack may be swapped in as a replacement mining tool. */
        @SideOnly(Side.CLIENT)
        private static boolean isUsableCandidate(ItemStack stack, EntityPlayer player, Block block, int meta) {
            if (GT5ToolCompat.isGTToolbox(stack)) {
                // Toolboxes need a concrete target to pick an internal tool.
                if (block == null || block == net.minecraft.init.Blocks.air) return false;
                return GT5ToolCompat.findBestToolboxSlotForBlock(stack, block, meta) >= 0;
            }
            if (block != null && block != net.minecraft.init.Blocks.air) {
                return ToolHarvestEligibility.isEligible(stack, block, meta);
            }
            return ToolHarvestEligibility.isUsableMiningTool(stack);
        }

    }
}
