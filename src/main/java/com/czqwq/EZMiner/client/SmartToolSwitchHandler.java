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
    private boolean tempDisabled;

    // ── Target tracking ───────────────────────────────────────────────────────
    private int lastBlockX = Integer.MIN_VALUE, lastBlockY = Integer.MIN_VALUE, lastBlockZ = Integer.MIN_VALUE;
    /** Sorted list of suitable hotbar slots for the current target. */
    private final List<Integer> suitableSlots = new ArrayList<>();
    /** Index into {@link #suitableSlots} that is currently selected. */
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
                    suitableSlots.clear();
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
                suitableSlots.clear();
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
            return;
        }

        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null || mc.theWorld == null) return;
        EntityPlayer player = mc.thePlayer;
        MovingObjectPosition mop = mc.objectMouseOver;
        if (mop == null) {
            if (!suitableSlots.isEmpty()) suitableSlots.clear();
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
                player.inventory.currentItem = suitableSlots.get(0);
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
        configureToolboxIfNeeded(player, bestSlot, block, meta);
        player.inventory.currentItem = bestSlot;
        lastBlockX = mop.blockX;
        lastBlockY = mop.blockY;
        lastBlockZ = mop.blockZ;
    }

    private void clearBlockTracking() {
        lastBlockX = Integer.MIN_VALUE;
        lastBlockY = Integer.MIN_VALUE;
        lastBlockZ = Integer.MIN_VALUE;
        suitableSlots.clear();
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
            if (tempDisabled) suitableSlots.clear();
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
        if (!hasTarget || suitableSlots.size() < 2) return;

        // Cancel vanilla inventory scroll — we handle hotbar switching ourselves
        event.setCanceled(true);

        int delta = event.dwheel > 0 ? -1 : 1;
        cycleIndex = (cycleIndex + delta + suitableSlots.size()) % suitableSlots.size();
        int newSlot = suitableSlots.get(cycleIndex);
        player.inventory.currentItem = newSlot;

        // Update toolbox internal selection if needed
        if (mop != null && mop.typeOfHit == MovingObjectType.BLOCK) {
            Block block = player.worldObj.getBlock(mop.blockX, mop.blockY, mop.blockZ);
            // noinspection deprecation
            int meta = player.worldObj.getBlockMetadata(mop.blockX, mop.blockY, mop.blockZ);
            configureToolboxIfNeeded(player, newSlot, block, meta);
        }
    }

    // ── Build suitable-slot lists ─────────────────────────────────────────────

    private void buildSuitableSlotsForBlock(EntityPlayer player, Block block, int meta) {
        suitableSlots.clear();
        cycleIndex = -1;
        // noinspection deprecation
        String requiredToolClass = block.getHarvestTool(meta);
        // noinspection deprecation
        int requiredLevel = block.getHarvestLevel(meta);
        boolean shearBlock = needsShears(block);

        List<int[]> scored = new ArrayList<>(); // [slot, score]
        for (int i = 0; i < InventoryPlayer.getHotbarSize(); i++) {
            ItemStack stack = player.inventory.mainInventory[i];
            if (stack == null) continue;

            int score = -1;
            boolean ok = false;

            if (shearBlock && isShears(stack)) {
                ok = true;
                score = 100;
            } else if (GT5ToolCompat.isGTToolbox(stack)) {
                int s = GT5ToolCompat.getToolboxBestInternalSlot(stack, player, block, meta);
                if (s >= 0) {
                    ok = true;
                    score = GT5ToolCompat.getToolboxInternalToolHarvestLevel(stack, s, requiredToolClass);
                    if (score < 0) score = 0;
                }
            } else if (GT5ToolCompat.isGTTool(stack)) {
                if (GT5ToolCompat.canGTToolMineBlock(stack, block, meta)) {
                    ok = true;
                    score = GT5ToolCompat.getGTToolHarvestLevel(stack, requiredToolClass);
                    if (score < 0) score = requiredLevel;
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
            if (ok) scored.add(new int[] { i, score });
        }

        // Sort by score descending, then slot ascending
        Collections.sort(
            scored,
            Comparator.<int[]>comparingInt(a -> -a[1])
                .thenComparingInt(a -> a[0]));
        suitableSlots.clear();
        for (int[] s : scored) suitableSlots.add(s[0]);
    }

    // ── Shears ────────────────────────────────────────────────────────────────

    private static boolean needsShears(Block block) {
        net.minecraft.block.material.Material mat = block.getMaterial();
        return mat == net.minecraft.block.material.Material.leaves || mat == net.minecraft.block.material.Material.vine
            || mat == net.minecraft.block.material.Material.plants
            || mat == net.minecraft.block.material.Material.web
            || mat == net.minecraft.block.material.Material.cloth
            || mat == net.minecraft.block.material.Material.carpet;
    }

    private static boolean isShears(ItemStack stack) {
        if (stack == null) return false;
        Item item = stack.getItem();
        if (item == null) return false;
        for (Class<?> c = item.getClass(); c != null; c = c.getSuperclass()) {
            if (c.getName()
                .equals("net.minecraft.item.ItemShears")) return true;
        }
        return false;
    }

    // ── Toolbox pre-configuration ─────────────────────────────────────────────

    private static void configureToolboxIfNeeded(EntityPlayer player, int bestSlot, Block block, int meta) {
        ItemStack bestStack = player.inventory.mainInventory[bestSlot];
        if (bestStack == null || !GT5ToolCompat.isGTToolbox(bestStack)) return;
        int internalSlot = GT5ToolCompat.getToolboxBestInternalSlot(bestStack, player, block, meta);
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

    // ── State reset ───────────────────────────────────────────────────────────

    private void resetState() {
        lastBlockX = Integer.MIN_VALUE;
        lastBlockY = Integer.MIN_VALUE;
        lastBlockZ = Integer.MIN_VALUE;
        suitableSlots.clear();
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
