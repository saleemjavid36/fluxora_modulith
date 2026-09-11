package com.springmodulith.plugin.model;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class ModulithDependencyGraph {

    private final List<ModulithModule> modules;
    private final Set<ModuleDependency> dependencies;

    public ModulithDependencyGraph(
            @NotNull List<ModulithModule> modules,
            @NotNull Set<ModuleDependency> dependencies) {

        this.modules = Collections.unmodifiableList(new ArrayList<>(modules));
        this.dependencies = Collections.unmodifiableSet(
                new LinkedHashSet<>(dependencies)
        );
    }

    @NotNull
    public List<ModulithModule> getModules() {
        return modules;
    }

    @NotNull
    public Set<ModuleDependency> getDependencies() {
        return dependencies;
    }

    public boolean dependsOn(
            @NotNull ModulithModule source,
            @NotNull ModulithModule target) {

        return dependencies.contains(
                new ModuleDependency(
                        source.getPackageName(),
                        target.getPackageName()
                )
        );
    }

    public record ModuleDependency(
            @NotNull String sourcePackage,
            @NotNull String targetPackage) {
    }
}