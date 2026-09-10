package com.springmodulith.plugin.inspection;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.codeInspection.AbstractBaseJavaLocalInspectionTool;
import com.intellij.openapi.project.Project;
import com.intellij.psi.JavaElementVisitor;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiJavaCodeReferenceElement;
import com.springmodulith.plugin.analyzer.ModulithDependencyAnalyzer;
import com.springmodulith.plugin.configuration.ModulithSettings;
import com.springmodulith.plugin.quickfix.AddAllowedDependencyFix;
import com.springmodulith.plugin.quickfix.MakeModuleOpenFix;
import com.springmodulith.plugin.resolver.ModulithModuleResolver;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public final class ModulithApiUsageInspection extends AbstractBaseJavaLocalInspectionTool {
    @Override public @NotNull PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
        Project project = holder.getProject();
        if (!ModulithSettings.getInstance(project).isInspectApiUsage()) return PsiElementVisitor.EMPTY_VISITOR;
        ModulithModuleResolver resolver = new ModulithModuleResolver(project);
        ModulithDependencyAnalyzer analyzer = new ModulithDependencyAnalyzer(resolver, project);
        return new JavaElementVisitor() {
            @Override public void visitReferenceElement(@NotNull PsiJavaCodeReferenceElement reference) {
                ModulithDependencyAnalyzer.Dependency dependency = analyzer.analyze(reference);
                if (dependency == null) return;
                PsiClass target = dependency.targetClass();
                String targetType = target.getQualifiedName();
                if (targetType == null) return;
                String targetPackage = target.getContainingFile() instanceof com.intellij.psi.PsiJavaFile file ? file.getPackageName() : "";

                if (!dependency.source().allowsType(targetType, targetPackage)) {
                    List<LocalQuickFix> fixes = new ArrayList<>();
                    String rule = dependency.target().getName();
                    if (targetPackage.equals(dependency.target().getPackageName())) {
                        fixes.add(new AddAllowedDependencyFix(rule, dependency.source().getPackageName()));
                    }
                    holder.registerProblem(reference.getReferenceNameElement(),
                            "Modulith dependency is not allowed: " + dependency.source().getName() + " -> " + dependency.target().getName(),
                            fixes.toArray(new LocalQuickFix[0]));
                    return;
                }

                if (!dependency.target().exposes(targetType, targetPackage)) {
                    List<LocalQuickFix> fixes = List.of(new MakeModuleOpenFix(dependency.target().getPackageName()));
                    holder.registerProblem(reference.getReferenceNameElement(),
                            "Access to non-exposed API: " + target.getName() + " from module '" + dependency.source().getName() + "'",
                            fixes.toArray(new LocalQuickFix[0]));
                }
            }
        };
    }
}
