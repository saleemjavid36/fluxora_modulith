package com.springmodulith.plugin.toolwindow;

import com.intellij.openapi.application.ReadAction;
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
import com.intellij.psi.PsiClass;
import com.intellij.psi.search.GlobalSearchScope;


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

        VirtualFile targetFile = ReadAction.compute(() -> {
            PsiDirectory directory = findPackageDirectory(
                    project,
                    module.getPackageName()
            );

            if (directory == null || !directory.isValid()) {
                return null;
            }

            PsiFile packageInfo = directory.findFile("package-info.java");

            if (packageInfo != null && packageInfo.getVirtualFile() != null) {
                return packageInfo.getVirtualFile();
            }

            PsiFile javaFile = findFirstJavaFile(directory);
            return javaFile != null ? javaFile.getVirtualFile() : null;
        });

        if (targetFile != null && targetFile.isValid()) {
            FileEditorManager.getInstance(project)
                    .openFile(targetFile, true);
        }
    }

    public static void openQualifiedType(
            @NotNull Project project,
            @NotNull String qualifiedName) {

        NavigationTarget target = ReadAction.compute(() -> {
            PsiClass clazz = JavaPsiFacade.getInstance(project).findClass(
                    qualifiedName,
                    GlobalSearchScope.projectScope(project)
            );

            if (clazz == null) {
                return null;
            }

            PsiFile containingFile = clazz.getContainingFile();

            if (containingFile == null
                    || containingFile.getVirtualFile() == null) {
                return null;
            }

            return new NavigationTarget(
                    containingFile.getVirtualFile(),
                    clazz.getTextOffset()
            );
        });

        if (target == null || !target.file().isValid()) {
            return;
        }

        OpenFileDescriptor descriptor =
                new OpenFileDescriptor(
                        project,
                        target.file(),
                        target.offset()
                );

        FileEditorManager.getInstance(project)
                .openTextEditor(descriptor, true);
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
            VirtualFile virtualFile = file.getVirtualFile();

            if (virtualFile != null
                    && "java".equalsIgnoreCase(virtualFile.getExtension())) {
                return file;
            }
        }

        return null;
    }

    private record NavigationTarget(
            @NotNull VirtualFile file,
            int offset) {
    }
}