package com.springmodulith.plugin.inspection;

import com.intellij.codeInspection.AbstractBaseJavaLocalInspectionTool;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.openapi.project.Project;
import com.intellij.psi.JavaElementVisitor;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiImportStatement;
import com.intellij.psi.PsiJavaCodeReferenceElement;
import com.springmodulith.plugin.analyzer.ModulithDependencyAnalyzer;
import com.springmodulith.plugin.configuration.ModulithSettings;
import com.springmodulith.plugin.model.ModulithDependencyAnalysis;
import com.springmodulith.plugin.model.ModulithModule;
import com.springmodulith.plugin.model.NamedInterface;
import com.springmodulith.plugin.quickfix.AddAllowedDependencyFix;
import com.springmodulith.plugin.quickfix.ExposePackageAsNamedInterfaceFix;
import com.springmodulith.plugin.quickfix.NavigateToModuleFix;
import com.springmodulith.plugin.quickfix.SuppressModulithInspectionFix;
import com.springmodulith.plugin.resolver.ModulithModuleResolver;
import org.jetbrains.annotations.NotNull;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiWhiteSpace;

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

        ModulithDependencyAnalysis dependency =
                analyzer.analyze(reference);

        if (dependency == null
                || !dependency.isForbidden()) {
            return;
        }

        String message =
                analyzer.getMessage(dependency);

        if (message == null) {
            return;
        }

        LocalQuickFix[] fixes =
                createQuickFixes(dependency);

        PsiElement nameElement =
                reference.getReferenceNameElement();

        if (nameElement == null
                || isSuppressed(reference)) {
            return;
        }

        /*
         * The editor underline is rendered by ModulithBoundaryAnnotator.
         * Keep this inspection available for inspection results and
         * quick-fix discovery without applying IntelliJ's ERROR text
         * attributes, which turn the Java identifier itself red.
         */
        holder.registerProblem(
                nameElement,
                message,
                ProblemHighlightType.INFORMATION,
                fixes
        );
    }

    @NotNull
    private static LocalQuickFix[] createQuickFixes(
            @NotNull ModulithDependencyAnalysis dependency) {

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
         * API exposure has priority.
         *
         * If the accessed type is outside the target module's exposed API,
         * adding a broad module dependency does not make that type legal.
         * Expose the accessed package first.
         *
         * Example:
         *     teacher -> student.dto.StudentDto
         *
         * Fix:
         *     Expose package 'org.example.student.dto'
         *     as named interface 'dto'
         */
        if (dependency.apiViolation()) {
            fixes.add(
                    new ExposePackageAsNamedInterfaceFix(
                            targetPackage
                    )
            );
        }

        /*
         * Preserve the precise dependency quick fix for already-exposed
         * named interfaces.
         *
         * Example:
         *     student :: repository
         *
         * must never be reduced to the broader:
         *     student
         */
        NamedInterface accessedNamedInterface =
                target.findNamedInterfaceForType(
                        targetType,
                        targetPackage
                );

        if (accessedNamedInterface != null) {

            String namedDependency =
                    target.getName()
                            + " :: "
                            + accessedNamedInterface.getName();

            if (!hasAllowedDependency(
                    source,
                    namedDependency
            )) {
                fixes.add(
                        new AddAllowedDependencyFix(
                                namedDependency,
                                source.getPackageName(),
                                "Add dependency '" + namedDependency
                                        + "' to @ApplicationModule"
                        )
                );
            }

        } else if (targetPackage.equals(
                target.getPackageName()
        )) {

            /*
             * Only root-package types use the module-level dependency.
             * Internal packages must be exposed instead of being granted
             * the broad module dependency.
             */
            String moduleDependency = target.getName();

            if (!hasAllowedDependency(
                    source,
                    moduleDependency
            )) {
                fixes.add(
                        new AddAllowedDependencyFix(
                                moduleDependency,
                                source.getPackageName(),
                                "Add dependency '" + moduleDependency
                                        + "' to @ApplicationModule"
                        )
                );
            }
        }

        /*
         * Navigation is deliberately read-only and never changes the
         * source project.
         */
        fixes.add(
                new NavigateToModuleFix(target.getPackageName())
        );

        /*
         * Keep suppression for genuine dependency violations. When the
         * module dependency is already allowed and the remaining violation
         * is only API exposure, the editor should offer the concrete
         * exposure action without adding a suppression action.
         */
        boolean moduleDependencyAllowed = false;
        if (source.isAllowedDependenciesConfigured()) {
            for (String configuredDependency : source.getAllowedDependencies()) {
                ModulithModule.DependencyRule rule =
                        ModulithModule.DependencyRule.parse(configuredDependency);
                if (rule != null
                        && rule.interfaceId() == null
                        && target.matchesModuleId(rule.moduleId())) {
                    moduleDependencyAllowed = true;
                    break;
                }
            }
        } else {
            moduleDependencyAllowed = true;
        }

        if (!dependency.apiViolation() || !moduleDependencyAllowed) {
            fixes.add(new SuppressModulithInspectionFix("ModulithDependency"));
        }

        return fixes.toArray(new LocalQuickFix[0]);
    }
    private static boolean isSuppressed(
            @NotNull PsiElement element) {

        PsiElement target = findSuppressionTarget(element);

        if (target == null) {
            return false;
        }

        PsiElement previous = target.getPrevSibling();

        while (previous != null) {
            if (previous instanceof PsiWhiteSpace) {
                previous = previous.getPrevSibling();
                continue;
            }

            String text = previous.getText();

            return text != null
                    && text.contains("//noinspection ModulithDependency");
        }

        return false;
    }

    @NotNull
    private static PsiElement findSuppressionTarget(
            @NotNull PsiElement element) {

        PsiImportStatement importStatement =
                com.intellij.psi.util.PsiTreeUtil.getParentOfType(
                        element,
                        PsiImportStatement.class
                );

        if (importStatement != null) {
            return importStatement;
        }

        PsiElement field =
                com.intellij.psi.util.PsiTreeUtil.getParentOfType(
                        element,
                        com.intellij.psi.PsiField.class
                );

        if (field != null) {
            return field;
        }

        PsiElement method =
                com.intellij.psi.util.PsiTreeUtil.getParentOfType(
                        element,
                        com.intellij.psi.PsiMethod.class
                );

        if (method != null) {
            return method;
        }

        return element;
    }

    private static boolean hasAllowedDependency(
            @NotNull ModulithModule source,
            @NotNull String dependency) {

        ModulithModule.DependencyRule expected =
                ModulithModule.DependencyRule.parse(dependency);

        if (expected == null) {
            return false;
        }

        for (String configuredDependency :
                source.getAllowedDependencies()) {

            ModulithModule.DependencyRule configured =
                    ModulithModule.DependencyRule.parse(
                            configuredDependency
                    );

            if (configured == null
                    || !expected.moduleId().equals(
                    configured.moduleId()
            )) {
                continue;
            }

            if (expected.interfaceId() == null) {
                return configured.interfaceId() == null;
            }

            if (expected.interfaceId().equals(
                    configured.interfaceId()
            )) {
                return true;
            }
        }

        return false;
    }

}