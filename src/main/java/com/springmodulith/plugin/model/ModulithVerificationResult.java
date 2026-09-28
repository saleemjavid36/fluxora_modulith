package com.springmodulith.plugin.model;

import org.jetbrains.annotations.NotNull;

import java.util.List;

public record ModulithVerificationResult(
        @NotNull List<ModulithDependencyAnalysis> dependencyViolations,
        @NotNull List<String> cycles,
        @NotNull List<ModulithDependencyAnalysis> allDependencies) {
    public ModulithVerificationResult {
        dependencyViolations = List.copyOf(dependencyViolations);
        cycles = List.copyOf(cycles);
        allDependencies = List.copyOf(allDependencies);
    }

    public int violationCount() {
        return dependencyViolations.size() + cycles.size();
    }
}
