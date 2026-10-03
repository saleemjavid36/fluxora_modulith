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

        // The module base package is always its public API.
        if (packageName.equals(module.getPackageName())) {
            return true;
        }

        /*
         * For an explicitly OPEN module, even an empty sub-package is part of
         * the exposed module surface and must use the public/unlocked icon.
         * package-info.java is metadata and therefore does not make a package
         * non-empty for this purpose. CLOSED modules keep empty sub-packages
         * internal/locked.
         */
        if (!containsJavaType(directory) && !module.isOpen()) {
            return false;
        }

        /*
         * A package explicitly exposed through @NamedInterface is public in a
         * CLOSED module as well.
         */
        if (hasNamedInterface(directory, module.getPackageName())) {
            return true;
        }

        /*
         * If the module root has no @ApplicationModule declaration, mirror the
         * Project View behavior for an implicit module: ordinary sub-packages
         * remain unmarked/public, while an explicitly declared nested module
         * gets the internal/locked icon.
         */
        PsiDirectory moduleDirectory = findModuleDirectory(directory, module.getPackageName());
        if (moduleDirectory != null && !hasApplicationModule(moduleDirectory)) {
            return !hasApplicationModule(directory);
        }

        /*
         * An explicitly declared OPEN module exposes its non-empty sub-packages.
         * A default/explicit CLOSED module keeps them internal unless a named
         * interface exposed them above.
         */
        return module.isOpen();
    }

    private static boolean containsJavaType(@NotNull PsiDirectory directory) {
        for (PsiFile file : directory.getFiles()) {
            if (file instanceof PsiJavaFile javaFile
                    && javaFile.getClasses().length > 0) {
                return true;
            }
        }

        for (PsiDirectory child : directory.getSubdirectories()) {
            if (containsJavaType(child)) {
                return true;
            }
        }

        return false;
    }

    private static PsiDirectory findModuleDirectory(
            @NotNull PsiDirectory directory,
            @NotNull String modulePackage) {
        PsiDirectory current = directory;

        while (current != null) {
            PsiPackage pkg = JavaDirectoryService.getInstance().getPackage(current);
            if (pkg != null && modulePackage.equals(pkg.getQualifiedName())) {
                return current;
            }
            current = current.getParentDirectory();
        }

        return null;
    }

    private static boolean hasApplicationModule(@NotNull PsiDirectory directory) {
        return findPackageAnnotation(
                directory,
                ModulithModuleResolver.APPLICATION_MODULE
        ) != null;
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
        PsiAnnotation annotation = findPackageAnnotation(
                directory,
                ModulithModuleResolver.NAMED_INTERFACE
        );
        return annotation != null;
    }

    private static PsiAnnotation findPackageAnnotation(
            @NotNull PsiDirectory directory,
            @NotNull String qualifiedName) {
        PsiFile packageInfo = directory.findFile("package-info.java");
        if (!(packageInfo instanceof PsiJavaFile javaFile)) return null;

        PsiPackageStatement packageStatement = javaFile.getPackageStatement();
        if (packageStatement == null) return null;

        PsiModifierList annotations = packageStatement.getAnnotationList();
        if (annotations == null) return null;

        for (PsiAnnotation annotation : annotations.getAnnotations()) {
            if (annotation.hasQualifiedName(qualifiedName)) {
                return annotation;
            }

        }

        return null;
    }
}
