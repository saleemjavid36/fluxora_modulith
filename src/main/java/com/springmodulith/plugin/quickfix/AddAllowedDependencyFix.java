package com.springmodulith.plugin.quickfix;

import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.ide.highlighter.JavaFileType;
import com.springmodulith.plugin.resolver.ModulithModuleResolver;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import com.intellij.codeInspection.IntentionAndQuickFixAction;
import com.intellij.openapi.editor.Editor;

public final class AddAllowedDependencyFix
        extends IntentionAndQuickFixAction {

    private final String dependency;
    private final String sourceModulePackage;
    private final String customName;

    public AddAllowedDependencyFix(
            @NotNull String dependency,
            @NotNull String sourceModulePackage) {

        this(
                dependency,
                sourceModulePackage,
                null
        );
    }

    public AddAllowedDependencyFix(
            @NotNull String dependency,
            @NotNull String sourceModulePackage,
            @Nullable String customName) {

        this.dependency = dependency;
        this.sourceModulePackage = sourceModulePackage;
        this.customName = customName;
    }

    @Override
    public @NotNull String getName() {

        if (customName != null
                && !customName.isBlank()) {

            return customName;
        }

        return "Allow dependency '"
                + dependency
                + "'";
    }

    @Override
    public @NotNull String getFamilyName() {
        return "Spring Modulith";
    }

    @Override
    public boolean startInWriteAction() {
        return false;
    }

    @Override
    public void applyFix(
            @NotNull Project project,
            @NotNull PsiFile file,
            @Nullable Editor editor) {

        applyDependencyChange(project);
    }

    private void applyDependencyChange(
            @NotNull Project project) {

        PsiDirectory moduleDirectory =
                findPackageDirectory(
                        project,
                        sourceModulePackage
                );

        if (moduleDirectory == null) {
            return;
        }

        WriteCommandAction
                .writeCommandAction(project)
                .withName("Allow Modulith dependency")
                .run(() -> {

                    PsiJavaFile packageInfo =
                            findPackageInfo(moduleDirectory);

                    if (packageInfo == null) {

                        PsiElementFactory factory =
                                PsiElementFactory
                                        .getInstance(project);

                        createPackageInfo(
                                project,
                                moduleDirectory,
                                factory
                        );

                        packageInfo =
                                findPackageInfo(moduleDirectory);

                        if (packageInfo == null) {
                            return;
                        }
                    }

                    PsiAnnotation applicationModule =
                            findApplicationModule(packageInfo);

                    if (applicationModule != null
                            && applicationModule.isValid()) {

                        addDependencyToAnnotation(
                                project,
                                applicationModule
                        );

                    } else {

                        PsiPackageStatement packageStatement =
                                packageInfo.getPackageStatement();

                        if (packageStatement == null) {
                            return;
                        }

                        addApplicationModuleAnnotation(
                                project,
                                packageStatement
                        );
                    }
                });
    }

    private void addDependencyToAnnotation(
            @NotNull Project project,
            @NotNull PsiAnnotation applicationModule) {

        PsiElementFactory factory =
                PsiElementFactory.getInstance(project);

        PsiAnnotationMemberValue value =
                applicationModule.findDeclaredAttributeValue(
                        "allowedDependencies"
                );

        String annotationText =
                applicationModule.getText();

        /*
         * @ApplicationModule without allowedDependencies.
         *
         * Example:
         *
         * @ApplicationModule
         *
         * becomes:
         *
         * @ApplicationModule(
         *     allowedDependencies = {"user :: repository"}
         * )
         */
        if (value == null) {

            int closeParen =
                    annotationText.lastIndexOf(')');

            if (closeParen < 0) {
                return;
            }

            String newAnnotationText =
                    annotationText.substring(
                            0,
                            closeParen
                    )
                            + "allowedDependencies = {\""
                            + dependency
                            + "\"}"
                            + annotationText.substring(
                            closeParen
                    );

            PsiAnnotation replacement =
                    factory.createAnnotationFromText(
                            newAnnotationText,
                            applicationModule
                    );

            applicationModule.replace(replacement);

            return;
        }

        /*
         * allowedDependencies must be an array.
         */
        if (!(value instanceof PsiArrayInitializerMemberValue)) {
            return;
        }

        PsiArrayInitializerMemberValue arrayValue =
                (PsiArrayInitializerMemberValue) value;

        /*
         * Already configured.
         */
        if (containsDependencyLiteral(arrayValue)) {
            return;
        }

        String valueText =
                arrayValue.getText();

        int closingBrace =
                valueText.lastIndexOf('}');

        if (closingBrace < 0) {
            return;
        }

        String insertion =
                arrayValue.getInitializers().length == 0
                        ? "\""
                        + dependency
                        + "\""
                        : ", \""
                        + dependency
                        + "\"";

        String newValueText =
                valueText.substring(
                        0,
                        closingBrace
                )
                        + insertion
                        + valueText.substring(
                        closingBrace
                );

        int valueStart =
                annotationText.indexOf(valueText);

        if (valueStart < 0) {
            return;
        }

        String newAnnotationText =
                annotationText.substring(
                        0,
                        valueStart
                )
                        + newValueText
                        + annotationText.substring(
                        valueStart + valueText.length()
                );

        PsiAnnotation replacement =
                factory.createAnnotationFromText(
                        newAnnotationText,
                        applicationModule
                );

        applicationModule.replace(replacement);
    }

    private static @Nullable PsiJavaFile findPackageInfo(
            @NotNull PsiDirectory directory) {

        PsiFile file =
                directory.findFile(
                        "package-info.java"
                );

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

    private static @Nullable PsiAnnotation findApplicationModule(
            @NotNull PsiJavaFile javaFile) {

        PsiPackageStatement packageStatement =
                javaFile.getPackageStatement();

        if (packageStatement == null) {
            return null;
        }

        PsiModifierList annotationList =
                packageStatement.getAnnotationList();

        if (annotationList == null) {
            return null;
        }

        for (PsiAnnotation annotation :
                annotationList.getAnnotations()) {

            if (isApplicationModule(annotation)) {
                return annotation;
            }
        }

        return null;
    }

    private static boolean isApplicationModule(
            @NotNull PsiAnnotation annotation) {

        String qualifiedName =
                annotation.getQualifiedName();

        return "ApplicationModule".equals(qualifiedName)
                || "org.springframework.modulith.ApplicationModule"
                .equals(qualifiedName);
    }

    private void addApplicationModuleAnnotation(
            @NotNull Project project,
            @NotNull PsiPackageStatement packageStatement) {

        PsiElementFactory factory =
                PsiElementFactory.getInstance(project);

        PsiAnnotation annotation =
                factory.createAnnotationFromText(
                        "@org.springframework.modulith.ApplicationModule(" +
                                "allowedDependencies = {\"" +
                                dependency +
                                "\"})",
                        packageStatement
                );

        PsiModifierList annotationList =
                packageStatement.getAnnotationList();

        if (annotationList != null) {

            annotationList.addBefore(
                    annotation,
                    annotationList.getFirstChild()
            );

        } else {

            packageStatement.addBefore(
                    annotation,
                    packageStatement.getFirstChild()
            );
        }
    }

    private boolean containsDependencyLiteral(
            @NotNull PsiAnnotationMemberValue value) {

        for (PsiLiteralExpression literal :
                PsiTreeUtil.findChildrenOfType(
                        value,
                        PsiLiteralExpression.class
                )) {

            Object literalValue =
                    literal.getValue();

            if (dependency.equals(literalValue)) {
                return true;
            }
        }

        return false;
    }

    private static PsiDirectory findPackageDirectory(
            @NotNull Project project,
            @NotNull String packageName) {

        return new ModulithModuleResolver(project)
                .findDirectoryForPackage(packageName);
    }

    @Override
    public @NotNull IntentionPreviewInfo generatePreview(
            @NotNull Project project,
            @NotNull ProblemDescriptor previewDescriptor) {

        return IntentionPreviewInfo.EMPTY;
    }
}