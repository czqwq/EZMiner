package com.czqwq.EZMiner.chain.execution;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;

import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.world.World;

import org.joml.Vector3i;

import com.czqwq.EZMiner.Config;
import com.czqwq.EZMiner.compat.GT5BlockSwapCompat;
import com.czqwq.EZMiner.core.founder.DeterminingIdentical;
import com.czqwq.EZMiner.utils.MessageUtils;

public class BlockSwapModeHandler {

    private static long encode(int x, int y, int z) {
        return ((long) x << 40) | ((long) z << 8) | (long) (y & 0xFF);
    }

    public void handleSwap(EntityPlayerMP player, Vector3i targetPos) {
        World world = player.worldObj;
        if (world == null || world.isRemote) return;

        // ── Validate target block ──
        if (!world.blockExists(targetPos.x, targetPos.y, targetPos.z)) return;
        Block targetBlock = world.getBlock(targetPos.x, targetPos.y, targetPos.z);
        int targetMeta = world.getBlockMetadata(targetPos.x, targetPos.y, targetPos.z);
        if (targetBlock == null || targetBlock.isAir(world, targetPos.x, targetPos.y, targetPos.z)) return;

        // ── Validate replacement item (block in hand) ──
        ItemStack heldStack = player.getCurrentEquippedItem();
        if (heldStack == null) {
            MessageUtils.serverSendPlayerMessage(
                new ChatComponentTranslation("ezminer.message.blockSwap.noItemInHand"),
                player.getUniqueID());
            return;
        }
        Block replacementBlock = Block.getBlockFromItem(heldStack.getItem());
        if (replacementBlock == null || replacementBlock.isAir(world, 0, 0, 0)) {
            MessageUtils.serverSendPlayerMessage(
                new ChatComponentTranslation("ezminer.message.blockSwap.notABlock"),
                player.getUniqueID());
            return;
        }
        int replacementMeta = heldStack.getItemDamage();
        // GT5U meta-blocks require the base meta from getTileEntityBaseType(),
        // not the raw item damage. Non-GT items pass through unchanged.
        replacementMeta = GT5BlockSwapCompat.getBaseMetaForItem(replacementMeta);
        final boolean replacementIsGT = GT5BlockSwapCompat.isGTBlock(replacementBlock);
        final int replacementItemDamage = heldStack.getItemDamage();

        // ── BFS: find all matching blocks within configured radius ──
        int maxRadius = Config.blockSwapRadius;
        int adjacencyRadius = Config.blockSwapAdjacencyRadius;
        int maxBlocks = Math.min(Config.blockSwapLimit, countAvailableItems(player, heldStack));

        if (maxBlocks <= 0) {
            MessageUtils.serverSendPlayerMessage(
                new ChatComponentTranslation("ezminer.message.blockSwap.noItems"),
                player.getUniqueID());
            return;
        }

        // GT cable: use connection-aware traversal + meta-tile-ID matching
        final boolean targetIsCable = GT5BlockSwapCompat
            .isCable(world.getTileEntity(targetPos.x, targetPos.y, targetPos.z));

        List<Vector3i> matched;
        if (targetIsCable) {
            int targetMetaId = GT5BlockSwapCompat
                .getCableMetaTileId(world.getTileEntity(targetPos.x, targetPos.y, targetPos.z));
            matched = bfsSearchCable(world, targetPos, targetMetaId, maxRadius, maxBlocks);
        } else {
            matched = bfsSearch(
                world,
                targetPos,
                targetBlock,
                targetMeta,
                maxRadius,
                adjacencyRadius,
                maxBlocks,
                player);
        }
        if (matched.isEmpty()) return;

        // ── Sort by Chebyshev distance from origin → center-out replacement ──
        matched.sort((a, b) -> {
            int da = Math
                .max(Math.max(Math.abs(a.x - targetPos.x), Math.abs(a.y - targetPos.y)), Math.abs(a.z - targetPos.z));
            int db = Math
                .max(Math.max(Math.abs(b.x - targetPos.x), Math.abs(b.y - targetPos.y)), Math.abs(b.z - targetPos.z));
            return Integer.compare(da, db);
        });

        // ── Replace blocks and consume items ──
        int swapped = 0;
        for (Vector3i pos : matched) {
            if (swapped >= maxBlocks) break;

            int oldMeta = world.getBlockMetadata(pos.x, pos.y, pos.z);
            Block oldBlock = world.getBlock(pos.x, pos.y, pos.z);
            if (oldBlock == null || oldBlock.isAir(world, pos.x, pos.y, pos.z)) continue;

            // Consume from inventory AFTER validating the block still exists.
            // (harvestBlock on some modded blocks sets the tile to air, which
            // could invalidate adjacent positions if item were consumed first.)
            if (!consumeItem(player, heldStack)) break;

            // ── Preserve GT cable/pipe connection state before removal ──
            int savedConnections = GT5BlockSwapCompat.saveConnections(world.getTileEntity(pos.x, pos.y, pos.z));

            // Harvest the original block (fires HarvestDropsEvent → drops collected
            // by ChainDropCollector when inOperate is set).
            oldBlock.harvestBlock(world, player, pos.x, pos.y, pos.z, oldMeta);

            // Replace with the new block
            world.setBlock(pos.x, pos.y, pos.z, replacementBlock, replacementMeta, 3);

            // ── Init GT meta-tile-entity and restore connections ──
            if (replacementIsGT) {
                GT5BlockSwapCompat.initGTMetaTileEntity(world, pos.x, pos.y, pos.z, replacementItemDamage);
                if (savedConnections >= 0) {
                    GT5BlockSwapCompat.restoreConnections(world, pos.x, pos.y, pos.z, savedConnections);
                }
            }
            swapped++;
        }

        // Force inventory sync to client so the client sees the consumed items
        // immediately (prevents ghost items until the next container-open).
        player.inventoryContainer.detectAndSendChanges();

        if (swapped > 0) {
            MessageUtils.serverSendPlayerMessage(
                new ChatComponentTranslation("ezminer.message.blockSwap.done", swapped),
                player.getUniqueID());
        } else {
            MessageUtils.serverSendPlayerMessage(
                new ChatComponentTranslation("ezminer.message.blockSwap.noItems"),
                player.getUniqueID());
        }
    }

    /** Reset handler state (no-op for now — swap is one-shot with no persisted state). */
    public void reset() {}

    // ===== Internal BFS =====

    private static List<Vector3i> bfsSearch(World world, Vector3i origin, Block targetBlock, int targetMeta,
        int maxRadius, int adjacencyRadius, int maxBlocks, EntityPlayerMP player) {
        List<Vector3i> results = new ArrayList<>();
        HashSet<Long> seen = new HashSet<>();

        // ── Seed with origin if it matches ──
        if (!world.blockExists(origin.x, origin.y, origin.z)) return results;
        Block originBlock = world.getBlock(origin.x, origin.y, origin.z);
        if (originBlock == null) return results;
        if (!DeterminingIdentical.identical(targetBlock, targetMeta, null, origin, player)) return results;

        results.add(origin);
        seen.add(encode(origin.x, origin.y, origin.z));

        // ── Scan shells from inside out ──
        for (int r = 1; r <= maxRadius && results.size() < maxBlocks; r++) {
            // Re-scan the current shell until no new matches are discovered
            // (handles intra-shell adjacency chains).
            boolean shellChanged;
            do {
                shellChanged = false;
                for (int dx = -r; dx <= r; dx++) {
                    int cx = origin.x + dx;
                    for (int dy = -r; dy <= r; dy++) {
                        int cy = origin.y + dy;
                        for (int dz = -r; dz <= r; dz++) {
                            int cz = origin.z + dz;
                            if (Math.max(Math.max(Math.abs(dx), Math.abs(dy)), Math.abs(dz)) != r) continue;
                            if (results.size() >= maxBlocks) break;

                            long key = encode(cx, cy, cz);
                            if (seen.contains(key)) continue;
                            seen.add(key);

                            if (!world.blockExists(cx, cy, cz)) continue;
                            Block block = world.getBlock(cx, cy, cz);
                            if (block == null) continue;

                            if (!DeterminingIdentical
                                .identical(targetBlock, targetMeta, null, new Vector3i(cx, cy, cz), player)) continue;

                            // Accept only if within adjacencyRadius of an existing result
                            if (isWithinRadius(cx, cy, cz, results, adjacencyRadius)) {
                                results.add(new Vector3i(cx, cy, cz));
                                shellChanged = true;
                            }
                        }
                    }
                }
            } while (shellChanged && results.size() < maxBlocks);
        }
        return results;
    }

    /** True if (x,y,z) is within Chebyshev distance {@code radius} of any element in {@code list}. */
    private static boolean isWithinRadius(int x, int y, int z, List<Vector3i> list, int radius) {
        for (int i = 0, n = list.size(); i < n; i++) {
            Vector3i v = list.get(i);
            if (Math.max(Math.max(Math.abs(x - v.x), Math.abs(y - v.y)), Math.abs(z - v.z)) <= radius) {
                return true;
            }
        }
        return false;
    }

    /**
     * Cable-aware BFS: only follows physically-connected sides of GT cables/pipes.
     * Matches by meta-tile-entity ID (same material + voltage + insulation).
     * Unconnected or different-type cables are treated as different blocks.
     */
    private static List<Vector3i> bfsSearchCable(World world, Vector3i origin, int targetMetaId, int maxRadius,
        int maxBlocks) {
        List<Vector3i> results = new ArrayList<>();
        HashSet<Long> visited = new HashSet<>();
        Deque<Vector3i> queue = new ArrayDeque<>();

        visited.add(encode(origin.x, origin.y, origin.z));
        queue.add(origin);
        results.add(origin);

        while (!queue.isEmpty() && results.size() < maxBlocks) {
            Vector3i current = queue.poll();

            // Only expand through connected sides (read from mConnections byte)
            int[][] dirs = GT5BlockSwapCompat
                .getConnectedDirections(world.getTileEntity(current.x, current.y, current.z));
            for (int[] dir : dirs) {
                int nx = current.x + dir[0];
                int ny = current.y + dir[1];
                int nz = current.z + dir[2];

                if (Math.abs(nx - origin.x) > maxRadius || Math.abs(ny - origin.y) > maxRadius
                    || Math.abs(nz - origin.z) > maxRadius) continue;

                long key = encode(nx, ny, nz);
                if (!visited.add(key)) continue;

                if (!world.blockExists(nx, ny, nz)) continue;

                // Match by meta-tile ID (same material, insulation, voltage)
                int neighborId = GT5BlockSwapCompat.getCableMetaTileId(world.getTileEntity(nx, ny, nz));
                if (neighborId == targetMetaId) {
                    Vector3i nPos = new Vector3i(nx, ny, nz);
                    queue.add(nPos);
                    results.add(nPos);
                    if (results.size() >= maxBlocks) break;
                }
            }
        }
        return results;
    }

    // ===== Inventory helpers =====

    /** Count how many matching items the player has (hotbar + main inventory). */
    private static int countAvailableItems(EntityPlayerMP player, ItemStack reference) {
        int count = 0;
        for (ItemStack stack : player.inventory.mainInventory) {
            if (stack != null && isSameItem(stack, reference)) {
                count += stack.stackSize;
            }
        }
        return count;
    }

    /** Match items by type + damage only (ignore NBT). */
    private static boolean isSameItem(ItemStack a, ItemStack b) {
        if (a == null || b == null) return a == b;
        return Objects.equals(a.getItem(), b.getItem()) && a.getItemDamage() == b.getItemDamage();
    }

    /**
     * Consume one item matching {@code reference} from the player's inventory.
     * Searches backpack slots (9–35) first, then hotbar (0–8), and finally the
     * currently-held slot as a last resort — so the held item is preserved as
     * long as there are matching items elsewhere in the inventory.
     *
     * @return {@code true} if an item was consumed
     */
    private static boolean consumeItem(EntityPlayerMP player, ItemStack reference) {
        ItemStack[] inv = player.inventory.mainInventory;
        int heldSlot = player.inventory.currentItem;

        // 1) Backpack slots: 9–35 (non-hotbar)
        for (int i = 35; i >= 9; i--) {
            if (consumeFromSlot(player, inv, i, reference)) return true;
        }
        // 2) Hotbar slots except the held one: 0–8, skip heldSlot
        for (int i = 8; i >= 0; i--) {
            if (i == heldSlot) continue;
            if (consumeFromSlot(player, inv, i, reference)) return true;
        }
        // 3) Held slot as last resort
        return consumeFromSlot(player, inv, heldSlot, reference);
    }

    private static boolean consumeFromSlot(EntityPlayerMP player, ItemStack[] inv, int slot, ItemStack reference) {
        ItemStack stack = inv[slot];
        if (stack != null && isSameItem(stack, reference)) {
            // Use setInventorySlotContents which calls markDirty() internally,
            // ensuring the client syncs immediately (no ghost items).
            if (stack.stackSize <= 1) {
                player.inventory.setInventorySlotContents(slot, null);
            } else {
                stack.stackSize--;
                player.inventory.markDirty();
            }
            return true;
        }
        return false;
    }
}
