package com.springmodulith.plugin.model;

import org.jetbrains.annotations.NotNull;

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

    @NotNull public String getName() { return name; }
    @NotNull public String getPackageName() { return packageName; }
    public boolean isOpen() { return open; }
    public boolean isAllowedDependenciesConfigured() { return allowedDependenciesConfigured; }
    @NotNull public Set<String> getAllowedDependencies() { return allowedDependencies; }
    @NotNull public List<NamedInterface> getNamedInterfaces() { return namedInterfaces; }

    public boolean containsPackage(@NotNull String candidate) {
        return candidate.equals(packageName) || candidate.startsWith(packageName + ".");
    }

    public boolean allowsDependency(@NotNull ModulithModule target) {
        if (this == target || packageName.equals(target.packageName)) return true;
        if (!allowedDependenciesConfigured) return true;
        for (String dependency : allowedDependencies) {
            DependencyRule rule = DependencyRule.parse(dependency);
            if (rule != null && target.matches(rule.moduleId())) {
                if (rule.interfaceId() == null || "*".equals(rule.interfaceId())) return true;
                if (target.hasNamedInterface(rule.interfaceId())) return true;
            }
        }
        return false;
    }

    public boolean allowsType(@NotNull String qualifiedType, @NotNull String targetPackage) {
        if (!allowedDependenciesConfigured) return true;
        for (String dependency : allowedDependencies) {
            DependencyRule rule = DependencyRule.parse(dependency);
            if (rule == null || !rule.moduleIdMatchesModule(target)) continue;
            if (rule.interfaceId() == null) {
                return targetPackage.equals(target.getPackageName());
            }
            if ("*".equals(rule.interfaceId())) return true;
            NamedInterface named = target.findNamedInterface(rule.interfaceId());
            if (named != null && (named.containsType(qualifiedType) || named.containsPackage(targetPackage))) return true;
        }
        return false;
    }

    public boolean exposes(@NotNull String qualifiedType, @NotNull String targetPackage) {
        if (open) return true;
        for (NamedInterface namedInterface : namedInterfaces) {
            if (namedInterface.containsType(qualifiedType) || namedInterface.containsPackage(targetPackage)) return true;
        }
        return targetPackage.equals(packageName);
    }

    public boolean hasNamedInterface(@NotNull String id) { return findNamedInterface(id) != null; }
    public NamedInterface findNamedInterface(@NotNull String id) {
        return namedInterfaces.stream().filter(i -> i.getName().equals(id)).findFirst().orElse(null);
    }

    private boolean matches(@NotNull String id) { return name.equals(id) || packageName.equals(id); }

    @Override public String toString() { return name + " (" + packageName + ")"; }

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
        private boolean moduleIdMatchesModule(ModulithModule module) { return module.matches(moduleId); }
    }
}
