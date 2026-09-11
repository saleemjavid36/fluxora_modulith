package com.springmodulith.plugin.model;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class ModulithDependencyGraph {

    private final List<ModulithModule> modules;
    private final Set<ModuleDependency> dependencies;
    private final Set<ModuleDependency> cycleEdges;

    public ModulithDependencyGraph(
            @NotNull List<ModulithModule> modules,
            @NotNull Set<ModuleDependency> dependencies) {
        this.modules = Collections.unmodifiableList(new ArrayList<>(modules));
        this.dependencies = Collections.unmodifiableSet(new LinkedHashSet<>(dependencies));
        this.cycleEdges = Collections.unmodifiableSet(findCycleEdges());
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
                new ModuleDependency(source.getPackageName(), target.getPackageName())
        );
    }

    @NotNull
    public List<ModuleDependency> getOutgoingDependencies(@NotNull ModulithModule module) {
        List<ModuleDependency> result = new ArrayList<>();
        for (ModuleDependency dependency : dependencies) {
            if (dependency.sourcePackage().equals(module.getPackageName())) {
                result.add(dependency);
            }
        }
        return Collections.unmodifiableList(result);
    }

    @NotNull
    public List<ModuleDependency> getIncomingDependencies(@NotNull ModulithModule module) {
        List<ModuleDependency> result = new ArrayList<>();
        for (ModuleDependency dependency : dependencies) {
            if (dependency.targetPackage().equals(module.getPackageName())) {
                result.add(dependency);
            }
        }
        return Collections.unmodifiableList(result);
    }

    public boolean isCyclicEdge(@NotNull ModuleDependency edge) {
        return cycleEdges.contains(edge);
    }

    @NotNull
    private Set<ModuleDependency> findCycleEdges() {
        Set<ModuleDependency> result = new LinkedHashSet<>();
        Map<String, Set<String>> adjacency = new HashMap<>();

        for (ModulithModule module : modules) {
            if (!module.isOpen()) {
                adjacency.put(module.getPackageName(), new LinkedHashSet<>());
            }
        }

        for (ModuleDependency dependency : dependencies) {
            if (adjacency.containsKey(dependency.sourcePackage())
                    && adjacency.containsKey(dependency.targetPackage())) {
                adjacency.get(dependency.sourcePackage()).add(dependency.targetPackage());
            }
        }

        for (ModuleDependency dependency : dependencies) {
            if (adjacency.containsKey(dependency.sourcePackage())
                    && adjacency.containsKey(dependency.targetPackage())
                    && reachable(dependency.targetPackage(), dependency.sourcePackage(), adjacency, new HashSet<>())) {
                result.add(dependency);
            }
        }
        return result;
    }

    @NotNull
    public Set<Set<String>> getCycles() {
        Map<String, Set<String>> adjacency = new HashMap<>();
        for (ModulithModule module : modules) {
            if (!module.isOpen()) {
                adjacency.put(module.getPackageName(), new LinkedHashSet<>());
            }
        }

        for (ModuleDependency dependency : dependencies) {
            if (adjacency.containsKey(dependency.sourcePackage())
                    && adjacency.containsKey(dependency.targetPackage())) {
                adjacency.get(dependency.sourcePackage()).add(dependency.targetPackage());
            }
        }

        Set<Set<String>> cycles = new LinkedHashSet<>();
        for (String start : adjacency.keySet()) {
            findCycle(start, start, adjacency, new LinkedHashSet<>(), cycles);
        }
        return Collections.unmodifiableSet(cycles);
    }

    private boolean reachable(
            @NotNull String current,
            @NotNull String target,
            @NotNull Map<String, Set<String>> adjacency,
            @NotNull Set<String> visited) {
        if (current.equals(target)) {
            return true;
        }
        if (!visited.add(current)) {
            return false;
        }
        for (String next : adjacency.getOrDefault(current, Set.of())) {
            if (reachable(next, target, adjacency, visited)) {
                return true;
            }
        }
        return false;
    }

    private void findCycle(
            @NotNull String start,
            @NotNull String current,
            @NotNull Map<String, Set<String>> adjacency,
            @NotNull LinkedHashSet<String> path,
            @NotNull Set<Set<String>> cycles) {
        path.add(current);
        for (String target : adjacency.getOrDefault(current, Set.of())) {
            if (target.equals(start) && path.size() > 1) {
                cycles.add(new LinkedHashSet<>(path));
            } else if (!path.contains(target)) {
                findCycle(start, target, adjacency, path, cycles);
            }
        }
        path.remove(current);
    }

    public enum EdgeKind {
        ALLOWED,
        FORBIDDEN,
        NAMED_INTERFACE
    }

    public static final class ModuleDependency {
        private final String sourcePackage;
        private final String targetPackage;
        private final EdgeKind kind;
        private final boolean apiViolation;
        private final String namedInterface;
        private int referenceCount;

        public ModuleDependency(
                @NotNull String sourcePackage,
                @NotNull String targetPackage) {
            this(sourcePackage, targetPackage, EdgeKind.ALLOWED, false, null, 1);
        }

        public ModuleDependency(
                @NotNull String sourcePackage,
                @NotNull String targetPackage,
                @NotNull EdgeKind kind,
                boolean apiViolation,
                String namedInterface) {
            this(sourcePackage, targetPackage, kind, apiViolation, namedInterface, 1);
        }

        public ModuleDependency(
                @NotNull String sourcePackage,
                @NotNull String targetPackage,
                @NotNull EdgeKind kind,
                boolean apiViolation,
                String namedInterface,
                int referenceCount) {
            this.sourcePackage = sourcePackage;
            this.targetPackage = targetPackage;
            this.kind = kind;
            this.apiViolation = apiViolation;
            this.namedInterface = namedInterface;
            this.referenceCount = Math.max(1, referenceCount);
        }

        @NotNull
        public String sourcePackage() {
            return sourcePackage;
        }

        @NotNull
        public String targetPackage() {
            return targetPackage;
        }

        @NotNull
        public EdgeKind kind() {
            return kind;
        }

        public boolean isAllowed() {
            return kind != EdgeKind.FORBIDDEN && !apiViolation;
        }

        public boolean isForbidden() {
            return kind == EdgeKind.FORBIDDEN || apiViolation;
        }

        public boolean isNamedInterface() {
            return kind == EdgeKind.NAMED_INTERFACE;
        }

        public boolean isApiViolation() {
            return apiViolation;
        }

        @org.jetbrains.annotations.Nullable
        public String namedInterface() {
            return namedInterface;
        }

        public int referenceCount() {
            return referenceCount;
        }

        public void incrementReferenceCount() {
            referenceCount++;
        }

        @Override
        public boolean equals(Object object) {
            if (this == object) return true;
            if (!(object instanceof ModuleDependency other)) return false;
            return sourcePackage.equals(other.sourcePackage)
                    && targetPackage.equals(other.targetPackage);
        }

        @Override
        public int hashCode() {
            return 31 * sourcePackage.hashCode() + targetPackage.hashCode();
        }

        @Override
        public String toString() {
            return sourcePackage + " -> " + targetPackage + " [" + kind + "]";
        }
    }
}
