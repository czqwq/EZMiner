package com.czqwq.EZMiner.mixin;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

import com.czqwq.EZMiner.Config;

/**
 * ASM-based bytecode inspection utility for mixin capability gating.
 *
 * <p>
 * Provides {@link #targetHasMethod(String, String, String)} for checking whether
 * a target class contains a method with the expected signature — used by
 * {@link Mixins.MixinClass#addBytecodeCondition} to skip mixins whose target
 * class shape doesn't match (preventing crashes on GTNH version mismatches).
 * </p>
 *
 * <p>
 * This is a standalone utility class, not a Mixin {@code IMixinConfigPlugin}
 * implementation. The {@code IMixinConfigPlugin} interface varies across Mixin
 * versions (the {@code IMixinInfo} parameter class is not portable), so the
 * capability gate is applied through the late-mixin loader path instead.
 * </p>
 *
 * <p>
 * Gated behind {@link Config#enableMixinCapabilityGates} — when disabled,
 * {@link #targetHasMethod} always returns {@code true}.
 * </p>
 *
 * <p>
 * Used by the late-mixin loader for conditional mixin application.
 * </p>
 */
public final class MixinCapabilityPlugin {

    private MixinCapabilityPlugin() {}

    /**
     * Checks whether a target class (by internal name) contains a method with the
     * given name and descriptor, by reading its bytecode without loading the class.
     *
     * @param targetClass internal class name (e.g. {@code "gregtech/common/ores/GTOreAdapter"})
     * @param methodName  method name
     * @param descriptor  JVM method descriptor
     * @return {@code true} if the method exists in the target class bytecode
     */
    public static boolean targetHasMethod(String targetClass, String methodName, String descriptor) {
        if (!Config.enableMixinCapabilityGates) return true;
        try {
            java.io.InputStream is = MixinCapabilityPlugin.class.getClassLoader()
                .getResourceAsStream(targetClass.replace('.', '/') + ".class");
            if (is == null) return false;
            byte[] bytes = readAllBytes(is);
            is.close();
            if (bytes == null || bytes.length == 0) return false;

            ClassNode node = new ClassNode();
            new ClassReader(bytes).accept(node, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
            for (MethodNode m : node.methods) {
                if (methodName.equals(m.name) && descriptor.equals(m.desc)) {
                    return true;
                }
            }
            return false;
        } catch (Exception e) {
            // If we can't read the bytecode, err on the side of applying the mixin.
            return true;
        }
    }

    private static byte[] readAllBytes(java.io.InputStream is) throws java.io.IOException {
        java.io.ByteArrayOutputStream buffer = new java.io.ByteArrayOutputStream();
        byte[] data = new byte[4096];
        int n;
        while ((n = is.read(data, 0, data.length)) != -1) {
            buffer.write(data, 0, n);
        }
        return buffer.toByteArray();
    }
}
