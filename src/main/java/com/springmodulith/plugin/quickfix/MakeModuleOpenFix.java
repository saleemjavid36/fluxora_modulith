package com.springmodulith.plugin.quickfix;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.project.Project;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiModifierList;
import com.intellij.psi.PsiPackageStatement;
import com.springmodulith.plugin.resolver.ModulithModuleResolver;
import org.jetbrains.annotations.NotNull;

public final class MakeModuleOpenFix
        implements LocalQuickFix {

    private static final String APPLICATION_MODULE =
            ModulithModuleResolver.APPLICATION_MODULE;

    private final String targetPackage;

    public MakeModuleOpenFix(
            @NotNull String targetPackage) {

        this.targetPackage = targetPackage;
    }

    @Override
    public @NotNull String getFamilyName() {
        return "Spring Modulith";
    }

    @Override
    public @NotNull String getName() {
        return "Make module '"
                + targetPackage
                + "' open";
    }

    @Override
    public void applyFix(
            @NotNull Project project,
            @NotNull ProblemDescriptor descriptor) {

        ModulithModuleResolver resolver =
                new ModulithModuleResolver(project);

        PsiDirectory directory =
                resolver.findDirectoryForPackage(
                        targetPackage
                );

        if (directory == null) {
            return;
        }

        WriteCommandAction.runWriteCommandAction(
                project,
                () -> {

                    PsiJavaFile packageInfo =
                            directory.findFile(
                                    "package-info.java"
                            ) instanceof PsiJavaFile file
                                    ? file
                                    : null;

                    if (packageInfo == null) {
                        return;
                    }

                    PsiPackageStatement packageStatement =
                            packageInfo.getPackageStatement();

                    if (packageStatement == null) {
                        return;
                    }

                    PsiModifierList annotationList =
                            packageStatement.getAnnotationList();

                    if (annotationList == null) {
                        return;
                    }

                    PsiAnnotation applicationModule =
                            annotationList.findAnnotation(
                                    APPLICATION_MODULE
                            );

                    if (applicationModule == null) {
                        return;
                    }

                    PsiElementFactory factory =
                            JavaPsiFacade.getElementFactory(
                                    project
                            );

                    /*
                     * Add/change only the "type" attribute.
                     *
                     * This preserves allowedDependencies.
                     */
                    applicationModule
                            .setDeclaredAttributeValue(
                                    "type",
                                    factory.createExpressionFromText(
                                            "org.springframework.modulith.ApplicationModule.Type.OPEN",
                                            applicationModule
                                    )
                            );
                }
        );
    }
}