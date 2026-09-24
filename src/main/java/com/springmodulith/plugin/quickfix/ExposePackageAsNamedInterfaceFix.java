package com.springmodulith.plugin.quickfix;

import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo;
import com.intellij.codeInspection.IntentionAndQuickFixAction;
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileFactory;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiModifierList;
import com.intellij.psi.PsiPackageStatement;
import com.springmodulith.plugin.resolver.ModulithModuleResolver;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class ExposePackageAsNamedInterfaceFix
        extends IntentionAndQuickFixAction {

    private static final String NAMED_INTERFACE =
            ModulithModuleResolver.NAMED_INTERFACE;

    private final String packageName;
    private final String interfaceName;

    public ExposePackageAsNamedInterfaceFix(
            @NotNull String packageName) {

        this.packageName = packageName;

        int index =
                packageName.lastIndexOf('.');

        this.interfaceName =
                index >= 0
                        ? packageName.substring(index + 1)
                        : packageName;
    }

    @Override
    public @NotNull String getFamilyName() {
        return "Spring Modulith";
    }

    @Override
    public @NotNull String getName() {

        return "Expose package '"
                + packageName
                + "' as named interface '"
                + interfaceName
                + "'";
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

        applyExposeFix(project);
    }

    private void applyExposeFix(
            @NotNull Project project) {

        ModulithModuleResolver resolver =
                new ModulithModuleResolver(project);

        PsiDirectory directory =
                resolver.findDirectoryForPackage(
                        packageName
                );

        if (directory == null) {
            return;
        }

        WriteCommandAction.runWriteCommandAction(
                project,
                () -> {

                    PsiJavaFile packageInfo =
                            findPackageInfo(directory);

                    PsiElementFactory factory =
                            JavaPsiFacade.getElementFactory(
                                    project
                            );

                    /*
                     * package-info.java does not exist.
                     *
                     * Create it with the named interface.
                     */
                    if (packageInfo == null) {

                        String text =
                                "@org.springframework.modulith.NamedInterface(\""
                                        + interfaceName
                                        + "\")\n"
                                        + "package "
                                        + packageName
                                        + ";\n";

                        PsiJavaFile created =
                                (PsiJavaFile) PsiFileFactory
                                        .getInstance(project)
                                        .createFileFromText(
                                                "package-info.java",
                                                JavaFileType.INSTANCE,
                                                text
                                        );

                        directory.add(created);

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

                    /*
                     * IMPORTANT:
                     *
                     * Never add @NamedInterface twice.
                     */
                    for (PsiAnnotation annotation :
                            annotationList.getAnnotations()) {

                        String qualifiedName =
                                annotation.getQualifiedName();

                        if (NAMED_INTERFACE.equals(
                                qualifiedName
                        )
                                || "org.springframework.modulith.NamedInterface"
                                .equals(qualifiedName)) {

                            return;
                        }
                    }

                    annotationList.add(
                            factory.createAnnotationFromText(
                                    "@org.springframework.modulith.NamedInterface(\""
                                            + interfaceName
                                            + "\")",
                                    packageStatement
                            )
                    );
                }
        );
    }

    private static @Nullable PsiJavaFile findPackageInfo(
            @NotNull PsiDirectory directory) {

        PsiFile file =
                directory.findFile(
                        "package-info.java"
                );

        return file instanceof PsiJavaFile
                ? (PsiJavaFile) file
                : null;
    }

    @Override
    public @NotNull IntentionPreviewInfo generatePreview(
            @NotNull Project project,
            @NotNull com.intellij.codeInspection.ProblemDescriptor previewDescriptor) {

        return IntentionPreviewInfo.EMPTY;
    }
}