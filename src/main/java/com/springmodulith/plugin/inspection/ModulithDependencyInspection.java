package com.springmodulith.plugin.inspection;

import com.intellij.codeInspection.AbstractBaseJavaLocalInspectionTool;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.openapi.project.Project;
import com.intellij.psi.JavaElementVisitor;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiImportStatement;
import com.springmodulith.plugin.analyzer.ModulithDependencyAnalyzer;
import com.springmodulith.plugin.resolver.ModulithModuleResolver;
import org.jetbrains.annotations.NotNull;

public final class ModulithDependencyInspection
        extends AbstractBaseJavaLocalInspectionTool {

    @Override
    public @NotNull PsiElementVisitor buildVisitor(
            @NotNull ProblemsHolder holder,
            boolean isOnTheFly) {

        Project project =
                holder.getProject();

        ModulithModuleResolver moduleResolver =
                new ModulithModuleResolver(project);

        ModulithDependencyAnalyzer analyzer =
                new ModulithDependencyAnalyzer(
                        moduleResolver
                );

        return new JavaElementVisitor() {

            @Override
            public void visitImportStatement(
                    @NotNull PsiImportStatement importStatement) {

                if (!analyzer.isCrossModuleDependency(
                        importStatement)) {

                    return;
                }

                String message =
                        analyzer.getDependencyMessage(
                                importStatement
                        );

                if (message == null) {
                    return;
                }

                holder.registerProblem(
                        importStatement,
                        message
                );
            }
        };
    }
}