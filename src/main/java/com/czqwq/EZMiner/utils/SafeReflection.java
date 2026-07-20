package com.czqwq.EZMiner.utils;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import com.czqwq.EZMiner.Config;

/**
 * Safe reflection utilities with {@link LinkageError} guards.
 *
 * <p>
 * When {@link Config#enableSafeReflection} is {@code true} (default), all methods
 * use {@code Class.forName(name, false, loader)} (no static init) and catch
 * {@link LinkageError} in addition to the usual reflection exceptions. When the
 * flag is {@code false}, methods delegate to the standard JDK APIs directly
 * (legacy behavior).
 * </p>
 *
 * <p>
 * Safe reflection utilities with LinkageError guards.
 * </p>
 */
public final class SafeReflection {

    private SafeReflection() {}

    // ── Class resolution ────────────────────────────────────────────────────────

    /**
     * Resolves a class by name.
     *
     * <p>
     * When safe mode is on: uses {@code Class.forName(name, false, loader)} —
     * no static initializer execution, swallows {@link LinkageError}.
     * When safe mode is off: uses the standard 1-arg {@code Class.forName(name)}.
     * </p>
     *
     * @param className fully-qualified class name
     * @return the {@link Class} object, or {@code null} on failure
     */
    public static Class<?> forName(String className) {
        if (!Config.enableSafeReflection) {
            try {
                return Class.forName(className);
            } catch (ClassNotFoundException ignored) {
                return null;
            }
        }
        try {
            return Class.forName(className, false, SafeReflection.class.getClassLoader());
        } catch (ClassNotFoundException | LinkageError | SecurityException ignored) {
            return null;
        }
    }

    // ── Method resolution ───────────────────────────────────────────────────────

    /**
     * Returns a {@link Method} from {@code clazz} with the given name and parameter
     * types, with {@code setAccessible(true)} already called.
     *
     * @param clazz      the class to search (null-safe — returns null)
     * @param name       method name
     * @param paramTypes parameter types
     * @return the accessible {@link Method}, or {@code null} on failure
     */
    public static Method getMethod(Class<?> clazz, String name, Class<?>... paramTypes) {
        if (clazz == null || name == null) return null;
        try {
            Method m = clazz.getMethod(name, paramTypes);
            m.setAccessible(true);
            return m;
        } catch (NoSuchMethodException | SecurityException | LinkageError ignored) {
            return null;
        }
    }

    /**
     * Returns a declared {@link Method} (including non-public) from {@code clazz},
     * with {@code setAccessible(true)} already called.
     *
     * @param clazz      the class to search (null-safe — returns null)
     * @param name       method name
     * @param paramTypes parameter types
     * @return the accessible {@link Method}, or {@code null} on failure
     */
    public static Method getDeclaredMethod(Class<?> clazz, String name, Class<?>... paramTypes) {
        if (clazz == null || name == null) return null;
        try {
            Method m = clazz.getDeclaredMethod(name, paramTypes);
            m.setAccessible(true);
            return m;
        } catch (NoSuchMethodException | SecurityException | LinkageError ignored) {
            return null;
        }
    }

    // ── Field resolution ────────────────────────────────────────────────────────

    /**
     * Returns a {@link Field} from {@code clazz} (public fields only, including
     * inherited), with {@code setAccessible(true)} already called.
     *
     * @param clazz the class to search (null-safe — returns null)
     * @param name  field name
     * @return the accessible {@link Field}, or {@code null} on failure
     */
    public static Field getField(Class<?> clazz, String name) {
        if (clazz == null || name == null) return null;
        try {
            Field f = clazz.getField(name);
            f.setAccessible(true);
            return f;
        } catch (NoSuchFieldException | SecurityException | LinkageError ignored) {
            return null;
        }
    }

    // ── Invocation ──────────────────────────────────────────────────────────────

    /**
     * Invokes a {@link Method} on {@code target} with the given arguments.
     * Swallows all exceptions and returns {@code null} on any failure.
     *
     * @param method the method to invoke (null-safe — returns null)
     * @param target the target object (may be {@code null} for static methods)
     * @param args   the invocation arguments
     * @return the method's return value, or {@code null} on failure
     */
    public static Object invoke(Method method, Object target, Object... args) {
        if (method == null) return null;
        try {
            return method.invoke(target, args);
        } catch (IllegalAccessException | InvocationTargetException | LinkageError ignored) {
            return null;
        }
    }
}
