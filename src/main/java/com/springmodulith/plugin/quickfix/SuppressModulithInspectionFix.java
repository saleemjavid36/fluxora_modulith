package com.springmodulith.plugin.quickfix;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.SuppressQuickFix;
import com.intellij.openapi.project.Project;
import com.springmodulith.plugin.inspection.ModulithDependencyInspection;
import org.jetbrains.annotations.NotNull;

/**
 * Delegates suppression to IntelliJ's standard Java suppression providers.
 */
public final class SuppressModulithInspectionFix implements LocalQuickFix {

    @Override
    public @NotNull String getName() {
        return "Suppress inspection";
    }

    @Override
    public @NotNull String getFamilyName() {
        return "Spring Modulith";
    }

    @Override
    public void applyFix(
            @NotNull Project project,
            @NotNull ProblemDescriptor descriptor) {

        if (descriptor.getPsiElement() == null) {
            return;
        }

        SuppressQuickFix[] suppressFixes =
                new ModulithDependencyInspection()
                        .getBatchSuppressActions(descriptor.getPsiElement());

        if (suppressFixes.length == 0) {
            return;
        }

        suppressFixes[0].applyFix(project, descriptor);
    }

    @Override
    public boolean startInWriteAction() {
        return false;
    }
}
