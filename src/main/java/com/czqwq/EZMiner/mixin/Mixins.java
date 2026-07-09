package com.czqwq.EZMiner.mixin;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;

@SuppressWarnings("unused")
public enum Mixins {
    ;

    private final MixinClass[] MIXIN_CLASS;
    private final Supplier<Boolean> shouldApplyThisMixinGroup;

    Mixins(MixinClass... MIXIN_CLASS) {
        this(() -> true, MIXIN_CLASS);
    }

    Mixins(Supplier<Boolean> shouldApplyThisMixinGroup, MixinClass... MIXIN_CLASS) {
        this.MIXIN_CLASS = MIXIN_CLASS;
        this.shouldApplyThisMixinGroup = shouldApplyThisMixinGroup;
    }

    public static List<String> getLateMixins(Set<String> loadedMods) {
        List<String> mixins = new ArrayList<>();
        for (Mixins value : Mixins.values()) {
            if (!value.shouldApplyThisMixinGroup.get()) continue;
            for (MixinClass mixinClass : value.MIXIN_CLASS) {
                if (mixinClass.mClass.equals(MixinClass.ERROR)) continue;
                if (!mixinClass.phase.equals(Phase.LATE)) continue;
                if (!mixinClass.classPredicate.test(mixinClass)) continue;
                if (!loadedMods.containsAll(
                    mixinClass.targetMods.stream()
                        .map(TargetMod::getModId)
                        .collect(Collectors.toSet())))
                    continue;
                if (mixinClass.excludedMods.stream()
                    .map(TargetMod::getModId)
                    .anyMatch(loadedMods::contains)) continue;
                mixins.add(mixinClass.getMixinClassPath());
            }
        }
        return mixins;
    }

    enum PackagePath {

        HodgePodge;

        private final String path;

        PackagePath() {
            this.path = this.toString();
        }
    }

    static class MixinClass {

        static final String ERROR = "EZMiner_MIXIN_ERROR";

        final String id;
        String mClass = ERROR;
        String packagePath;
        Phase phase = Phase.ERROR_PHASE;
        List<TargetMod> targetMods = new ArrayList<>();
        List<TargetMod> excludedMods = new ArrayList<>();
        Predicate<MixinClass> classPredicate = mixinClass -> true;

        public MixinClass(String id) {
            this.id = id;
        }

        static MixinClass newMixinClass(String aIdentifier) {
            return new MixinClass(aIdentifier);
        }

        MixinClass setClass(String mClass) {
            this.mClass = mClass;
            return this;
        }

        public MixinClass setPhase(Phase phase) {
            this.phase = phase;
            return this;
        }

        public String getMixinClassPath() {
            if (this.packagePath == null) return this.mClass;
            return this.packagePath + this.mClass;
        }

        public MixinClass setPackagePath(PackagePath... packagePath) {
            if (packagePath == null || packagePath.length == 0) return this;
            this.packagePath = Arrays.stream(packagePath)
                .map(p -> p.path)
                .collect(Collectors.joining(".", "", "."));
            return this;
        }

        MixinClass addTargetMod(TargetMod... targetMod) {
            targetMods.addAll(Arrays.asList(targetMod));
            return this;
        }

        MixinClass addCondition(boolean condition) {
            classPredicate = classPredicate.and(mixinClass -> condition);
            return this;
        }
    }

    enum Phase {
        LATE,
        EARLY,
        ERROR_PHASE
    }
}
