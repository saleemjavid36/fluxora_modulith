package com.springmodulith.plugin.configuration;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Service(Service.Level.PROJECT)
@State(name = "SpringModulithSettings", storages = @Storage("spring-modulith.xml"))
public final class ModulithSettings implements PersistentStateComponent<ModulithSettings.State> {
    public static final String DIRECT_SUB_PACKAGES = "DIRECT_SUB_PACKAGES";
    public static final String EXPLICITLY_ANNOTATED = "EXPLICITLY_ANNOTATED";

    public static final class State {
        public String rootPackage = "";
        public String detectionStrategy = DIRECT_SUB_PACKAGES;
        public boolean inspectApiUsage = true;
        public boolean inspectAllowedDependencies = true;
        public boolean inspectEventListeners = true;
        public boolean inspectCycles = true;
        public String excludedPackagePrefixes = "";
        public String additionalModulePackages = "";
        public String dependencyOverrides = "";
    }

    private State state = new State();

    public static ModulithSettings getInstance(@NotNull Project project) {
        return project.getService(ModulithSettings.class);
    }
    public boolean isInspectCycles() {
        return state.inspectCycles;
    }

    @NotNull public java.util.List<String> getExcludedPackagePrefixes() { return csv(state.excludedPackagePrefixes); }
    public void setExcludedPackagePrefixes(@Nullable String value) { state.excludedPackagePrefixes = value == null ? "" : value; }
    @NotNull public java.util.List<String> getAdditionalModulePackages() { return csv(state.additionalModulePackages); }
    public void setAdditionalModulePackages(@Nullable String value) { state.additionalModulePackages = value == null ? "" : value; }
    @NotNull public java.util.List<String> getDependencyOverrides() { return csvLines(state.dependencyOverrides); }
    @NotNull public java.util.Map<String, java.util.Set<String>> getDependencyOverrideMap() {
        java.util.Map<String, java.util.Set<String>> result = new java.util.LinkedHashMap<>();
        for (String line : getDependencyOverrides()) {
            int separator = line.indexOf("=");
            if (separator <= 0) continue;
            String source = line.substring(0, separator).trim();
            if (source.isEmpty()) continue;
            java.util.Set<String> values = new java.util.LinkedHashSet<>();
            for (String value : line.substring(separator + 1).split(";")) {
                if (!value.isBlank()) values.add(value.trim());
            }
            if (!values.isEmpty()) result.put(source, values);
        }
        return java.util.Collections.unmodifiableMap(result);
    }
    public void setDependencyOverrides(@Nullable String value) { state.dependencyOverrides = value == null ? "" : value; }

    private static java.util.List<String> csv(String value) {
        if (value == null || value.isBlank()) return java.util.List.of();
        return java.util.Arrays.stream(value.split(","))
                .map(String::trim).filter(v -> !v.isEmpty()).distinct().toList();
    }
    private static java.util.List<String> csvLines(String value) {
        if (value == null || value.isBlank()) return java.util.List.of();
        return java.util.Arrays.stream(value.split("[\\n,]"))
                .map(String::trim).filter(v -> !v.isEmpty()).distinct().toList();
    }

    @Override @Nullable public State getState() { return state; }
    @Override public void loadState(@NotNull State state) { this.state = state; }

    @NotNull public String getRootPackage() { return state.rootPackage == null ? "" : state.rootPackage.trim(); }
    public void setRootPackage(@Nullable String value) { state.rootPackage = value == null ? "" : value.trim(); }
    @NotNull public String getDetectionStrategy() { return EXPLICITLY_ANNOTATED.equals(state.detectionStrategy) ? EXPLICITLY_ANNOTATED : DIRECT_SUB_PACKAGES; }
    public void setDetectionStrategy(@NotNull String value) { state.detectionStrategy = value; }
    public boolean isInspectApiUsage() { return state.inspectApiUsage; }
    public boolean isInspectAllowedDependencies() { return state.inspectAllowedDependencies; }
    public boolean isInspectEventListeners() { return state.inspectEventListeners; }
    public void stateForUi(
            boolean api,
            boolean deps,
            boolean events) {

        stateForUi(
                api,
                deps,
                events,
                state.inspectCycles
        );
    }

    public void stateForUi(
            boolean api,
            boolean deps,
            boolean events,
            boolean cycles) {

        state.inspectApiUsage = api;
        state.inspectAllowedDependencies = deps;
        state.inspectEventListeners = events;
        state.inspectCycles = cycles;
    }
}
