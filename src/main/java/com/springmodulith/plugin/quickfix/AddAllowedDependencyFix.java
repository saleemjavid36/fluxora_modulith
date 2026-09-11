package com.springmodulith.plugin.quickfix;

import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.ide.highlighter.JavaFileType;
import com.springmodulith.plugin.resolver.ModulithModuleResolver;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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
    public boolean startInWriteAction() {
        return false;
    }

    @Override
    public void applyFix(
            @NotNull Project project,
            @NotNull ProblemDescriptor descriptor) {

        PsiDirectory moduleDirectory =
                findPackageDirectory(project, sourceModulePackage);

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
                                PsiElementFactory.getInstance(project);

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

    private void addDependency(
            @NotNull Project project,
            @NotNull PsiAnnotation applicationModule) {

        PsiElementFactory factory =
                PsiElementFactory.getInstance(project);

        String annotationText =
                applicationModule.getText();

        PsiNameValuePair allowedAttribute =
                findAttribute(
                        applicationModule,
                        "allowedDependencies"
                );

        String dependencyLiteral =
                "\"" + dependency + "\"";

        String newAnnotationText;

        if (allowedAttribute == null) {

            int openParen =
                    annotationText.indexOf('(');

            int closeParen =
                    annotationText.lastIndexOf(')');

            if (openParen < 0) {

                newAnnotationText =
                        annotationText +
                                "(allowedDependencies = {" +
                                dependencyLiteral +
                                "})";

            } else if (closeParen > openParen) {

                String arguments =
                        annotationText.substring(
                                openParen + 1,
                                closeParen
                        ).trim();

                String newArguments =
                        arguments.isEmpty()
                                ? "allowedDependencies = {" +
                                dependencyLiteral +
                                "}"
                                : arguments +
                                ", allowedDependencies = {" +
                                dependencyLiteral +
                                "}";

                newAnnotationText =
                        annotationText.substring(
                                0,
                                openParen + 1
                        ) +
                                newArguments +
                                annotationText.substring(
                                        closeParen
                                );

            } else {
                return;
            }

        } else {

            PsiAnnotationMemberValue value =
                    allowedAttribute.getValue();

            if (!(value instanceof PsiArrayInitializerMemberValue)) {
                return;
            }

            PsiArrayInitializerMemberValue arrayValue =
                    (PsiArrayInitializerMemberValue) value;

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

            boolean hasElements =
                    arrayValue.getInitializers().length > 0;

            String insertion =
                    hasElements
                            ? ", " + dependencyLiteral
                            : dependencyLiteral;

            String newValueText =
                    valueText.substring(
                            0,
                            closingBrace
                    ) +
                            insertion +
                            valueText.substring(
                                    closingBrace
                            );

            int valueStart =
                    annotationText.indexOf(valueText);

            if (valueStart < 0) {
                return;
            }

            newAnnotationText =
                    annotationText.substring(
                            0,
                            valueStart
                    ) +
                            newValueText +
                            annotationText.substring(
                                    valueStart + valueText.length()
                            );
        }

        PsiAnnotation replacement =
                factory.createAnnotationFromText(
                        newAnnotationText,
                        applicationModule
                );

        applicationModule.replace(replacement);
    }

    private static PsiNameValuePair findAttribute(
            @NotNull PsiAnnotation annotation,
            @NotNull String name) {

        for (PsiNameValuePair attribute :
                annotation.getParameterList()
                        .getAttributes()) {

            if (name.equals(attribute.getName())) {
                return attribute;
            }
        }

        return null;
    }


    private static @Nullable PsiJavaFile findPackageInfo(
            @NotNull PsiDirectory directory) {

        PsiFile file = directory.findFile("package-info.java");

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

        String qualifiedName = annotation.getQualifiedName();

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

    private void addDependencyToAnnotation(
            @NotNull Project project,
            @NotNull PsiAnnotation annotation) {

        PsiElementFactory factory =
                PsiElementFactory.getInstance(project);

        PsiAnnotationMemberValue value =
                annotation.findDeclaredAttributeValue(
                        "allowedDependencies"
                );

        String annotationText = annotation.getText();

        if (value == null) {
            int closeParen =
                    annotationText.lastIndexOf(')');

            if (closeParen < 0) {
                return;
            }

            String newAnnotationText =
                    annotationText.substring(0, closeParen)
                            + "allowedDependencies = {\""
                            + dependency
                            + "\"}"
                            + annotationText.substring(closeParen);

            PsiAnnotation replacement =
                    factory.createAnnotationFromText(
                            newAnnotationText,
                            annotation
                    );

            annotation.replace(replacement);
            return;
        }

        if (!(value instanceof PsiArrayInitializerMemberValue)) {
            return;
        }

        PsiArrayInitializerMemberValue arrayValue =
                (PsiArrayInitializerMemberValue) value;

        if (containsDependencyLiteral(arrayValue)) {
            return;
        }

        String valueText = arrayValue.getText();

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
                valueText.substring(0, closingBrace)
                        + insertion
                        + valueText.substring(closingBrace);

        String annotationValueText =
                annotationText.substring(
                        0,
                        annotationText.indexOf(valueText)
                )
                        + newValueText
                        + annotationText.substring(
                        annotationText.indexOf(valueText)
                                + valueText.length()
                );

        PsiAnnotation replacement =
                factory.createAnnotationFromText(
                        annotationValueText,
                        annotation
                );

        annotation.replace(replacement);
    }

    private void addMissingAllowedDependenciesAttribute(
            @NotNull PsiElementFactory factory,
            @NotNull PsiAnnotation annotation) {

        String annotationText = annotation.getText();
        int openParen = annotationText.indexOf('(');
        int closeParen = annotationText.lastIndexOf(')');

        if (openParen < 0) {
            String newAnnotationText =
                    annotationText +
                            "(allowedDependencies = {\"" +
                            dependency +
                            "\"})";

            PsiAnnotation replacement =
                    factory.createAnnotationFromText(
                            newAnnotationText,
                            annotation
                    );

            annotation.replace(replacement);
            return;
        }

        if (closeParen <= openParen) {
            return;
        }

        String arguments =
                annotationText.substring(
                        openParen + 1,
                        closeParen
                ).trim();

        String dependencyAttribute =
                "allowedDependencies = {\"" +
                        dependency +
                        "\"}";

        String newArguments =
                arguments.isEmpty()
                        ? dependencyAttribute
                        : arguments + ", " + dependencyAttribute;

        String newAnnotationText =
                annotationText.substring(0, openParen + 1) +
                        newArguments +
                        annotationText.substring(closeParen);

        PsiAnnotation replacement =
                factory.createAnnotationFromText(
                        newAnnotationText,
                        annotation
                );

        annotation.replace(replacement);
    }

    private boolean containsDependencyLiteral(
            @NotNull PsiAnnotationMemberValue value) {

        for (PsiLiteralExpression literal :
                PsiTreeUtil.findChildrenOfType(
                        value,
                        PsiLiteralExpression.class
                )) {

            Object literalValue = literal.getValue();

            if (dependency.equals(literalValue)) {
                return true;
            }
        }

        return false;
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

    @Override
    public @NotNull IntentionPreviewInfo generatePreview(
            @NotNull Project project,
            @NotNull ProblemDescriptor previewDescriptor) {

        return IntentionPreviewInfo.EMPTY;
    }
}