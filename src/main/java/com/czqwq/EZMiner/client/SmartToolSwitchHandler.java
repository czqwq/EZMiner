package com.czqwq.EZMiner.client;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.I18n;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.MovingObjectPosition.MovingObjectType;
import net.minecraftforge.client.event.MouseEvent;
import net.minecraftforge.common.MinecraftForge;

import org.lwjgl.input.Keyboard;

import com.czqwq.EZMiner.Config;
import com.czqwq.EZMiner.compat.GT5ToolCompat;

import cpw.mods.fml.client.registry.ClientRegistry;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.InputEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.common.network.FMLNetworkEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Smart-tool-switching handler with multi-tool cycling via mouse wheel.
 */
@SideOnly(Side.CLIENT)
public class SmartToolSwitchHandler {

    public static final KeyBinding KEY_SMART_TOOL_SWITCH = new KeyBinding(
        "key.ezminer.smartToolSwitch",
        Keyboard.KEY_R,
        "key.categories.ezminer");

    // ── State ─────────────────────────────────────────────────────────────────
    private boolean wasHolding;
    volatile boolean toggled;
    private volatile boolean tempDisabled;
    /** Server owns tool-borrow restore; the client is a thin observer. */

    // ── Target tracking ───────────────────────────────────────────────────────
    private int lastBlockX = Integer.MIN_VALUE, lastBlockY = Integer.MIN_VALUE, lastBlockZ = Integer.MIN_VALUE;
    /** Sorted list of suitable hotbar slots for the current target (best-first by score). */
    private final List<Integer> suitableSlots = new ArrayList<>();
    /** Suitable slots in hotbar/ascending slot order for predictable scroll-wheel cycling. */
    private final List<Integer> scrollSlots = new ArrayList<>();
    /** Index into {@link #scrollSlots} that is currently selected. */
    private int cycleIndex = -1;

    public boolean isActive() {
        return toggled;
    }

    // ── Input ─────────────────────────────────────────────────────────────────

    @SubscribeEvent
    public void onInput(InputEvent event) {
        if (!Config.smartToolSwitchEnabled) return;
        boolean holding = KEY_SMART_TOOL_SWITCH.getIsKeyPressed();
        boolean risingEdge = holding && !wasHolding;

        if (Config.smartToolSwitchActivationMode == 1) {
            if (risingEdge) {
                toggled = !toggled;
                if (toggled) {
                    printToggleMessage(true);
                    resetState();
                } else {
                    printToggleMessage(false);
                    restoreSwapAndClear();
                }
            }
        } else {
            boolean wasToggled = toggled;
            toggled = holding;
            if (toggled && !wasToggled) {
                printToggleMessage(true);
                resetState();
            } else if (!toggled && wasToggled) {
                printToggleMessage(false);
                restoreSwapAndClear();
            }
        }
        wasHolding = holding;
    }

    // ── Tick ──────────────────────────────────────────────────────────────────

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;
        if (!Config.smartToolSwitchEnabled || !toggled || tempDisabled) {
            if (!suitableSlots.isEmpty()) suitableSlots.clear();
            scrollSlots.clear();
            return;
        }

        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null || mc.theWorld == null) return;
        EntityPlayer player = mc.thePlayer;
        MovingObjectPosition mop = mc.objectMouseOver;
        if (mop == null) {
            if (!suitableSlots.isEmpty()) suitableSlots.clear();
            scrollSlots.clear();
            return;
        }

        int currentSlot = player.inventory.currentItem;

        if (mop.typeOfHit == MovingObjectType.BLOCK) {
            handleBlockTarget(player, mop, currentSlot, mc);
        } else if (mop.typeOfHit == MovingObjectType.ENTITY) {
            // Entities: unlock the hotbar — no auto-switch, no scroll hijack.
            // Weapon detection across mods is too fragile to get right, and the
            // player should freely pick their own weapon.
            clearBlockTracking();
        }
    }

    // ── Block targeting ───────────────────────────────────────────────────────

    private void handleBlockTarget(EntityPlayer player, MovingObjectPosition mop, int currentSlot, Minecraft mc) {
        Block block = player.worldObj.getBlock(mop.blockX, mop.blockY, mop.blockZ);
        // noinspection deprecation
        int meta = player.worldObj.getBlockMetadata(mop.blockX, mop.blockY, mop.blockZ);
        if (block == null || block.isAir(player.worldObj, mop.blockX, mop.blockY, mop.blockZ)) return;

        boolean sameTarget = mop.blockX == lastBlockX && mop.blockY == lastBlockY && mop.blockZ == lastBlockZ;

        // ── Stale-cache guard (P6): rebuild if current slot no longer has the expected tool ──
        if (sameTarget && !suitableSlots.isEmpty()) {
            ItemStack currentStack = player.inventory.mainInventory[currentSlot];
            if (currentStack == null
                || ToolEligibility.remainingDurability(currentStack) < ToolEligibility.MIN_REMAINING_DURABILITY
                || !ToolEligibility.isEffectiveForBlock(currentStack, block, meta)) {
                buildSuitableSlotsForBlock(player, block, meta);
                sameTarget = false; // treat as new target so switching logic runs
            }
        }

        if (!sameTarget) {
            // New target: find all suitable tools
            buildSuitableSlotsForBlock(player, block, meta);
        }

        if (suitableSlots.isEmpty()) return;

        // If the player is already on one of the suitable slots, accept it
        int idx = suitableSlots.indexOf(currentSlot);
        if (idx >= 0) {
            if (!sameTarget) {
                cycleIndex = 0;
                int bestSlot = suitableSlots.get(0);
                if (bestSlot != currentSlot) {
                    // The better tool may live in the main inventory (9-35): use the
                    // server-authoritative path (or client swap for GT toolbox).
                    if (bestSlot >= InventoryPlayer.getHotbarSize()) {
                        requestOrSwapInventoryTool(player, mop, bestSlot);
                        lastBlockX = mop.blockX;
                        lastBlockY = mop.blockY;
                        lastBlockZ = mop.blockZ;
                        return;
                    }
                    player.inventory.currentItem = bestSlot;
                }
            } else {
                cycleIndex = idx;
            }
            // Always refresh the toolbox internal selection — the player may
            // have manually deselected the tool (or it broke), even while
            // still aiming at the same block.
            configureToolboxIfNeeded(player, currentSlot, block, meta);
            lastBlockX = mop.blockX;
            lastBlockY = mop.blockY;
            lastBlockZ = mop.blockZ;
            return;
        }

        // Current slot is NOT suitable → switch to the best one
        cycleIndex = 0;
        int bestSlot = suitableSlots.get(0);
        // If the best tool is in the main inventory (not hotbar), bring it into
        // hand via the server-authoritative request.
        if (bestSlot >= InventoryPlayer.getHotbarSize()) {
            requestOrSwapInventoryTool(player, mop, bestSlot);
            lastBlockX = mop.blockX;
            lastBlockY = mop.blockY;
            lastBlockZ = mop.blockZ;
            return;
        }
        configureToolboxIfNeeded(player, bestSlot, block, meta);
        player.inventory.currentItem = bestSlot;
        lastBlockX = mop.blockX;
        lastBlockY = mop.blockY;
        lastBlockZ = mop.blockZ;
    }

    /**
     * Brings an inventory tool (9-35) into hand via the server-authoritative path.
     * The server validates the candidate, performs the physical swap (including GT
     * Toolboxes — internal selection is configured via {@code PacketToolSwapResult}),
     * records the borrow in its ledger and syncs the inventory back.
     */
    private void requestOrSwapInventoryTool(EntityPlayer player, MovingObjectPosition mop, int inventorySlot) {
        if (mop == null || mop.typeOfHit != MovingObjectType.BLOCK) return;
        if (player.inventory.mainInventory[inventorySlot] == null) return;
        com.czqwq.EZMiner.EZMiner.network.network.sendToServer(
            new com.czqwq.EZMiner.network.PacketToolSwapRequest(mop.blockX, mop.blockY, mop.blockZ, inventorySlot));
    }

    private void clearBlockTracking() {
        lastBlockX = Integer.MIN_VALUE;
        lastBlockY = Integer.MIN_VALUE;
        lastBlockZ = Integer.MIN_VALUE;
        suitableSlots.clear();
        scrollSlots.clear();
        cycleIndex = -1;
    }

    // ── Mouse-wheel cycling ───────────────────────────────────────────────────

    @SubscribeEvent
    public void onMouseEvent(MouseEvent event) {
        // ── Shift + left-click air: toggle temp disable ───────────────────────
        if (event.button == 0 && event.buttonstate && Keyboard.isKeyDown(Keyboard.KEY_LSHIFT)) {
            if (!Config.smartToolSwitchEnabled) return;
            Minecraft mc = Minecraft.getMinecraft();
            if (mc.objectMouseOver != null && mc.objectMouseOver.typeOfHit != MovingObjectType.MISS) return;
            if (!toggled && !tempDisabled) return;
            tempDisabled = !tempDisabled;
            if (tempDisabled) {
                suitableSlots.clear();
                scrollSlots.clear();
            }
            if (mc.thePlayer != null) {
                mc.thePlayer.addChatMessage(
                    new ChatComponentTranslation(
                        tempDisabled ? "ezminer.smartToolSwitch.tempDisabled"
                            : "ezminer.smartToolSwitch.tempReEnabled"));
            }
            return;
        }

        // ── Scroll wheel: cycle among suitable tools ──────────────────────────
        if (event.dwheel == 0) return;
        if (!Config.smartToolSwitchEnabled || !toggled || tempDisabled) return;

        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null) return;
        EntityPlayer player = mc.thePlayer;

        // Only hijack scrolling when we have a valid target with suitable tools
        MovingObjectPosition mop = mc.objectMouseOver;
        boolean hasTarget = false;
        if (mop != null && mop.typeOfHit == MovingObjectType.BLOCK) {
            if (mop.blockX == lastBlockX && mop.blockY == lastBlockY && mop.blockZ == lastBlockZ) hasTarget = true;
        }
        if (!hasTarget || scrollSlots.size() < 2) return;

        // Cancel vanilla inventory scroll — we handle hotbar switching ourselves
        event.setCanceled(true);

        int delta = event.dwheel > 0 ? -1 : 1;
        // Walk through scrollSlots (hotbar/ascending order) to find the next valid
        // candidate (P7: validate on scroll).
        int attempts = scrollSlots.size();
        Block block = player.worldObj.getBlock(mop.blockX, mop.blockY, mop.blockZ);
        // noinspection deprecation
        int meta = player.worldObj.getBlockMetadata(mop.blockX, mop.blockY, mop.blockZ);
        for (int tried = 0; tried < attempts; tried++) {
            cycleIndex = (cycleIndex + delta + scrollSlots.size()) % scrollSlots.size();
            int candidate = scrollSlots.get(cycleIndex);
            ItemStack candidateStack = player.inventory.mainInventory[candidate];
            if (candidateStack != null
                && ToolEligibility.remainingDurability(candidateStack) >= ToolEligibility.MIN_REMAINING_DURABILITY
                && ToolEligibility.isEffectiveForBlock(candidateStack, block, meta)) {
                // Inventory slots (9-35) cannot be assigned to currentItem (0-8):
                // ask the server to swap the tool into the current hotbar slot
                // (server-authoritative, ledger + restore + sync).
                if (candidate >= InventoryPlayer.getHotbarSize()) {
                    requestOrSwapInventoryTool(player, mop, candidate);
                    return;
                }
                player.inventory.currentItem = candidate;
                // Update toolbox internal selection if needed
                configureToolboxIfNeeded(player, candidate, block, meta);
                return;
            }
        }
        // No valid tool found via scrolling — rebuild for next tick
        buildSuitableSlotsForBlock(player, block, meta);
    }

    // ── Build suitable-slot lists ─────────────────────────────────────────────

    private void buildSuitableSlotsForBlock(EntityPlayer player, Block block, int meta) {
        suitableSlots.clear();
        scrollSlots.clear();
        cycleIndex = -1;
        // noinspection deprecation
        String requiredToolClass = block.getHarvestTool(meta);
        // noinspection deprecation
        int requiredLevel = block.getHarvestLevel(meta);
        boolean shearBlock = ToolEligibility.needsShears(block);

        // Parse preferred tools list (lazy, cached)
        String[] prefs = parsePreferredTools();

        int scanEnd = Config.smartToolSwitchFullInventory ? player.inventory.mainInventory.length
            : InventoryPlayer.getHotbarSize();

        List<int[]> scored = new ArrayList<>(); // [slot, score]
        for (int i = 0; i < scanEnd; i++) {
            ItemStack stack = player.inventory.mainInventory[i];
            if (stack == null) continue;

            // ── Durability gate (P0): skip tools with <= 1 remaining durability ──
            if (ToolEligibility.remainingDurability(stack) < ToolEligibility.MIN_REMAINING_DURABILITY) continue;

            // ── Efficiency gate (P1): skip tools that are not effective on this block ──
            boolean effective;
            if (GT5ToolCompat.isGTToolbox(stack)) {
                // Toolbox: check if an internal tool can actually harvest the block
                int s = GT5ToolCompat.findBestToolboxSlotForBlock(stack, block, meta);
                effective = s >= 0 && GT5ToolCompat.getToolboxInternalToolDigSpeed(stack, s, block, meta) > 1.0F;
            } else {
                effective = ToolEligibility.isEffectiveForBlock(stack, block, meta);
            }
            if (!effective) continue;

            int score = -1;
            boolean ok = false;

            if (shearBlock && ToolEligibility.isShears(stack)) {
                ok = true;
                score = 100;
            } else if (GT5ToolCompat.isGTToolbox(stack)) {
                int s = GT5ToolCompat.findBestToolboxSlotForBlock(stack, block, meta);
                if (s >= 0) {
                    ok = true;
                    score = GT5ToolCompat.getToolboxInternalToolHarvestLevel(stack, s, requiredToolClass);
                    if (score < 0) score = 0;
                }
            } else if (GT5ToolCompat.isGTTool(stack)) {
                if (GT5ToolCompat.canGTToolMineBlock(stack, block, meta)) {
                    // A GT tool must actually have the required tool class/level.
                    // Without this, a wrench could claim a pickaxe slot via the
                    // generic quality fallback (now removed from the helper too).
                    int gtLevel = GT5ToolCompat.getGTToolHarvestLevel(stack, requiredToolClass);
                    if (requiredToolClass == null || requiredToolClass.isEmpty() || gtLevel >= requiredLevel) {
                        ok = true;
                        score = Math.max(0, gtLevel);
                    }
                }
            } else {
                int hl = stack.getItem()
                    .getHarvestLevel(stack, requiredToolClass);
                if (hl >= requiredLevel) {
                    @SuppressWarnings("unchecked")
                    Set<String> tc = stack.getItem()
                        .getToolClasses(stack);
                    if (requiredToolClass == null || requiredToolClass.isEmpty() || tc.contains(requiredToolClass)) {
                        ok = true;
                        score = hl;
                    }
                }
            }
            if (!ok) continue;

            // Preferred tools boost — higher index = higher preference decay
            int prefBoost = getPreferenceBoost(stack, prefs);
            // Durability-aware scoring — durabilityPercent as tiebreaker
            int durabilityBonus = Config.smartToolSwitchDurabilityScore ? getDurabilityPercent(stack) : 0;

            // Composite score: harvestLevel * 1000 + prefBoost * 100 + durabilityBonus
            // prefBoost is inverted: 0 = highest pref (prefs.length), N = lowest pref
            int maxPref = Math.max(1, prefs.length);
            int effectiveScore = score * 1000 + (maxPref - prefBoost) * 100 + durabilityBonus;
            scored.add(new int[] { i, effectiveScore });
        }

        // Sort by effective score descending, then slot ascending (auto-best order).
        Collections.sort(
            scored,
            Comparator.<int[]>comparingInt(a -> -a[1])
                .thenComparingInt(a -> a[0]));
        suitableSlots.clear();
        for (int[] s : scored) suitableSlots.add(s[0]);

        // Scroll-wheel order: same eligible tools, but in hotbar/ascending slot
        // order so the player can predictably cycle 1→2→…→9 instead of a
        // score-sorted order that looks random.
        scrollSlots.clear();
        for (int[] s : scored) scrollSlots.add(s[0]);
        scrollSlots.sort(Comparator.naturalOrder());
    }

    // ── Durability helpers ──────────────────────────────────────────────────────

    /** Returns 0-100 durability percent for scoring tiebreaker. */
    private static int getDurabilityPercent(ItemStack stack) {
        if (stack == null) return 0;
        // Unbreakable items (GT electric tools, some TiC tools) — full score
        if (!stack.isItemStackDamageable()) return 100;
        int max = stack.getMaxDamage();
        if (max <= 0) return 100;
        int damage = stack.getItemDamage();
        return Math.max(0, (max - damage) * 100 / max);
    }

    // ── Preferred-tools helpers ─────────────────────────────────────────────────

    /** Lazy-parsed cache of preferred tool registry names. */
    private String[] cachedPrefs;
    private String cachedPrefsRaw;

    private String[] parsePreferredTools() {
        String raw = Config.preferredTools;
        if (raw == null) raw = "";
        if (raw.equals(cachedPrefsRaw) && cachedPrefs != null) return cachedPrefs;
        cachedPrefsRaw = raw;
        if (raw.isEmpty()) {
            cachedPrefs = new String[0];
        } else {
            cachedPrefs = raw.split(",");
            for (int i = 0; i < cachedPrefs.length; i++) cachedPrefs[i] = cachedPrefs[i].trim();
        }
        return cachedPrefs;
    }

    /** Returns preference ranking: 0 = highest priority (first in list), prefs.length = no match. */
    private int getPreferenceBoost(ItemStack stack, String[] prefs) {
        if (stack == null || prefs.length == 0) return prefs.length;
        Item item = stack.getItem();
        if (item == null) return prefs.length;
        String registryName = Item.itemRegistry.getNameForObject(item);
        if (registryName == null) return prefs.length;
        for (int i = 0; i < prefs.length; i++) {
            if (registryName.equals(prefs[i])) return i;
        }
        return prefs.length;
    }

    // ── Durability helpers ──────────────────────────────────────────────────────

    // ── Toolbox pre-configuration ─────────────────────────────────────────────

    private static void configureToolboxIfNeeded(EntityPlayer player, int bestSlot, Block block, int meta) {
        ItemStack bestStack = player.inventory.mainInventory[bestSlot];
        if (bestStack == null || !GT5ToolCompat.isGTToolbox(bestStack)) return;
        int internalSlot = GT5ToolCompat.findBestToolboxSlotForBlock(bestStack, block, meta);
        if (internalSlot >= 0) {
            GT5ToolCompat.setToolboxSelectedTool(bestSlot, internalSlot);
        }
    }

    // ── Messages ──────────────────────────────────────────────────────────────

    private static void printToggleMessage(boolean enabled) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null) return;
        String status = enabled ? I18n.format("ezminer.smartToolSwitch.enabled")
            : I18n.format("ezminer.smartToolSwitch.disabled");
        mc.thePlayer.addChatMessage(new ChatComponentTranslation("ezminer.smartToolSwitch.toggle", status));
    }

    // ── Swap restore (returns tools to original slots on key release) ──────────

    /**
     * Restores any borrowed hotbar items when a chain execution ends, without
     * disabling smart-switch mode. The player can immediately start another chain
     * and the hotbar layout is back to normal in between.
     */
    public void restoreAfterChainEnd() {
        restoreSwapAndClear();
    }

    /** Restores borrowed hotbar items and clears tracking state. */
    private void restoreSwapAndClear() {
        // The server is the authority for restoring borrows (chain end or
        // smart-switch deactivate); the client only asks and clears its lists.
        com.czqwq.EZMiner.EZMiner.network.network.sendToServer(new com.czqwq.EZMiner.network.PacketToolSwapFinalize());
        suitableSlots.clear();
        scrollSlots.clear();
    }

    // ── State reset ───────────────────────────────────────────────────────────

    private void resetState() {
        lastBlockX = Integer.MIN_VALUE;
        lastBlockY = Integer.MIN_VALUE;
        lastBlockZ = Integer.MIN_VALUE;
        suitableSlots.clear();
        scrollSlots.clear();
        cycleIndex = -1;
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    @SubscribeEvent
    public void onClientDisconnect(FMLNetworkEvent.ClientDisconnectionFromServerEvent event) {
        wasHolding = false;
        toggled = false;
        tempDisabled = false;
        resetState();
    }

    public void registry() {
        ClientRegistry.registerKeyBinding(KEY_SMART_TOOL_SWITCH);
        FMLCommonHandler.instance()
            .bus()
            .register(this);
        MinecraftForge.EVENT_BUS.register(this);
    }
}
