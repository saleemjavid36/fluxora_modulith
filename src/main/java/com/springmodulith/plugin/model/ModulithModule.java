package com.springmodulith.plugin.model;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class ModulithModule {
    private final String name;
    private final String packageName;
    private final boolean open;
    private final boolean allowedDependenciesConfigured;
    private final Set<String> allowedDependencies;
    private final List<NamedInterface> namedInterfaces;

    public ModulithModule(@NotNull String name, @NotNull String packageName, boolean open,
                          boolean allowedDependenciesConfigured, @NotNull Set<String> allowedDependencies,
                          @NotNull List<NamedInterface> namedInterfaces) {
        this.name = name;
        this.packageName = packageName;
        this.open = open;
        this.allowedDependenciesConfigured = allowedDependenciesConfigured;
        this.allowedDependencies = Collections.unmodifiableSet(new LinkedHashSet<>(allowedDependencies));
        this.namedInterfaces = Collections.unmodifiableList(List.copyOf(namedInterfaces));
    }

    @NotNull
    public String getName() {
        return name;
    }

    @NotNull
    public String getPackageName() {
        return packageName;
    }

    public boolean isOpen() {
        return open;
    }

    public boolean isAllowedDependenciesConfigured() {
        return allowedDependenciesConfigured;
    }

    @NotNull
    public Set<String> getAllowedDependencies() {
        return allowedDependencies;
    }

    @NotNull
    public List<NamedInterface> getNamedInterfaces() {
        return namedInterfaces;
    }

    public boolean containsPackage(@NotNull String candidate) {
        return candidate.equals(packageName) || candidate.startsWith(packageName + ".");
    }

    public boolean allowsDependency(@NotNull ModulithModule target) {
        if (this == target || packageName.equals(target.packageName)) return true;
        if (!allowedDependenciesConfigured) return true;
        for (String dependency : allowedDependencies) {
            DependencyRule rule = DependencyRule.parse(dependency);
            if (rule != null && target.matchesModuleId(rule.moduleId())) {
                if (rule.interfaceId() == null || "*".equals(rule.interfaceId())) return true;
                if (target.hasNamedInterface(rule.interfaceId())) return true;
            }
        }
        return false;
    }

    public boolean allowsType(
            @NotNull String qualifiedType,
            @NotNull String targetPackage,
            @NotNull ModulithModule target) {

        if (!allowedDependenciesConfigured) {
            return true;
        }

        if (findAllowedNamedInterface(
                qualifiedType,
                targetPackage,
                target) != null) {
            return true;
        }

        for (String dependency : allowedDependencies) {
            DependencyRule rule = DependencyRule.parse(dependency);

            if (rule == null || !rule.moduleIdMatchesModule(target)) {
                continue;
            }

            /*
             * "student" -> root API only.
             */
            if (rule.interfaceId() == null) {
                if (target.isOpen()
                        || targetPackage.equals(target.getPackageName())) {
                    return true;
                }

                /*
                 * This rule did not allow the type.
                 * Continue checking the remaining rules.
                 */
                continue;
            }

            /*
             * "student::*" or "student :: *"
             * -> all explicitly declared named interfaces.
             */
            if ("*".equals(rule.interfaceId())) {
                if (target.findNamedInterfaceForType(
                        qualifiedType,
                        targetPackage
                ) != null) {
                    return true;
                }

                /*
                 * This wildcard rule did not allow the type.
                 * Continue checking the remaining rules.
                 */
                continue;
            }
        }

        return false;
    }

    @Nullable
    public NamedInterface findAllowedNamedInterface(
            @NotNull String qualifiedType,
            @NotNull String targetPackage,
            @NotNull ModulithModule target) {

        if (!allowedDependenciesConfigured) {
            return null;
        }

        for (String dependency : allowedDependencies) {
            DependencyRule rule = DependencyRule.parse(dependency);

            if (rule == null
                    || rule.interfaceId() == null
                    || "*".equals(rule.interfaceId())) {
                continue;
            }

            if (!target.matchesModuleId(rule.moduleId())) {
                continue;
            }

            NamedInterface namedInterface =
                    target.findNamedInterface(rule.interfaceId());

            if (namedInterface != null
                    && namedInterface.contains(
                    qualifiedType,
                    targetPackage)) {
                return namedInterface;
            }
        }

        return null;
    }

    public boolean exposes(@NotNull String qualifiedType, @NotNull String targetPackage) {
        if (open) return true;
        for (NamedInterface namedInterface : namedInterfaces) {
            if (namedInterface.containsType(qualifiedType) || namedInterface.containsPackage(targetPackage))
                return true;
        }
        return targetPackage.equals(packageName);
    }

    public boolean hasNamedInterface(@NotNull String id) {
        return findNamedInterface(id) != null;
    }

    public NamedInterface findNamedInterface(@NotNull String id) {
        return namedInterfaces.stream().filter(i -> i.getName().equals(id)).findFirst().orElse(null);
    }

    @Nullable
    public NamedInterface findNamedInterfaceForType(
            @NotNull String qualifiedType,
            @NotNull String targetPackage) {
        for (NamedInterface namedInterface : namedInterfaces) {
            if (namedInterface.containsType(qualifiedType)
                    || namedInterface.containsPackage(targetPackage)) {
                return namedInterface;
            }
        }
        return null;
    }

    public boolean matchesModuleId(@NotNull String id) {
        return name.equals(id) || packageName.equals(id);
    }

    @Override
    public String toString() {
        return name + " (" + packageName + ")";
    }

    public record DependencyRule(String moduleId, String interfaceId) {
        public static DependencyRule parse(String value) {
            if (value == null) return null;
            String text = value.trim();
            if (text.isEmpty()) return null;
            int separator = text.indexOf("::");
            if (separator < 0) return new DependencyRule(text, null);
            String module = text.substring(0, separator).trim();
            String iface = text.substring(separator + 2).trim();
            if (module.isEmpty() || iface.isEmpty()) return null;
            return new DependencyRule(module, iface);
        }

        private boolean moduleIdMatchesModule(ModulithModule module) {
            return module.matchesModuleId(moduleId);
        }
    }
}
