package com.czqwq.EZMiner.compat;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

/**
 * Reflection bridge for GregTech 5 Unofficial tool compatibility.
 * <p>
 * All GT5 classes are accessed purely through reflection so EZMiner has zero
 * compile-time dependency on GT5. When GT5 is not installed every public method
 * returns a safe default ({@code false}, {@code -1}, or {@code 0F}).
 * <p>
 * This class lives in the {@code compat} package alongside other optional-mod
 * bridges and is explicitly initialised from {@code ClientProxy} so that
 * feature handlers stay decoupled from compatibility concerns.
 */
public class GT5ToolCompat {

    private static volatile boolean initialized;
    private static boolean gtLoaded;

    // ── Cached classes ───────────────────────────────────────────────────────
    private static Class<?> classMetaGeneratedTool;
    private static Class<?> classItemGTToolbox;
    private static Class<?> classIToolStats;
    private static Class<?> classToolboxPickBlockDecider;
    private static Class<?> classToolboxItemStackHandler;
    private static Class<?> classPickResults;

    // ── Cached MetaGeneratedTool methods ─────────────────────────────────────
    private static Method mGetToolStats;
    private static Method mGetToolCombatDamage;

    // ── Cached IToolStats methods ────────────────────────────────────────────
    private static Method mIsMinableBlock;
    private static Method mGetBaseQuality;

    // ── Cached toolbox methods ───────────────────────────────────────────────
    private static Method mGetSuggestedTool;
    private static Method mPickResultsSuggestedTools;
    private static Method mPickResultsForceDeselect;
    private static Method mToolboxItemStackHandlerGetStackInSlot;
    /** {@code ItemGTToolbox.sendChangeToolPacket(int, int)} — private static. */
    private static Method mSendChangeToolPacket;

    // ── Sword / Tool attack damage fields (MCP first, SRG fallback) ──────────
    private static final String[] SWORD_DAMAGE_FIELDS = { "field_150934_a", "weaponDamage" };
    private static final String[] TOOL_DAMAGE_FIELDS = { "damageVsEntity", "field_78036_m" };

    // ── Initialisation ───────────────────────────────────────────────────────

    /**
     * Must be called once during client startup (e.g. from
     * {@code ClientProxy.preInit}). Idempotent — subsequent calls are no-ops.
     */
    public static void init() {
        if (initialized) return;
        initialized = true;
        try {
            classMetaGeneratedTool = Class.forName("gregtech.api.items.MetaGeneratedTool");
            classItemGTToolbox = Class.forName("gregtech.common.items.ItemGTToolbox");
            classIToolStats = Class.forName("gregtech.api.interfaces.IToolStats");
            classToolboxPickBlockDecider = Class.forName("gregtech.common.items.toolbox.ToolboxPickBlockDecider");
            classToolboxItemStackHandler = Class.forName("gregtech.common.items.toolbox.ToolboxItemStackHandler");
            classPickResults = Class.forName("gregtech.common.items.toolbox.pickblock.PickResults");

            mGetToolStats = classMetaGeneratedTool.getMethod("getToolStats", ItemStack.class);
            mGetToolCombatDamage = classMetaGeneratedTool.getMethod("getToolCombatDamage", ItemStack.class);
            mIsMinableBlock = classIToolStats.getMethod("isMinableBlock", Block.class, int.class);
            mGetBaseQuality = classIToolStats.getMethod("getBaseQuality");
            mGetSuggestedTool = classToolboxPickBlockDecider.getMethod("getSuggestedTool", EntityPlayer.class);

            for (Method m : classPickResults.getMethods()) {
                if (m.getName()
                    .equals("suggestedTools") && m.getParameterTypes().length == 0) {
                    mPickResultsSuggestedTools = m;
                } else if (m.getName()
                    .equals("forceDeselect") && m.getParameterTypes().length == 0) {
                        mPickResultsForceDeselect = m;
                    }
            }
            for (Method m : classToolboxItemStackHandler.getMethods()) {
                if (m.getName()
                    .equals("getStackInSlot") && m.getParameterTypes().length == 1
                    && m.getParameterTypes()[0] == int.class) {
                    mToolboxItemStackHandlerGetStackInSlot = m;
                    break;
                }
            }

            // sendChangeToolPacket is private static — use getDeclaredMethod + setAccessible
            mSendChangeToolPacket = classItemGTToolbox.getDeclaredMethod("sendChangeToolPacket", int.class, int.class);
            mSendChangeToolPacket.setAccessible(true);

            gtLoaded = true;
        } catch (ClassNotFoundException ignored) {
            gtLoaded = false;
        } catch (NoSuchMethodException e) {
            gtLoaded = false;
            e.printStackTrace();
        }
    }

    /** Returns {@code true} when GT5 reflection initialisation succeeded. */
    public static boolean isLoaded() {
        return gtLoaded;
    }

    // ── Tool detection ───────────────────────────────────────────────────────

    public static boolean isGTTool(ItemStack stack) {
        if (!gtLoaded || stack == null) return false;
        Item item = stack.getItem();
        return item != null && classMetaGeneratedTool.isInstance(item);
    }

    public static boolean isGTToolbox(ItemStack stack) {
        if (!gtLoaded || stack == null) return false;
        Item item = stack.getItem();
        return item != null && classItemGTToolbox.isInstance(item);
    }

    // ── GT-tool block mining ─────────────────────────────────────────────────

    public static boolean canGTToolMineBlock(ItemStack tool, Block block, int meta) {
        if (!gtLoaded || tool == null || block == null) return false;
        try {
            Object toolStats = mGetToolStats.invoke(tool.getItem(), tool);
            if (toolStats == null) return false;
            return (boolean) mIsMinableBlock.invoke(toolStats, block, meta);
        } catch (IllegalAccessException | InvocationTargetException ignored) {
            return false;
        }
    }

    public static int getGTToolHarvestLevel(ItemStack tool, String toolClass) {
        if (!gtLoaded || tool == null || toolClass == null) return -1;
        int level = tool.getItem()
            .getHarvestLevel(tool, toolClass);
        if (level >= 0) return level;
        try {
            Object toolStats = mGetToolStats.invoke(tool.getItem(), tool);
            if (toolStats == null) return -1;
            return (int) mGetBaseQuality.invoke(toolStats);
        } catch (IllegalAccessException | InvocationTargetException ignored) {
            return -1;
        }
    }

    // ── GT-tool combat ───────────────────────────────────────────────────────

    public static float getGTToolAttackDamage(ItemStack tool) {
        if (!gtLoaded || tool == null) return 1.0F;
        try {
            return (float) mGetToolCombatDamage.invoke(tool.getItem(), tool);
        } catch (IllegalAccessException | InvocationTargetException ignored) {
            return 1.0F;
        }
    }

    // ── Toolbox API ──────────────────────────────────────────────────────────

    public static int getToolboxBestInternalSlot(ItemStack toolbox, EntityPlayer player, Block block, int meta) {
        if (!gtLoaded || toolbox == null || player == null) return -1;
        try {
            Object pickResults = mGetSuggestedTool.invoke(null, player);
            if (pickResults == null) return -1;
            if (mPickResultsForceDeselect != null) {
                if ((boolean) mPickResultsForceDeselect.invoke(pickResults)) return -1;
            }
            @SuppressWarnings("unchecked")
            List<Enum<?>> slots = (List<Enum<?>>) mPickResultsSuggestedTools.invoke(pickResults);
            if (slots == null || slots.isEmpty()) return -1;
            Enum<?> bestSlot = slots.get(0);
            int slotId = bestSlot.ordinal();
            Object handler = classToolboxItemStackHandler.getConstructor(ItemStack.class)
                .newInstance(toolbox);
            ItemStack internalTool = (ItemStack) mToolboxItemStackHandlerGetStackInSlot.invoke(handler, slotId);
            return (internalTool != null && internalTool.getItem() != null) ? slotId : -1;
        } catch (IllegalAccessException | InvocationTargetException | InstantiationException
            | NoSuchMethodException ignored) {
            return -1;
        }
    }

    public static int getToolboxInternalToolHarvestLevel(ItemStack toolbox, int slotId, String toolClass) {
        if (!gtLoaded || toolbox == null) return -1;
        try {
            Object handler = classToolboxItemStackHandler.getConstructor(ItemStack.class)
                .newInstance(toolbox);
            ItemStack internalTool = (ItemStack) mToolboxItemStackHandlerGetStackInSlot.invoke(handler, slotId);
            if (internalTool == null) return -1;
            return internalTool.getItem()
                .getHarvestLevel(internalTool, toolClass);
        } catch (IllegalAccessException | InvocationTargetException | InstantiationException
            | NoSuchMethodException ignored) {
            return -1;
        }
    }

    public static boolean canToolboxMineBlock(ItemStack toolbox, EntityPlayer player, Block block, int meta) {
        return getToolboxBestInternalSlot(toolbox, player, block, meta) >= 0;
    }

    /**
     * Changes the toolbox's active internal tool via GT's own
     * {@code sendChangeToolPacket}, which sends a {@code GTPacketToolboxEvent}
     * to the server so both sides stay in sync. The slot ID should come from
     * {@link #getToolboxBestInternalSlot}.
     *
     * @param hotbarSlot the hotbar index (0–8) where the toolbox currently resides
     */
    public static void setToolboxSelectedTool(int hotbarSlot, int slotId) {
        if (!gtLoaded || mSendChangeToolPacket == null) return;
        try {
            mSendChangeToolPacket.invoke(null, hotbarSlot, slotId);
        } catch (IllegalAccessException | InvocationTargetException ignored) {}
    }

    // ── Vanilla attack-damage helpers ────────────────────────────────────────

    public static float getVanillaAttackDamage(ItemStack stack) {
        if (stack == null) return 1.0F;
        Item item = stack.getItem();
        for (String fieldName : SWORD_DAMAGE_FIELDS) {
            try {
                java.lang.reflect.Field f = item.getClass()
                    .getField(fieldName);
                return f.getFloat(item);
            } catch (NoSuchFieldException | IllegalAccessException ignored) {}
        }
        for (String fieldName : TOOL_DAMAGE_FIELDS) {
            try {
                java.lang.reflect.Field f = item.getClass()
                    .getField(fieldName);
                return f.getFloat(item);
            } catch (NoSuchFieldException | IllegalAccessException ignored) {}
        }
        return 1.0F;
    }

    public static boolean isPotentialWeapon(ItemStack stack) {
        if (stack == null) return false;
        return getVanillaAttackDamage(stack) > 1.5F || isGTTool(stack);
    }

    /**
     * Returns {@code true} for GT tools that are actual weapons (knife,
     * butchery knife), not mining/utility tools that happen to have non-zero
     * attack damage (hammer, wrench, etc.).
     */
    public static boolean isGTWeapon(ItemStack stack) {
        if (!gtLoaded || stack == null) return false;
        try {
            Object toolStats = mGetToolStats.invoke(stack.getItem(), stack);
            if (toolStats == null) return false;
            String className = toolStats.getClass()
                .getSimpleName()
                .toLowerCase();
            return className.contains("knife") || className.contains("butchery");
        } catch (IllegalAccessException | InvocationTargetException ignored) {
            return false;
        }
    }
}
