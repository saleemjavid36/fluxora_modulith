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
    }

    private State state = new State();

    public static ModulithSettings getInstance(@NotNull Project project) {
        return project.getService(ModulithSettings.class);
    }
    public boolean isInspectCycles() {
        return state.inspectCycles;
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
