package com.czqwq.EZMiner.client;

import java.lang.reflect.Field;

import com.czqwq.EZMiner.EZMiner;

import cpw.mods.fml.common.Loader;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Reflection-based bridge to InGame Info XML's {@code ConfigurationHandler.showHUD} flag.
 *
 * <p>
 * When enabled by the {@code suppressIngameInfoHud} config option, EZMiner will temporarily
 * set {@code showHUD = false} while its own HUD is visible (chain key held), and restore the
 * previous value when the key is released or the player disconnects.
 *
 * <p>
 * All access is done via reflection so that EZMiner continues to work correctly when
 * InGame Info XML is not installed.
 */
@SideOnly(Side.CLIENT)
public final class IngameInfoBridge {

    private static final String MOD_ID = "InGameInfoXML";
    private static final String CLASS_NAME = "com.github.lunatrius.ingameinfo.handler.ConfigurationHandler";
    private static final String FIELD_NAME = "showHUD";

    private static boolean initialized = false;
    private static boolean available = false;
    private static Field showHudField = null;

    /** Value of {@code showHUD} before we set it to {@code false}. */
    private static boolean savedShowHud = true;

    private IngameInfoBridge() {}

    private static void ensureInit() {
        if (initialized) return;
        initialized = true;
        if (!Loader.isModLoaded(MOD_ID)) return;
        try {
            Class<?> clazz = Class.forName(CLASS_NAME);
            showHudField = clazz.getField(FIELD_NAME);
            available = true;
        } catch (Exception e) {
            EZMiner.LOG.warn("[EZMiner] Could not access InGameInfoXML ConfigurationHandler.showHUD: {}", e.toString());
        }
    }

    /**
     * Saves the current value of {@code ConfigurationHandler.showHUD} and sets it to
     * {@code false}, suppressing InGame Info XML rendering while EZMiner's HUD is shown.
     *
     * <p>
     * Does nothing if InGame Info XML is not installed.
     */
    public static void hideHud() {
        ensureInit();
        if (!available) return;
        try {
            savedShowHud = showHudField.getBoolean(null);
            showHudField.setBoolean(null, false);
        } catch (Exception e) {
            EZMiner.LOG.warn("[EZMiner] Failed to suppress InGameInfoXML HUD: {}", e.toString());
        }
    }

    /**
     * Restores {@code ConfigurationHandler.showHUD} to the value saved by the last call to
     * {@link #hideHud()}.
     *
     * <p>
     * Does nothing if InGame Info XML is not installed.
     */
    public static void restoreHud() {
        ensureInit();
        if (!available) return;
        try {
            showHudField.setBoolean(null, savedShowHud);
        } catch (Exception e) {
            EZMiner.LOG.warn("[EZMiner] Failed to restore InGameInfoXML HUD: {}", e.toString());
        }
    }
}
