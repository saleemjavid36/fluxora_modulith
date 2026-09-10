package com.springmodulith.plugin.inspection;

import com.intellij.codeInspection.AbstractBaseJavaLocalInspectionTool;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.openapi.project.Project;
import com.intellij.psi.JavaElementVisitor;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiImportStatement;
import com.intellij.psi.PsiJavaCodeReferenceElement;
import com.springmodulith.plugin.analyzer.ModulithDependencyAnalyzer;
import com.springmodulith.plugin.configuration.ModulithSettings;
import com.springmodulith.plugin.resolver.ModulithModuleResolver;
import org.jetbrains.annotations.NotNull;

public final class ModulithDependencyInspection
        extends AbstractBaseJavaLocalInspectionTool {

    @Override
    public @NotNull PsiElementVisitor buildVisitor(
            @NotNull ProblemsHolder holder,
            boolean isOnTheFly) {

        Project project = holder.getProject();

        if (!ModulithSettings.getInstance(project)
                .isInspectAllowedDependencies()) {
            return PsiElementVisitor.EMPTY_VISITOR;
        }

        ModulithDependencyAnalyzer analyzer =
                new ModulithDependencyAnalyzer(
                        new ModulithModuleResolver(project),
                        project
                );

        return new JavaElementVisitor() {

            @Override
            public void visitImportStatement(
                    @NotNull PsiImportStatement statement) {

                PsiJavaCodeReferenceElement reference =
                        statement.getImportReference();

                if (reference != null) {
                    registerViolation(reference, analyzer, holder);
                }
            }

            @Override
            public void visitReferenceElement(
                    @NotNull PsiJavaCodeReferenceElement reference) {

                if (reference.getParent() instanceof PsiImportStatement) {
                    return;
                }

                registerViolation(reference, analyzer, holder);
            }
        };
    }

    private static void registerViolation(
            @NotNull PsiJavaCodeReferenceElement reference,
            @NotNull ModulithDependencyAnalyzer analyzer,
            @NotNull ProblemsHolder holder) {

        String message = analyzer.getMessage(reference);

        if (message == null) {
            return;
        }

        holder.registerProblem(
                reference.getReferenceNameElement(),
                message
        );
    }
}