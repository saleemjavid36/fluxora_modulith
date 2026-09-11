package com.springmodulith.plugin.inspection;

import com.intellij.codeInspection.AbstractBaseJavaLocalInspectionTool;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.springmodulith.plugin.quickfix.ExposePackageAsNamedInterfaceFix;
import com.springmodulith.plugin.quickfix.MarkClassNamedInterfaceFix;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public final class ModulithApiUsageInspection
        extends AbstractBaseJavaLocalInspectionTool {

    private static final String APPLICATION_MODULE =
            "org.springframework.modulith.ApplicationModule";

    private static final String NAMED_INTERFACE =
            "org.springframework.modulith.NamedInterface";

    @Override
    public @NotNull PsiElementVisitor buildVisitor(
            @NotNull ProblemsHolder holder,
            boolean isOnTheFly) {

        Project project = holder.getProject();

        return new JavaElementVisitor() {

            @Override
            public void visitImportStatement(
                    @NotNull PsiImportStatement statement) {

                PsiJavaCodeReferenceElement reference =
                        statement.getImportReference();

                if (reference == null) {
                    return;
                }

                checkReference(
                        reference,
                        holder,
                        project
                );
            }

            @Override
            public void visitReferenceElement(
                    @NotNull PsiJavaCodeReferenceElement reference) {

                if (reference.getParent() instanceof PsiImportStatement) {
                    return;
                }

                checkReference(
                        reference,
                        holder,
                        project
                );
            }
        };
    }

    private static void checkReference(
            @NotNull PsiJavaCodeReferenceElement reference,
            @NotNull ProblemsHolder holder,
            @NotNull Project project) {

        PsiClass targetClass =
                resolveTargetClass(reference);

        if (targetClass == null) {
            return;
        }

        PsiFile sourceFile =
                reference.getContainingFile();

        if (!(sourceFile instanceof PsiJavaFile)) {
            return;
        }

        PsiJavaFile sourceJavaFile =
                (PsiJavaFile) sourceFile;

        PsiJavaFile targetJavaFile =
                getJavaFile(targetClass);

        if (targetJavaFile == null) {
            return;
        }

        String sourcePackage =
                sourceJavaFile.getPackageName();

        String targetPackage =
                targetJavaFile.getPackageName();

        if (sourcePackage == null
                || sourcePackage.isEmpty()
                || targetPackage == null
                || targetPackage.isEmpty()) {
            return;
        }

        String sourceModule =
                findApplicationModule(
                        project,
                        sourcePackage
                );

        String targetModule =
                findApplicationModule(
                        project,
                        targetPackage
                );

        if (sourceModule == null
                || targetModule == null) {
            return;
        }

        /*
         * Same module -> no API violation.
         */
        if (sourceModule.equals(targetModule)) {
            return;
        }

        /*
         * Target is exposed through a named interface -> allowed.
         */
        if (isExposedThroughNamedInterface(
                project,
                targetClass,
                targetPackage,
                targetModule)) {

            return;
        }

        /*
         * Different module + internal type -> violation.
         */
        String qualifiedName =
                targetClass.getQualifiedName();

        if (qualifiedName == null) {
            return;
        }

        LocalQuickFix[] fixes = createApiQuickFixes(
                targetClass,
                targetPackage
        );

        holder.registerProblem(
                reference.getReferenceNameElement(),
                "Modulith API violation: "
                        + sourceModule
                        + " accesses internal type "
                        + qualifiedName
                        + " from module "
                        + targetModule,
                fixes
        );
    }

    private static PsiClass resolveTargetClass(
            @NotNull PsiJavaCodeReferenceElement reference) {

        PsiElement resolved =
                reference.resolve();

        if (resolved instanceof PsiClass) {
            return (PsiClass) resolved;
        }

        return null;
    }

    private static PsiJavaFile getJavaFile(
            @NotNull PsiClass psiClass) {

        PsiFile file =
                psiClass.getContainingFile();

        if (file instanceof PsiJavaFile) {
            return (PsiJavaFile) file;
        }

        return null;
    }

    private static String findApplicationModule(
            @NotNull Project project,
            @NotNull String packageName) {

        String currentPackage =
                packageName;

        while (currentPackage != null
                && !currentPackage.isEmpty()) {

            PsiPackage psiPackage =
                    JavaPsiFacade
                            .getInstance(project)
                            .findPackage(currentPackage);

            if (psiPackage != null
                    && hasApplicationModuleAnnotation(
                    psiPackage)) {

                return currentPackage;
            }

            int lastDot =
                    currentPackage.lastIndexOf('.');

            if (lastDot < 0) {
                break;
            }

            currentPackage =
                    currentPackage.substring(
                            0,
                            lastDot
                    );
        }

        return null;
    }

    private static boolean hasApplicationModuleAnnotation(
            @NotNull PsiPackage psiPackage) {

        for (PsiDirectory directory :
                psiPackage.getDirectories()) {

            PsiFile packageInfo =
                    directory.findFile(
                            "package-info.java"
                    );

            if (!(packageInfo instanceof PsiJavaFile)) {
                continue;
            }

            PsiJavaFile javaFile =
                    (PsiJavaFile) packageInfo;

            PsiPackageStatement packageStatement =
                    javaFile.getPackageStatement();

            if (packageStatement == null) {
                continue;
            }

            PsiModifierList annotationList =
                    packageStatement.getAnnotationList();

            if (annotationList == null) {
                continue;
            }

            PsiAnnotation annotation =
                    annotationList.findAnnotation(APPLICATION_MODULE);

            if (annotation != null) {
                return true;
            }
        }

        return false;
    }

    private static boolean isExposedThroughNamedInterface(
            @NotNull Project project,
            @NotNull PsiClass targetClass,
            @NotNull String targetPackage,
            @NotNull String targetModule) {

        /*
         * Walk from target package towards module root.
         *
         * Example:
         *
         * com.springmodulith.user.api
         *
         * -> com.springmodulith.user
         */
        String currentPackage =
                targetPackage;

        while (currentPackage != null
                && !currentPackage.isEmpty()
                && isSameOrChildPackage(
                currentPackage,
                targetModule)) {

            PsiPackage psiPackage =
                    JavaPsiFacade
                            .getInstance(project)
                            .findPackage(currentPackage);

            if (psiPackage != null
                    && hasNamedInterfaceAnnotation(
                    psiPackage)) {

                return true;
            }

            if (currentPackage.equals(targetModule)) {
                break;
            }

            int lastDot =
                    currentPackage.lastIndexOf('.');

            if (lastDot < 0) {
                break;
            }

            currentPackage =
                    currentPackage.substring(
                            0,
                            lastDot
                    );
        }

        /*
         * Also support a type explicitly annotated
         * with @NamedInterface.
         */
        PsiAnnotation annotation =
                targetClass.getAnnotation(
                        NAMED_INTERFACE
                );

        return annotation != null;
    }

    private static boolean hasNamedInterfaceAnnotation(
            @NotNull PsiPackage psiPackage) {

        for (PsiDirectory directory :
                psiPackage.getDirectories()) {

            PsiFile packageInfo =
                    directory.findFile("package-info.java");

            if (!(packageInfo instanceof PsiJavaFile)) {
                continue;
            }

            PsiJavaFile javaFile =
                    (PsiJavaFile) packageInfo;

            PsiPackageStatement packageStatement =
                    javaFile.getPackageStatement();

            if (packageStatement == null) {
                continue;
            }

            PsiModifierList annotationList =
                    packageStatement.getAnnotationList();

            if (annotationList == null) {
                continue;
            }

            PsiAnnotation annotation =
                    annotationList.findAnnotation(NAMED_INTERFACE);

            if (annotation != null) {
                return true;
            }
        }

        return false;
    }

    private static boolean isSameOrChildPackage(
            @NotNull String packageName,
            @NotNull String modulePackage) {

        return packageName.equals(modulePackage)
                || packageName.startsWith(
                modulePackage + "."
        );
    }

    private static LocalQuickFix[] createApiQuickFixes(
            @NotNull PsiClass targetClass,
            @NotNull String targetPackage) {

        List<LocalQuickFix> fixes = new ArrayList<>();

        String qualifiedName =
                targetClass.getQualifiedName();

        if (qualifiedName != null) {
            String simpleName =
                    targetClass.getName();

            if (simpleName != null
                    && !simpleName.isEmpty()) {

                fixes.add(
                        new MarkClassNamedInterfaceFix(
                                simpleName
                        )
                );
            }
        }

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