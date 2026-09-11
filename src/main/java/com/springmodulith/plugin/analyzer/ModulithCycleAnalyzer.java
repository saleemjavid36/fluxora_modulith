package com.springmodulith.plugin.analyzer;

import com.intellij.openapi.project.Project;
import com.springmodulith.plugin.model.ModulithDependencyGraph;
import com.springmodulith.plugin.model.ModulithModule;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class ModulithCycleAnalyzer {

    private final Project project;

    public ModulithCycleAnalyzer(@NotNull Project project) {
        this.project = project;
    }

    @NotNull
    public List<Cycle> findCycles() {
        ModulithDependencyGraph graph =
                new ModulithDependencyGraphAnalyzer(project).analyze();

        Map<String, ModulithModule> modulesByPackage =
                new HashMap<>();

        Map<String, Set<String>> adjacency =
                new HashMap<>();

        /*
         * Open modules are excluded from cycle detection.
         */
        for (ModulithModule module : graph.getModules()) {

            if (module.isOpen()) {
                continue;
            }

            modulesByPackage.put(
                    module.getPackageName(),
                    module
            );

            adjacency.put(
                    module.getPackageName(),
                    new LinkedHashSet<>()
            );
        }

        /*
         * Build module dependency graph.
         */
        for (ModulithDependencyGraph.ModuleDependency dependency :
                graph.getDependencies()) {

            if (!adjacency.containsKey(
                    dependency.sourcePackage())) {
                continue;
            }

            if (!adjacency.containsKey(
                    dependency.targetPackage())) {
                continue;
            }

            adjacency.get(
                    dependency.sourcePackage()
            ).add(
                    dependency.targetPackage()
            );
        }

        List<String> modules =
                new ArrayList<>(adjacency.keySet());

        modules.sort(String::compareTo);

        List<Cycle> cycles =
                new ArrayList<>();

        Set<String> detected =
                new HashSet<>();

        /*
         * DFS from every module.
         */
        for (String start : modules) {

            List<String> path =
                    new ArrayList<>();

            Set<String> visiting =
                    new LinkedHashSet<>();

            findCycles(
                    start,
                    start,
                    adjacency,
                    path,
                    visiting,
                    modulesByPackage,
                    cycles,
                    detected
            );
        }

        return cycles;
    }

    private void findCycles(
            @NotNull String start,
            @NotNull String current,
            @NotNull Map<String, Set<String>> adjacency,
            @NotNull List<String> path,
            @NotNull Set<String> visiting,
            @NotNull Map<String, ModulithModule> modulesByPackage,
            @NotNull List<Cycle> cycles,
            @NotNull Set<String> detected) {

        path.add(current);
        visiting.add(current);

        List<String> targets =
                new ArrayList<>(
                        adjacency.getOrDefault(
                                current,
                                Set.of()
                        )
                );

        targets.sort(String::compareTo);

        for (String target : targets) {

            /*
             * We reached the starting module.
             */
            if (target.equals(start)) {

                List<ModulithModule> cycleModules =
                        new ArrayList<>();

                for (String packageName : path) {

                    ModulithModule module =
                            modulesByPackage.get(
                                    packageName
                            );

                    if (module != null) {
                        cycleModules.add(module);
                    }
                }

                /*
                 * Ignore self dependency.
                 */
                if (cycleModules.size() >= 2) {

                    String key =
                            canonicalKey(cycleModules);

                    if (detected.add(key)) {
                        cycles.add(
                                new Cycle(cycleModules)
                        );
                    }
                }

            } else if (!visiting.contains(target)) {

                findCycles(
                        start,
                        target,
                        adjacency,
                        path,
                        visiting,
                        modulesByPackage,
                        cycles,
                        detected
                );
            }
        }

        visiting.remove(current);
        path.remove(path.size() - 1);
    }

    @NotNull
    private String canonicalKey(
            @NotNull List<ModulithModule> modules) {

        return modules.stream()
                .map(ModulithModule::getPackageName)
                .sorted()
                .reduce(
                        (left, right) ->
                                left + "->" + right
                )
                .orElse("");
    }

    public record Cycle(
            @NotNull List<ModulithModule> modules) {

        public Cycle {
            modules = List.copyOf(modules);
        }

        @NotNull
        public String displayPath() {

            return String.join(
                    " -> ",
                    modules.stream()
                            .map(ModulithModule::getName)
                            .toList()
            ) + " -> " + modules.get(0).getName();
        }
    }
}