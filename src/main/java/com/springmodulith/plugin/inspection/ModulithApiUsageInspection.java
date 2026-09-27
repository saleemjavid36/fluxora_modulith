package com.springmodulith.plugin.inspection;

import com.intellij.codeInspection.AbstractBaseJavaLocalInspectionTool;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.openapi.project.Project;
import com.intellij.psi.JavaElementVisitor;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiImportStatement;
import com.intellij.psi.PsiJavaCodeReferenceElement;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiWhiteSpace;
import com.springmodulith.plugin.configuration.ModulithSettings;
import com.springmodulith.plugin.model.ModulithModule;
import com.springmodulith.plugin.quickfix.ExposePackageAsNamedInterfaceFix;
import com.springmodulith.plugin.quickfix.MarkClassNamedInterfaceFix;
import com.springmodulith.plugin.resolver.ModulithModuleResolver;
import org.jetbrains.annotations.NotNull;
import com.intellij.codeInspection.ProblemHighlightType;

import java.util.ArrayList;
import java.util.List;

public final class ModulithApiUsageInspection
        extends AbstractBaseJavaLocalInspectionTool {

    @Override
    public @NotNull PsiElementVisitor buildVisitor(
            @NotNull ProblemsHolder holder,
            boolean isOnTheFly) {

        Project project = holder.getProject();

        if (!ModulithSettings.getInstance(project)
                .isInspectApiUsage()) {

            return PsiElementVisitor.EMPTY_VISITOR;
        }

        ModulithModuleResolver resolver =
                new ModulithModuleResolver(project);

        return new JavaElementVisitor() {

            @Override
            public void visitImportStatement(
                    @NotNull PsiImportStatement statement) {

                PsiJavaCodeReferenceElement reference =
                        statement.getImportReference();

                if (reference != null) {
                    checkReference(
                            reference,
                            holder,
                            resolver
                    );
                }
            }

            @Override
            public void visitReferenceElement(
                    @NotNull PsiJavaCodeReferenceElement reference) {

                /*
                 * Imports are already handled by
                 * visitImportStatement().
                 */
                if (reference.getParent()
                        instanceof PsiImportStatement) {

                    return;
                }

                checkReference(
                        reference,
                        holder,
                        resolver
                );
            }
        };
    }

    private static void checkReference(
            @NotNull PsiJavaCodeReferenceElement reference,
            @NotNull ProblemsHolder holder,
            @NotNull ModulithModuleResolver resolver) {

        PsiElement resolved = reference.resolve();

        /*
         * Ignore unresolved references, methods,
         * fields, packages, etc.
         */
        if (!(resolved instanceof PsiClass targetClass)) {
            return;
        }

        PsiFile sourceFile =
                reference.getContainingFile();

        PsiFile targetFile =
                targetClass.getContainingFile();

        /*
         * Only Java source files participate.
         *
         * This automatically prevents warnings for
         * JDK/third-party classes.
         */
        if (!(sourceFile instanceof PsiJavaFile sourceJavaFile)
                || !(targetFile instanceof PsiJavaFile targetJavaFile)) {

            return;
        }

        String sourcePackage =
                sourceJavaFile.getPackageName();

        String targetPackage =
                targetJavaFile.getPackageName();

        if (sourcePackage.isEmpty()
                || targetPackage.isEmpty()) {

            return;
        }

        ModulithModule sourceModule =
                resolver.resolveModule(
                        sourceJavaFile,
                        sourcePackage
                );

        ModulithModule targetModule =
                resolver.resolveModule(
                        targetJavaFile,
                        targetPackage
                );

        /*
         * If either side is not a recognized module,
         * this is not a Modulith API boundary.
         */
        if (sourceModule == null
                || targetModule == null) {

            return;
        }

        /*
         * Same module -> completely valid.
         */
        if (sourceModule.getPackageName()
                .equals(targetModule.getPackageName())) {

            return;
        }

        String qualifiedName =
                targetClass.getQualifiedName();

        if (qualifiedName == null) {
            return;
        }

        /*
         * Ask the module model whether this type is
         * part of the module's exposed API.
         *
         * This handles:
         *
         * - open modules
         * - base package API
         * - package-level @NamedInterface
         * - class-level @NamedInterface
         */
        if (targetModule.exposes(
                qualifiedName,
                targetPackage)) {

            return;
        }

        /*
         * Different module + internal type
         * = API violation.
         */
        LocalQuickFix[] fixes =
                createApiQuickFixes(
                        targetClass,
                        targetPackage
                );

        PsiElement nameElement =
                reference.getReferenceNameElement();

        if (nameElement == null
                || isSuppressed(reference)) {
            return;
        }

        /*
         * Visible editor highlighting is provided by
         * ModulithBoundaryAnnotator. Using INFORMATION here keeps the
         * inspection result/quick-fix available without changing the
         * Java identifier's foreground color.
         */
        holder.registerProblem(
                nameElement,
                "Modulith API violation: "
                        + sourceModule.getName()
                        + " accesses internal type "
                        + qualifiedName
                        + " from module "
                        + targetModule.getName(),
                ProblemHighlightType.INFORMATION,
                fixes
        );
    }

    private static boolean isSuppressed(
            @NotNull PsiElement element) {

        PsiElement target =
                findSuppressionTarget(element);

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
                    && text.contains("//noinspection ModulithApiUsage");
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

    @NotNull
    private static LocalQuickFix[] createApiQuickFixes(
            @NotNull PsiClass targetClass,
            @NotNull String targetPackage) {

        List<LocalQuickFix> fixes =
                new ArrayList<>();

        String simpleName =
                targetClass.getName();

        /*
         * Offer class-level @NamedInterface.
         */
        if (simpleName != null
                && !simpleName.isEmpty()) {

            fixes.add(
                    new MarkClassNamedInterfaceFix(
                            simpleName
                    )
            );
        }

        /*
         * Offer package-level @NamedInterface.
         */
        fixes.add(
                new ExposePackageAsNamedInterfaceFix(
                        targetPackage
                )
        );

        return fixes.toArray(
                new LocalQuickFix[0]
        );
    }
}