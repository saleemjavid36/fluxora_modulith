package com.springmodulith.plugin.projectView;

import com.intellij.psi.JavaDirectoryService;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiModifierList;
import com.intellij.psi.PsiPackage;
import com.intellij.psi.PsiPackageStatement;
import com.springmodulith.plugin.model.ModulithModule;
import com.springmodulith.plugin.resolver.ModulithModuleResolver;
import org.jetbrains.annotations.NotNull;

final class ModulithPackageVisibilityResolver {
    private ModulithPackageVisibilityResolver() {
    }

    static boolean isPublic(
            @NotNull PsiDirectory directory,
            @NotNull String packageName,
            @NotNull ModulithModule module) {
        return packageName.equals(module.getPackageName())
                || module.isOpen()
                || hasNamedInterface(directory, module.getPackageName());
    }

    private static boolean hasNamedInterface(
            @NotNull PsiDirectory directory,
            @NotNull String modulePackage) {
        PsiDirectory current = directory;

        while (current != null) {
            PsiPackage currentPackage =
                    JavaDirectoryService.getInstance().getPackage(current);

            if (currentPackage == null) return false;

            String currentPackageName = currentPackage.getQualifiedName();
            if (!currentPackageName.equals(modulePackage)
                    && !currentPackageName.startsWith(modulePackage + ".")) {
                return false;
            }

            if (hasPackageNamedInterface(current)) return true;
            if (currentPackageName.equals(modulePackage)) return false;

            current = current.getParentDirectory();
        }

        return false;
    }

    private static boolean hasPackageNamedInterface(@NotNull PsiDirectory directory) {
        PsiFile packageInfo = directory.findFile("package-info.java");
        if (!(packageInfo instanceof PsiJavaFile javaFile)) return false;

        PsiPackageStatement packageStatement = javaFile.getPackageStatement();
        if (packageStatement == null) return false;

        PsiModifierList annotations = packageStatement.getAnnotationList();
        if (annotations == null) return false;

        for (PsiAnnotation annotation : annotations.getAnnotations()) {
            if (annotation.hasQualifiedName(ModulithModuleResolver.NAMED_INTERFACE)) {
                return true;
            }
        }

        return false;
    }
}
