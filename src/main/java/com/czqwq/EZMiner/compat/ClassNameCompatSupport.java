package com.czqwq.EZMiner.compat;

/**
 * Reflection utility for safely probing optional-mod classes by name.
 *
 * <p>
 * Classes are resolved with {@link Class#forName(String, boolean, ClassLoader)
 * Class.forName(name, false, loader)} — the {@code false} argument prevents static
 * initialisers from running, avoiding accidental side effects when probing
 * client-only classes or classes whose dependencies may not be satisfied.
 * </p>
 *
 * <p>
 * Used by EFR ore/crop compatibility adapters.
 * </p>
 */
public final class ClassNameCompatSupport {

    private ClassNameCompatSupport() {}

    /**
     * Returns {@code true} if a class with the given fully-qualified name can be resolved.
     */
    public static boolean isClassPresent(String className) {
        return resolveClass(className) != null;
    }

    /**
     * Resolves a class by name, returning {@code null} when the class or its dependencies
     * are unavailable.
     *
     * @param className fully-qualified class name
     * @return the {@link Class} object, or {@code null}
     */
    public static Class<?> resolveClass(String className) {
        if (className == null || className.isEmpty()) {
            return null;
        }
        try {
            return Class.forName(className, false, ClassNameCompatSupport.class.getClassLoader());
        } catch (ClassNotFoundException | LinkageError | SecurityException ignored) {
            return null;
        }
    }

    /**
     * Returns {@code true} when {@code instance} is an instance of {@code type}.
     *
     * <p>
     * Handles {@code null} inputs safely: returns {@code false} if either argument is null.
     */
    public static boolean isInstance(Class<?> type, Object instance) {
        return type != null && instance != null && type.isInstance(instance);
    }
}
