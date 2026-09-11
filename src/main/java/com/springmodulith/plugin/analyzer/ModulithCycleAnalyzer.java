package com.springmodulith.plugin.analyzer;

import com.intellij.openapi.project.Project;
import com.springmodulith.plugin.model.ModulithDependencyGraph;
import com.springmodulith.plugin.model.ModulithModule;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashMap;
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

        Map<String, ModulithModule> modulesByPackage = new HashMap<>();
        for (ModulithModule module : graph.getModules()) {
            modulesByPackage.put(module.getPackageName(), module);
        }

        List<Cycle> cycles = new ArrayList<>();
        for (Set<String> packageCycle : graph.getCycles()) {
            List<ModulithModule> modules = new ArrayList<>();
            for (String packageName : packageCycle) {
                ModulithModule module = modulesByPackage.get(packageName);
                if (module != null) {
                    modules.add(module);
                }
            }
            if (modules.size() >= 2) {
                cycles.add(new Cycle(modules));
            }
        }

        cycles.sort((left, right) -> left.displayPath().compareToIgnoreCase(right.displayPath()));
        return cycles;
    }

    public record Cycle(@NotNull List<ModulithModule> modules) {
        public Cycle {
            modules = List.copyOf(modules);
        }

        @NotNull
        public String displayPath() {
            if (modules.isEmpty()) return "";
            return String.join(
                    " -> ",
                    modules.stream().map(ModulithModule::getName).toList()
            ) + " -> " + modules.get(0).getName();
        }
    }
}
