package com.springmodulith.plugin.analyzer;

import com.intellij.openapi.project.Project;
import com.springmodulith.plugin.model.ModulithDependencyAnalysis;
import com.springmodulith.plugin.model.ModulithDependencyGraph;
import com.springmodulith.plugin.model.ModulithDependencyReference;
import com.springmodulith.plugin.model.ModulithModule;
import com.springmodulith.plugin.resolver.ModulithModuleResolver;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * Builds the graph from the same dependency analysis used by inspections.
 */
public final class ModulithDependencyGraphAnalyzer {
    private final Project project;
    private final ModulithModuleResolver resolver;

    public ModulithDependencyGraphAnalyzer(@NotNull Project project) {
        this.project = project;
        this.resolver = new ModulithModuleResolver(project);
    }

    @NotNull
    public ModulithDependencyGraph analyze() {
        if (project.isDisposed()) {
            return new ModulithDependencyGraph(List.of(), java.util.Set.of());
        }

        List<ModulithModule> modules = resolver.resolveModules();
        if (modules.isEmpty()) {
            return new ModulithDependencyGraph(modules, java.util.Set.of());
        }

        ModulithDependencyAnalyzer analyzer =
                new ModulithDependencyAnalyzer(resolver, project);
        List<ModulithDependencyAnalysis> analyses = analyzer.analyzeProject();

        Map<String, MutableDependency> aggregated = new LinkedHashMap<>();
        for (ModulithDependencyAnalysis analysis : analyses) {
            // A module graph has one logical edge per module pair.
            // Individual source references are retained inside that edge.
            String key = analysis.sourcePackage()
                    + "->" + analysis.targetPackage();
            MutableDependency dependency = aggregated.computeIfAbsent(
                    key,
                    ignored -> new MutableDependency(analysis)
            );
            dependency.add(analysis);
        }

        LinkedHashSet<ModulithDependencyGraph.ModuleDependency> dependencies = new LinkedHashSet<>();
        for (MutableDependency dependency : aggregated.values()) {
            dependencies.add(dependency.toGraphDependency());
        }

        return new ModulithDependencyGraph(modules, dependencies);
    }

    private static final class MutableDependency {
        private final String sourcePackage;
        private final String targetPackage;
        private boolean apiViolation;
        private int allowedReferenceCount;
        private int forbiddenReferenceCount;
        private int namedInterfaceReferenceCount;
        private final java.util.Set<String> namedInterfaces = new LinkedHashSet<>();
        private final List<ModulithDependencyReference> references = new ArrayList<>();

        private MutableDependency(@NotNull ModulithDependencyAnalysis first) {
            this.sourcePackage = first.sourcePackage();
            this.targetPackage = first.targetPackage();
            add(first);
        }

        private void add(@NotNull ModulithDependencyAnalysis next) {
            apiViolation |= next.apiViolation();
            references.add(next.sourceReference());

            switch (next.status()) {
                case FORBIDDEN -> forbiddenReferenceCount++;
                case NAMED_INTERFACE -> {
                    namedInterfaceReferenceCount++;
                    if (next.namedInterfaceName() != null) {
                        namedInterfaces.add(next.namedInterfaceName());
                    }
                }
                case ALLOWED -> allowedReferenceCount++;
            }
        }

        private ModulithDependencyGraph.ModuleDependency toGraphDependency() {
            ModulithDependencyGraph.EdgeKind kind;
            if (forbiddenReferenceCount > 0 || apiViolation) {
                kind = ModulithDependencyGraph.EdgeKind.FORBIDDEN;
            } else if (namedInterfaceReferenceCount > 0) {
                kind = ModulithDependencyGraph.EdgeKind.NAMED_INTERFACE;
            } else {
                kind = ModulithDependencyGraph.EdgeKind.ALLOWED;
            }

            return new ModulithDependencyGraph.ModuleDependency(
                    sourcePackage,
                    targetPackage,
                    kind,
                    apiViolation,
                    namedInterfaces,
                    allowedReferenceCount,
                    forbiddenReferenceCount,
                    namedInterfaceReferenceCount,
                    references
            );
        }
    }

}
