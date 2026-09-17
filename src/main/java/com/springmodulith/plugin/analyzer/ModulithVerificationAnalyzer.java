package com.springmodulith.plugin.analyzer;

import com.intellij.openapi.project.Project;
import com.springmodulith.plugin.model.ModulithDependencyAnalysis;
import com.springmodulith.plugin.model.ModulithVerificationResult;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public final class ModulithVerificationAnalyzer {
    private final Project project;

    public ModulithVerificationAnalyzer(@NotNull Project project) {
        this.project = project;
    }

    @NotNull
    public ModulithVerificationResult verify() {
        ModulithDependencyAnalyzer analyzer =
                new ModulithDependencyAnalyzer(
                        new com.springmodulith.plugin.resolver.ModulithModuleResolver(project),
                        project);
        List<ModulithDependencyAnalysis> violations = new ArrayList<>();
        for (ModulithDependencyAnalysis analysis : analyzer.analyzeProject()) {
            if (analysis.isForbidden()) violations.add(analysis);
        }

        List<String> cycles = new ModulithCycleAnalyzer(project).findCycles().stream()
                .map(ModulithCycleAnalyzer.Cycle::displayPath)
                .toList();
        return new ModulithVerificationResult(violations, cycles);
    }
}
