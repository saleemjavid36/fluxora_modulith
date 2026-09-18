package com.springmodulith.plugin.toolwindow;

import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiPackage;
import com.springmodulith.plugin.model.ModulithDependencyReference;
import com.springmodulith.plugin.model.ModulithModule;
import org.jetbrains.annotations.NotNull;

public final class ModulithModuleNavigation {

    private ModulithModuleNavigation() {
    }

    public static void openReference(
            @NotNull Project project,
            @NotNull ModulithDependencyReference reference) {

        if (!reference.file().isValid()) {
            return;
        }

        new OpenFileDescriptor(
                project,
                reference.file(),
                reference.offset()
        ).navigate(true);
    }

    public static void openPackage(
            @NotNull Project project,
            @NotNull ModulithModule module) {

        PsiDirectory directory = findPackageDirectory(
                project,
                module.getPackageName()
        );

        if (directory == null) {
            return;
        }

        PsiFile packageInfo = directory.findFile("package-info.java");

        if (packageInfo != null) {
            FileEditorManager.getInstance(project)
                    .openFile(packageInfo.getVirtualFile(), true);
            return;
        }

        PsiFile javaFile = findFirstJavaFile(directory);

        if (javaFile != null) {
            FileEditorManager.getInstance(project)
                    .openFile(javaFile.getVirtualFile(), true);
        }
    }

    public static void openQualifiedType(
            @NotNull Project project,
            @NotNull String qualifiedName) {
        var clazz = JavaPsiFacade.getInstance(project).findClass(
                qualifiedName,
                com.intellij.psi.search.GlobalSearchScope.projectScope(project)
        );
        VirtualFile virtualFile =
                clazz.getContainingFile().getVirtualFile();
        if (clazz != null && clazz.getContainingFile() != null) {
            new OpenFileDescriptor(project, virtualFile, clazz.getTextOffset()).navigate(true);
        }
    }

    private static PsiDirectory findPackageDirectory(
            @NotNull Project project,
            @NotNull String packageName) {

        PsiPackage psiPackage =
                JavaPsiFacade.getInstance(project)
                        .findPackage(packageName);

        if (psiPackage == null) {
            return null;
        }

        PsiDirectory[] directories = psiPackage.getDirectories();

        if (directories.length == 0) {
            return null;
        }

        return directories[0];
    }

    private static PsiFile findFirstJavaFile(
            @NotNull PsiDirectory directory) {

        for (PsiFile file : directory.getFiles()) {
            if ("java".equalsIgnoreCase(
                    file.getVirtualFile().getExtension())) {

                return file;
            }
        }

        return null;
    }
}