package com.czqwq.EZMiner.chain.execution;

import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.world.World;
import net.minecraftforge.common.IPlantable;
import net.minecraftforge.common.util.ForgeDirection;

import org.joml.Vector3i;

import com.czqwq.EZMiner.Config;
import com.czqwq.EZMiner.utils.MessageUtils;

/**
 * Per-player planting special-mode logic.
 *
 * <p>
 * When the player right-clicks a soil block while holding a plantable item, all
 * plantable positions within {@link Config#plantRadius} are planted via the
 * item's own {@code onItemUse} — the vanilla code path (soil validation via
 * {@code canSustainPlant}, y+1 offset, {@code canPlayerEdit} permission checks,
 * stack consumption and placement sounds) with zero reimplementation. The scan
 * is a Chebyshev-shell walk out from the clicked soil block, bounded by
 * {@link Config#plantMaxCount}.
 *
 * <p>
 * The whole inventory is used as a reservoir: matching stacks (same item +
 * damage) from other slots are swapped into the held slot before planting, so a
 * single burst can exceed one stack. Consumption always happens on the held
 * slot through the vanilla path (empty hand → {@code destroyCurrentEquippedItem},
 * then the next matching stack is pulled in), and every slot change goes
 * through {@code setInventorySlotContents} (markDirty → client sync) — no
 * direct decrements of non-held stacks, no ghost items.
 *
 * <p>
 * Fully self-contained: debounce state, plantable check, scan, placement and
 * feedback messages live here; {@link com.czqwq.EZMiner.core.Manager} only
 * delegates.
 */
public class PlantingModeHandler {

    /** Same-position debounce window, mirroring the block-swap handler. */
    private static final long DEBOUNCE_MS = 150;

    private long lastPlantTimeMs = 0L;
    private long lastPlantEncodedPos = Long.MIN_VALUE;

    /**
     * Plants in range of the right-clicked soil block.
     *
     * @return the number of successfully planted items; {@code -1} when the
     *         click was debounced (same position within 150 ms) or the held
     *         item is not plantable — the caller must NOT consume the event.
     */
    public int handlePlant(EntityPlayerMP player, Vector3i soilPos) {
        World world = player.worldObj;
        if (world == null) return -1;
        ItemStack held = player.getCurrentEquippedItem();
        if (held == null || held.stackSize <= 0 || !isPlantable(held)) return -1;

        // Debounce: skip if the same position was planted within 150 ms.
        long now = System.currentTimeMillis();
        long encoded = ((long) soilPos.x << 32) | (soilPos.y << 16) | (soilPos.z & 0xFFFF);
        if (encoded == lastPlantEncodedPos && now - lastPlantTimeMs < DEBOUNCE_MS) return -1;
        lastPlantEncodedPos = encoded;
        lastPlantTimeMs = now;

        Item item = held.getItem();
        IPlantable plantable = plantableFrom(held); // non-null: guaranteed by isPlantable above
        int radius = Config.plantRadius;
        int maxCount = Config.plantMaxCount;
        int planted = 0;
        int heldSlot = player.inventory.currentItem;

        // Consume inventory stacks first, the held stack last (mirrors the
        // block-swap priority): if another slot holds the same item, swap it
        // into the hand and move the original held stack to that slot. All slot
        // changes go through setInventorySlotContents (markDirty → sync).
        ItemStack current = held;
        int firstOther = findMatchingSlot(player, held, heldSlot);
        if (firstOther >= 0) {
            ItemStack other = player.inventory.mainInventory[firstOther];
            player.inventory.setInventorySlotContents(heldSlot, other);
            player.inventory.setInventorySlotContents(firstOther, held);
            current = player.getCurrentEquippedItem();
        }

        // Chebyshev shells out from the clicked soil block (shell 0 = the block itself).
        outer: for (int r = 0; r <= radius && planted < maxCount; r++) {
            for (int dx = -r; dx <= r && planted < maxCount; dx++) {
                for (int dy = -r; dy <= r && planted < maxCount; dy++) {
                    for (int dz = -r; dz <= r && planted < maxCount; dz++) {
                        if (Math.max(Math.max(Math.abs(dx), Math.abs(dy)), Math.abs(dz)) != r) continue;
                        int x = soilPos.x + dx;
                        int y = soilPos.y + dy;
                        int z = soilPos.z + dz;
                        // Shared candidate predicate — the client planting preview
                        // uses the exact same check, so preview == planting area.
                        if (!isPlantablePosition(world, x, y, z, plantable)) continue;
                        // Vanilla planting path: the item validates the soil itself
                        // (canSustainPlant), offsets to y+1 and consumes from the stack.
                        if (item.onItemUse(current, player, world, x, y, z, 1, 0.5F, 1.0F, 0.5F)) {
                            planted++;
                        }
                        if (current.stackSize <= 0) {
                            // Vanilla path: an empty hand is cleared, then the next
                            // matching stack is pulled in from the inventory.
                            player.destroyCurrentEquippedItem();
                            if (planted >= maxCount) break outer;
                            int next = findMatchingSlot(player, held, heldSlot);
                            if (next < 0) break outer; // every matching item is spent
                            player.inventory.setInventorySlotContents(heldSlot, player.inventory.mainInventory[next]);
                            player.inventory.setInventorySlotContents(next, null);
                            current = player.getCurrentEquippedItem();
                        }
                    }
                }
            }
        }

        if (planted > 0) {
            player.inventoryContainer.detectAndSendChanges();
        }
        sendDoneMessage(player, planted);
        return planted;
    }

    /**
     * Finds the first inventory slot holding a non-empty stack matching
     * {@code reference} (same item + damage, NBT ignored — like block swap).
     * Backpack slots (9-35) first, then hotbar slots (0-8) excluding
     * {@code skipSlot}, so the held item is consumed last.
     *
     * @return the slot index, or -1 when nothing matches
     */
    private static int findMatchingSlot(EntityPlayerMP player, ItemStack reference, int skipSlot) {
        ItemStack[] inv = player.inventory.mainInventory;
        for (int i = 35; i >= 9; i--) {
            if (matches(inv[i], reference)) return i;
        }
        for (int i = 8; i >= 0; i--) {
            if (i == skipSlot) continue;
            if (matches(inv[i], reference)) return i;
        }
        return -1;
    }

    private static boolean matches(ItemStack stack, ItemStack reference) {
        return stack != null && stack.stackSize > 0
            && reference != null
            && stack.getItem() == reference.getItem()
            && stack.getItemDamage() == reference.getItemDamage();
    }

    /** Returns true when the held stack is plantable (seeds, saplings, …). */
    public static boolean isPlantable(ItemStack stack) {
        return plantableFrom(stack) != null;
    }

    /**
     * Resolves the {@link IPlantable} for a held stack: either the item itself
     * (seeds) or the block it places (saplings via their ItemBlock).
     */
    public static IPlantable plantableFrom(ItemStack stack) {
        if (stack == null) return null;
        Item item = stack.getItem();
        if (item instanceof IPlantable) return (IPlantable) item;
        Block block = Block.getBlockFromItem(item);
        return block instanceof IPlantable ? (IPlantable) block : null;
    }

    /**
     * Shared area-level candidate predicate for a soil position ({@code x},
     * {@code y}, {@code z} — the plant occupies {@code y+1}). Used by the server
     * planting loop as a cheap pre-filter (the item's own {@code onItemUse} does
     * the authoritative validation afterwards), and as the base of the client
     * preview predicate.
     *
     * <p>
     * Deliberately area-level only (no {@code canSustainPlant} refinement):
     * adding it to the server path would change behaviour for custom onItemUse
     * items that do not go through {@code canSustainPlant}.
     */
    public static boolean isPlantablePosition(World world, int x, int y, int z, IPlantable plantable) {
        if (plantable == null) return false;
        // World.isAirBlock has no y bounds guard — check manually.
        if (y < 0 || y >= 255) return false;
        if (!world.blockExists(x, y, z)) return false;
        // The plant would occupy (x, y+1), which must be air, and the soil
        // block itself must be plantable ground (not air/liquid/bedrock).
        if (!world.isAirBlock(x, y + 1, z)) return false;
        Block soil = world.getBlock(x, y, z);
        if (soil == null || soil.isAir(world, x, y, z)) return false;
        if (soil.getMaterial()
            .isLiquid() || soil.equals(Blocks.bedrock)) return false;
        return true;
    }

    /**
     * Exact client-preview predicate: {@link #isPlantablePosition} plus the
     * {@code canSustainPlant} soil check — the same validation the item's
     * {@code onItemUse} performs. Without it the preview would highlight every
     * non-air block in range (stone, logs, …) instead of the actual plantable
     * soil, looking like a normal chain preview.
     */
    public static boolean isPreviewPlantablePosition(World world, int x, int y, int z, IPlantable plantable) {
        return isPlantablePosition(world, x, y, z, plantable) && world.getBlock(x, y, z)
            .canSustainPlant(world, x, y, z, ForgeDirection.UP, plantable);
    }

    private static void sendDoneMessage(EntityPlayerMP player, int planted) {
        MessageUtils.serverSendPlayerMessage(
            new ChatComponentTranslation(
                planted > 0 ? "ezminer.message.plant.done" : "ezminer.message.plant.none",
                planted),
            player.getUniqueID());
    }

    /** No persistent session state; kept for duck-type symmetry with the other handlers. */
    public void reset() {}
}
