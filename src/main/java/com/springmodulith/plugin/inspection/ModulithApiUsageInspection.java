package com.springmodulith.plugin.inspection;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.openapi.project.Project;
import com.intellij.psi.JavaElementVisitor;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiJavaCodeReferenceElement;
import com.intellij.psi.PsiJavaFile;
import com.springmodulith.plugin.analyzer.ModulithDependencyAnalyzer;
import com.springmodulith.plugin.configuration.ModulithSettings;
import com.springmodulith.plugin.model.ModulithModule;
import com.springmodulith.plugin.quickfix.ExposePackageAsNamedInterfaceFix;
import com.springmodulith.plugin.quickfix.MakeModuleOpenFix;
import com.springmodulith.plugin.resolver.ModulithModuleResolver;
import org.jetbrains.annotations.NotNull;

/**
 * Enforces Spring Modulith's API boundary rule for cross-module references.
 *
 * <p>A closed module exposes its base package and explicitly declared named
 * interfaces. References into other packages are reported unless the target
 * module is open.</p>
 */
public final class ModulithApiUsageInspection extends com.intellij.codeInspection.AbstractBaseJavaLocalInspectionTool {
    @Override
    public @NotNull PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
        Project project = holder.getProject();
        if (!ModulithSettings.getInstance(project).isInspectApiUsage()) {
            return PsiElementVisitor.EMPTY_VISITOR;
        }

        ModulithDependencyAnalyzer analyzer =
                new ModulithDependencyAnalyzer(new ModulithModuleResolver(project), project);

        return new JavaElementVisitor() {
            @Override
            public void visitReferenceElement(@NotNull PsiJavaCodeReferenceElement reference) {
                checkReference(reference, analyzer, holder);
            }
        };
    }

    private static void checkReference(
            @NotNull PsiJavaCodeReferenceElement reference,
            @NotNull ModulithDependencyAnalyzer analyzer,
            @NotNull ProblemsHolder holder) {

        ModulithDependencyAnalyzer.Dependency dependency = analyzer.analyze(reference);
        if (dependency == null) return;

        ModulithModule targetModule = dependency.target();
        if (targetModule.isOpen()) return;

        PsiClass targetClass = dependency.targetClass();
        String qualifiedType = targetClass.getQualifiedName();
        if (qualifiedType == null) return;

        String targetPackage = getPackageName(targetClass);
        if (targetPackage.isEmpty()) return;

        if (targetModule.exposes(qualifiedType, targetPackage)) return;

        LocalQuickFix[] fixes = {
                new ExposePackageAsNamedInterfaceFix(targetPackage),
                new MakeModuleOpenFix(targetModule.getPackageName())
        };

        holder.registerProblem(
                reference.getReferenceNameElement(),
                "Access to non-exposed API: " + targetClass.getName()
                        + " from module '" + dependency.source().getName() + "'",
                fixes);
    }

    @NotNull
    private static String getPackageName(@NotNull PsiClass psiClass) {
        if (!(psiClass.getContainingFile() instanceof PsiJavaFile javaFile)) return "";
        return javaFile.getPackageName();
    }
}
