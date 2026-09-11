package com.springmodulith.plugin.inspection;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.codeInspection.AbstractBaseJavaLocalInspectionTool;
import com.intellij.openapi.project.Project;
import com.intellij.psi.JavaElementVisitor;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiImportStatement;
import com.intellij.psi.PsiJavaCodeReferenceElement;
import com.intellij.psi.PsiClass;
import com.springmodulith.plugin.analyzer.ModulithDependencyAnalyzer;
import com.springmodulith.plugin.configuration.ModulithSettings;
import com.springmodulith.plugin.model.ModulithModule;
import com.springmodulith.plugin.model.NamedInterface;
import com.springmodulith.plugin.quickfix.AddAllowedDependencyFix;
import com.springmodulith.plugin.resolver.ModulithModuleResolver;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

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

        ModulithModuleResolver resolver =
                new ModulithModuleResolver(project);

        ModulithDependencyAnalyzer analyzer =
                new ModulithDependencyAnalyzer(resolver, project);

        return new JavaElementVisitor() {

            @Override
            public void visitImportStatement(
                    @NotNull PsiImportStatement statement) {

                PsiJavaCodeReferenceElement reference =
                        statement.getImportReference();

                if (reference != null) {
                    registerViolation(
                            reference,
                            analyzer,
                            holder
                    );
                }
            }

            @Override
            public void visitReferenceElement(
                    @NotNull PsiJavaCodeReferenceElement reference) {

                if (reference.getParent() instanceof PsiImportStatement) {
                    return;
                }

                registerViolation(
                        reference,
                        analyzer,
                        holder
                );
            }
        };
    }

    private static void registerViolation(
            @NotNull PsiJavaCodeReferenceElement reference,
            @NotNull ModulithDependencyAnalyzer analyzer,
            @NotNull ProblemsHolder holder) {

        ModulithDependencyAnalyzer.Dependency dependency =
                analyzer.analyze(reference);

        if (dependency == null) {
            return;
        }

        String message = analyzer.getMessage(reference);

        if (message == null) {
            return;
        }

        LocalQuickFix[] fixes =
                createQuickFixes(dependency);

        holder.registerProblem(
                reference.getReferenceNameElement(),
                message,
                fixes
        );
    }

    @NotNull
    private static LocalQuickFix[] createQuickFixes(
            @NotNull ModulithDependencyAnalyzer.Dependency dependency) {

        ModulithModule source = dependency.source();
        ModulithModule target = dependency.target();
        PsiClass targetClass = dependency.targetClass();

        String targetType = targetClass.getQualifiedName();

        if (targetType == null) {
            return new LocalQuickFix[0];
        }

        String targetPackage = "";

        if (targetClass.getContainingFile()
                instanceof com.intellij.psi.PsiJavaFile javaFile) {
            targetPackage = javaFile.getPackageName();
        }

        List<LocalQuickFix> fixes = new ArrayList<>();

        /*
         * Allow the complete target module.
         *
         * Example:
         *
         * "user"
         */
        fixes.add(
                new AddAllowedDependencyFix(
                        target.getName(),
                        source.getPackageName()
                )
        );

        /*
         * Allow a named interface only when the referenced
         * type/package actually belongs to that interface.
         *
         * Example:
         *
         * "user :: api"
         */
        for (NamedInterface namedInterface :
                target.getNamedInterfaces()) {

            if (namedInterface.containsType(targetType)
                    || namedInterface.containsPackage(targetPackage)) {

                String namedDependency =
                        target.getName()
                                + " :: "
                                + namedInterface.getName();

                fixes.add(
                        new AddAllowedDependencyFix(
                                namedDependency,
                                source.getPackageName()
                        )
                );
            }
        }

        return fixes.toArray(new LocalQuickFix[0]);
    }
}