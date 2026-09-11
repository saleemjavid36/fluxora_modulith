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
            String key = analysis.sourcePackage() + "->" + analysis.targetPackage();
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
        private ModulithDependencyAnalysis.Status status;
        private boolean apiViolation;
        private String namedInterface;
        private final List<ModulithDependencyReference> references = new ArrayList<>();

        private MutableDependency(@NotNull ModulithDependencyAnalysis first) {
            this.sourcePackage = first.sourcePackage();
            this.targetPackage = first.targetPackage();
            this.status = first.status();
            this.apiViolation = first.apiViolation();
            this.namedInterface = first.namedInterfaceName();
            this.references.add(first.sourceReference());
        }

        private void add(@NotNull ModulithDependencyAnalysis next) {
            this.apiViolation |= next.apiViolation();
            this.references.add(next.sourceReference());

            if (next.status() == ModulithDependencyAnalysis.Status.FORBIDDEN) {
                status = ModulithDependencyAnalysis.Status.FORBIDDEN;
            } else if (status != ModulithDependencyAnalysis.Status.FORBIDDEN
                    && next.status() == ModulithDependencyAnalysis.Status.NAMED_INTERFACE) {
                status = ModulithDependencyAnalysis.Status.NAMED_INTERFACE;
                if (namedInterface == null) {
                    namedInterface = next.namedInterfaceName();
                } else if (next.namedInterfaceName() != null
                        && !namedInterface.equals(next.namedInterfaceName())) {
                    namedInterface = null;
                }
            }
        }

        private ModulithDependencyGraph.ModuleDependency toGraphDependency() {
            ModulithDependencyGraph.EdgeKind kind = switch (status) {
                case FORBIDDEN -> ModulithDependencyGraph.EdgeKind.FORBIDDEN;
                case NAMED_INTERFACE -> ModulithDependencyGraph.EdgeKind.NAMED_INTERFACE;
                case ALLOWED -> ModulithDependencyGraph.EdgeKind.ALLOWED;
            };
            return new ModulithDependencyGraph.ModuleDependency(
                    sourcePackage,
                    targetPackage,
                    kind,
                    apiViolation,
                    namedInterface,
                    references.size(),
                    references
            );
        }
    }
}
