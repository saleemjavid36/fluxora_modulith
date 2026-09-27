package com.springmodulith.plugin.model;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
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
        Map<String, Set<String>> adjacency = buildClosedModuleAdjacency();
        Set<Set<String>> stronglyConnectedComponents = findStronglyConnectedComponents(adjacency);

        for (ModuleDependency dependency : dependencies) {
            if (isInCyclicComponent(dependency.sourcePackage(), dependency.targetPackage(), stronglyConnectedComponents)) {
                result.add(dependency);
            }
        }
        return result;
    }

    @NotNull
    public Set<Set<String>> getCycles() {
        Set<Set<String>> cycles = new LinkedHashSet<>();
        for (Set<String> component : findStronglyConnectedComponents(buildClosedModuleAdjacency())) {
            if (component.size() > 1) {
                cycles.add(new LinkedHashSet<>(component));
            }
        }
        return Collections.unmodifiableSet(cycles);
    }

    @NotNull
    private Map<String, Set<String>> buildClosedModuleAdjacency() {
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
        return adjacency;
    }

    private boolean isInCyclicComponent(
            @NotNull String source,
            @NotNull String target,
            @NotNull Set<Set<String>> components) {
        for (Set<String> component : components) {
            if (component.size() > 1 && component.contains(source) && component.contains(target)) {
                return true;
            }
        }
        return false;
    }

    @NotNull
    private Set<Set<String>> findStronglyConnectedComponents(
            @NotNull Map<String, Set<String>> adjacency) {
        Map<String, Integer> indexes = new HashMap<>();
        Map<String, Integer> lowLinks = new HashMap<>();
        LinkedHashSet<String> stack = new LinkedHashSet<>();
        Set<Set<String>> components = new LinkedHashSet<>();
        int[] index = {0};

        for (String node : adjacency.keySet()) {
            if (!indexes.containsKey(node)) {
                strongConnect(node, adjacency, indexes, lowLinks, stack, components, index);
            }
        }
        return components;
    }

    private void strongConnect(
            @NotNull String node,
            @NotNull Map<String, Set<String>> adjacency,
            @NotNull Map<String, Integer> indexes,
            @NotNull Map<String, Integer> lowLinks,
            @NotNull LinkedHashSet<String> stack,
            @NotNull Set<Set<String>> components,
            int[] index) {
        indexes.put(node, index[0]);
        lowLinks.put(node, index[0]);
        index[0]++;
        stack.add(node);

        for (String next : adjacency.getOrDefault(node, Set.of())) {
            if (!indexes.containsKey(next)) {
                strongConnect(next, adjacency, indexes, lowLinks, stack, components, index);
                lowLinks.put(node, Math.min(lowLinks.get(node), lowLinks.get(next)));
            } else if (stack.contains(next)) {
                lowLinks.put(node, Math.min(lowLinks.get(node), indexes.get(next)));
            }
        }

        if (lowLinks.get(node).equals(indexes.get(node))) {
            Set<String> component = new LinkedHashSet<>();
            String current;
            do {
                current = removeLast(stack);
                component.add(current);
            } while (!node.equals(current));
            components.add(component);
        }
    }

    @NotNull
    private String removeLast(@NotNull LinkedHashSet<String> values) {
        String last = null;
        for (String value : values) {
            last = value;
        }
        if (last == null) {
            throw new IllegalStateException("Cycle analysis stack is empty");
        }
        values.remove(last);
        return last;
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
        private final Set<String> namedInterfaces;
        private final List<ModulithDependencyReference> references;
        private final int allowedReferenceCount;
        private final int forbiddenReferenceCount;
        private final int namedInterfaceReferenceCount;
        private final int referenceCount;

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
            this(sourcePackage, targetPackage, kind, apiViolation, namedInterface, 1, List.of());
        }

        public ModuleDependency(
                @NotNull String sourcePackage,
                @NotNull String targetPackage,
                @NotNull EdgeKind kind,
                boolean apiViolation,
                String namedInterface,
                int referenceCount) {
            this(sourcePackage, targetPackage, kind, apiViolation, namedInterface, referenceCount, List.of());
        }

        public ModuleDependency(
                @NotNull String sourcePackage,
                @NotNull String targetPackage,
                @NotNull EdgeKind kind,
                boolean apiViolation,
                String namedInterface,
                int referenceCount,
                @NotNull List<ModulithDependencyReference> references) {
            this(
                    sourcePackage,
                    targetPackage,
                    kind,
                    apiViolation,
                    namedInterface == null ? Set.of() : Set.of(namedInterface),
                    kind == EdgeKind.FORBIDDEN ? 0 : referenceCount,
                    kind == EdgeKind.FORBIDDEN ? referenceCount : 0,
                    kind == EdgeKind.NAMED_INTERFACE ? referenceCount : 0,
                    references
            );
        }

        public ModuleDependency(
                @NotNull String sourcePackage,
                @NotNull String targetPackage,
                @NotNull EdgeKind kind,
                boolean apiViolation,
                @NotNull Set<String> namedInterfaces,
                int allowedReferenceCount,
                int forbiddenReferenceCount,
                int namedInterfaceReferenceCount,
                @NotNull List<ModulithDependencyReference> references) {
            this.sourcePackage = sourcePackage;
            this.targetPackage = targetPackage;
            this.kind = kind;
            this.apiViolation = apiViolation;
            this.namedInterfaces = Collections.unmodifiableSet(new LinkedHashSet<>(namedInterfaces));
            this.namedInterface = this.namedInterfaces.size() == 1
                    ? this.namedInterfaces.iterator().next()
                    : null;
            this.references = Collections.unmodifiableList(new ArrayList<>(references));
            this.allowedReferenceCount = Math.max(0, allowedReferenceCount);
            this.forbiddenReferenceCount = Math.max(0, forbiddenReferenceCount);
            this.namedInterfaceReferenceCount = Math.max(0, namedInterfaceReferenceCount);
            this.referenceCount = references.isEmpty()
                    ? Math.max(1, this.allowedReferenceCount
                    + this.forbiddenReferenceCount
                    + this.namedInterfaceReferenceCount)
                    : references.size();
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
            return !isForbidden();
        }

        public boolean isForbidden() {
            return kind == EdgeKind.FORBIDDEN || apiViolation || forbiddenReferenceCount > 0;
        }

        public boolean isNamedInterface() {
            return !isForbidden() && namedInterfaceReferenceCount > 0;
        }

        public boolean isMixed() {
            int categories = 0;
            if (allowedReferenceCount > 0) categories++;
            if (namedInterfaceReferenceCount > 0) categories++;
            if (forbiddenReferenceCount > 0 || apiViolation) categories++;
            return categories > 1;
        }

        public boolean hasAllowedReferences() {
            return allowedReferenceCount > 0;
        }

        public boolean hasForbiddenReferences() {
            return forbiddenReferenceCount > 0 || apiViolation;
        }

        public boolean hasNamedInterfaceReferences() {
            return namedInterfaceReferenceCount > 0;
        }

        public boolean isApiViolation() {
            return apiViolation;
        }

        @org.jetbrains.annotations.Nullable
        public String namedInterface() {
            return namedInterface;
        }

        @NotNull
        public Set<String> namedInterfaces() {
            return namedInterfaces;
        }

        public int referenceCount() {
            return referenceCount;
        }

        public int allowedReferenceCount() {
            return allowedReferenceCount;
        }

        public int forbiddenReferenceCount() {
            return forbiddenReferenceCount;
        }

        public int namedInterfaceReferenceCount() {
            return namedInterfaceReferenceCount;
        }

        @NotNull
        public List<ModulithDependencyReference> references() {
            return references;
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
            return java.util.Objects.hash(sourcePackage, targetPackage);
        }

        @Override
        public String toString() {
            return sourcePackage + " -> " + targetPackage + " [" + kind + "]";
        }
    }

}
