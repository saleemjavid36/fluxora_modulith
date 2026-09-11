package com.springmodulith.plugin.quickfix;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.project.Project;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiAnnotationMemberValue;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.PsiFileFactory;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiPackageStatement;
import com.intellij.psi.PsiModifierList;
import com.intellij.ide.highlighter.JavaFileType;
import com.springmodulith.plugin.resolver.ModulithModuleResolver;
import org.jetbrains.annotations.NotNull;

public final class AddAllowedDependencyFix implements LocalQuickFix {

    private final String dependency;
    private final String sourceModulePackage;

    public AddAllowedDependencyFix(
            @NotNull String dependency,
            @NotNull String sourceModulePackage) {

        this.dependency = dependency;
        this.sourceModulePackage = sourceModulePackage;
    }

    @Override
    public @NotNull String getFamilyName() {
        return "Spring Modulith";
    }

    @Override
    public @NotNull String getName() {
        return "Allow dependency '" + dependency + "'";
    }

    @Override
    public void applyFix(
            @NotNull Project project,
            @NotNull ProblemDescriptor descriptor) {

        PsiDirectory directory =
                findPackageDirectory(project, sourceModulePackage);

        if (directory == null) {
            return;
        }

        WriteCommandAction.runWriteCommandAction(project, () -> {

            PsiElementFactory factory =
                    JavaPsiFacade.getElementFactory(project);

            PsiJavaFile packageInfo =
                    findPackageInfo(directory);

            if (packageInfo == null) {
                createPackageInfo(
                        project,
                        directory,
                        factory
                );
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
                    findApplicationModule(annotationList);

            if (applicationModule == null) {
                addApplicationModuleAnnotation(
                        factory,
                        annotationList
                );
                return;
            }

            addDependencyToAnnotation(
                    factory,
                    applicationModule
            );
        });
    }

    private static PsiJavaFile findPackageInfo(
            @NotNull PsiDirectory directory) {

        PsiElement file = directory.findFile("package-info.java");

        if (file instanceof PsiJavaFile) {
            return (PsiJavaFile) file;
        }

        return null;
    }

    private void createPackageInfo(
            @NotNull Project project,
            @NotNull PsiDirectory directory,
            @NotNull PsiElementFactory factory) {

        String text =
                "@org.springframework.modulith.ApplicationModule(" +
                        "allowedDependencies = {\"" +
                        dependency +
                        "\"})\n" +
                        "package " +
                        sourceModulePackage +
                        ";\n";

        PsiJavaFile packageInfo =
                (PsiJavaFile) PsiFileFactory
                        .getInstance(project)
                        .createFileFromText(
                                "package-info.java",
                                JavaFileType.INSTANCE,
                                text
                        );

        directory.add(packageInfo);
    }

    private static PsiAnnotation findApplicationModule(
            @NotNull PsiModifierList annotationList) {

        for (PsiAnnotation annotation :
                annotationList.getAnnotations()) {

            if ("org.springframework.modulith.ApplicationModule"
                    .equals(annotation.getQualifiedName())) {

                return annotation;
            }
        }

        return null;
    }

    private static void addApplicationModuleAnnotation(
            @NotNull PsiElementFactory factory,
            @NotNull PsiModifierList annotationList) {

        PsiAnnotation annotation =
                factory.createAnnotationFromText(
                        "@org.springframework.modulith.ApplicationModule(" +
                                "allowedDependencies = {})",
                        annotationList
                );

        annotationList.addBefore(
                annotation,
                annotationList.getFirstChild()
        );
    }

    private void addDependencyToAnnotation(
            @NotNull PsiElementFactory factory,
            @NotNull PsiAnnotation annotation) {

        PsiAnnotationMemberValue value =
                annotation.findDeclaredAttributeValue(
                        "allowedDependencies"
                );

        if (value == null) {
            return;
        }

        String valueText = value.getText();

        if (!valueText.trim().startsWith("{")
                || !valueText.trim().endsWith("}")) {
            return;
        }

        if (containsDependency(valueText)) {
            return;
        }

        int closingBrace =
                valueText.lastIndexOf('}');

        if (closingBrace < 0) {
            return;
        }

        String beforeClosingBrace =
                valueText.substring(0, closingBrace);

        String afterClosingBrace =
                valueText.substring(closingBrace);

        String insertion;

        if (beforeClosingBrace.trim().equals("{")) {
            insertion =
                    "\n        \"" +
                            dependency +
                            "\"\n    ";
        } else {
            insertion =
                    ",\n        \"" +
                            dependency +
                            "\"\n    ";
        }

        String newValueText =
                beforeClosingBrace +
                        insertion +
                        afterClosingBrace;

        PsiAnnotation tempAnnotation =
                factory.createAnnotationFromText(
                        "@org.springframework.modulith.ApplicationModule(" +
                                "allowedDependencies = " +
                                newValueText +
                                ")",
                        annotation
                );

        PsiAnnotationMemberValue newValue =
                tempAnnotation.findDeclaredAttributeValue(
                        "allowedDependencies"
                );

        if (newValue != null) {
            value.replace(newValue);
        }
    }

    private boolean containsDependency(
            @NotNull String valueText) {

        String quotedDependency =
                "\"" + dependency + "\"";

        return valueText.contains(quotedDependency);
    }

    private static String detectIndentation(
            @NotNull String text) {

        int lastNewLine = text.lastIndexOf('\n');

        if (lastNewLine < 0) {
            return "";
        }

        String afterNewLine =
                text.substring(lastNewLine + 1);

        if (afterNewLine.trim().isEmpty()) {
            return afterNewLine;
        }

        return "    ";
    }

    private static PsiDirectory findPackageDirectory(
            @NotNull Project project,
            @NotNull String packageName) {

        return new ModulithModuleResolver(project)
                .findDirectoryForPackage(packageName);
    }
}