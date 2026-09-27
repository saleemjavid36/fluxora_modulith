package com.springmodulith.plugin.quickfix;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.springmodulith.plugin.model.ModulithModule;
import com.springmodulith.plugin.resolver.ModulithModuleResolver;
import com.springmodulith.plugin.toolwindow.ModulithModuleNavigation;
import org.jetbrains.annotations.NotNull;

/**
 * Opens the target module's package-info.java or first Java source file.
 * This action is intentionally read-only.
 */
public final class NavigateToModuleFix implements LocalQuickFix {
    private final String packageName;

    public NavigateToModuleFix(@NotNull String packageName) {
        this.packageName = packageName;
    }

    @Override
    public @NotNull String getName() {
        return "Navigate to module '" + moduleName() + "'";
    }

    @Override
    public @NotNull String getFamilyName() {
        return "Spring Modulith";
    }

    @Override
    public boolean startInWriteAction() {
        return false;
    }

    @Override
    public void applyFix(
            @NotNull Project project,
            @NotNull ProblemDescriptor descriptor) {

        ModulithModule target = new ModulithModuleResolver(project)
                .resolveModules()
                .stream()
                .filter(module -> module.getPackageName().equals(packageName))
                .findFirst()
                .orElse(null);

        if (target != null) {
            ModulithModuleNavigation.openPackage(project, target);
        }
    }

    private String moduleName() {
        int separator = packageName.lastIndexOf('.');
        return separator >= 0
                ? packageName.substring(separator + 1)
                : packageName;
    }

}
