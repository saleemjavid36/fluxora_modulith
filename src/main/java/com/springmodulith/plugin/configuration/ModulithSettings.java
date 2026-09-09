package com.springmodulith.plugin.configuration;

import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class ModulithSettings {

    private String rootPackage;

    private ModulithSettings() {
    }

    @NotNull
    public static ModulithSettings getInstance(@NotNull Project project) {
        return project.getService(ModulithSettings.class);
    }

    @Nullable
    public String getRootPackage() {
        return rootPackage;
    }

    public void setRootPackage(@Nullable String rootPackage) {
        this.rootPackage = rootPackage;
    }
}