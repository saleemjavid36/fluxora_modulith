package com.springmodulith.plugin.model;

import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

public final class ModulithModule {

    private final String name;
    private final String packageName;
    private final Set<String> allowedDependencies;

    public ModulithModule(
            @NotNull String name,
            @NotNull String packageName) {
        this(name, packageName, Collections.emptySet());
    }

    public ModulithModule(
            @NotNull String name,
            @NotNull String packageName,
            @NotNull Set<String> allowedDependencies) {

        this.name = name;
        this.packageName = packageName;
        this.allowedDependencies =
                Collections.unmodifiableSet(
                        new LinkedHashSet<>(allowedDependencies)
                );
    }

    @NotNull
    public String getName() {
        return name;
    }

    @NotNull
    public String getPackageName() {
        return packageName;
    }

    @NotNull
    public Set<String> getAllowedDependencies() {
        return allowedDependencies;
    }

    public boolean containsPackage(@NotNull String packageName) {
        return packageName.equals(this.packageName)
                || packageName.startsWith(this.packageName + ".");
    }

    public boolean allowsDependency(
            @NotNull ModulithModule targetModule) {

        if (targetModule.getPackageName().equals(this.packageName)) {
            return true;
        }

        if (allowedDependencies.contains(targetModule.getName())) {
            return true;
        }

        if (allowedDependencies.contains(targetModule.getPackageName())) {
            return true;
        }

        return false;
    }

    @Override
    public String toString() {
        return name + " (" + packageName + ")";
    }
}