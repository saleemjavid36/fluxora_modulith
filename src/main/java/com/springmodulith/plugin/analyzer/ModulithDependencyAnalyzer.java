package com.springmodulith.plugin.analyzer;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiImportStatement;
import com.intellij.psi.PsiJavaFile;
import com.springmodulith.plugin.model.ModulithModule;
import com.springmodulith.plugin.resolver.ModulithModuleResolver;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class ModulithDependencyAnalyzer {

    private final ModulithModuleResolver moduleResolver;

    public ModulithDependencyAnalyzer(
            @NotNull ModulithModuleResolver moduleResolver) {
        this.moduleResolver = moduleResolver;
    }

    public boolean isCrossModuleDependency(
            @NotNull PsiImportStatement importStatement) {

        PsiJavaFile sourceFile = getJavaFile(importStatement);

        if (sourceFile == null) {
            return false;
        }

        String sourcePackage = sourceFile.getPackageName();

        if (sourcePackage.isEmpty()) {
            return false;
        }

        PsiClass targetClass = resolveImportedClass(importStatement);

        if (targetClass == null) {
            return false;
        }

        String targetPackage = getPackageName(targetClass);

        if (targetPackage == null || targetPackage.isEmpty()) {
            return false;
        }

        ModulithModule sourceModule =
                moduleResolver.resolveModule(
                        sourceFile,
                        sourcePackage
                );

        ModulithModule targetModule =
                moduleResolver.resolveModule(
                        sourceFile,
                        targetPackage
                );

        if (sourceModule == null || targetModule == null) {
            return false;
        }

        return !sourceModule.getPackageName()
                .equals(targetModule.getPackageName());
    }

    @Nullable
    public String getDependencyMessage(
            @NotNull PsiImportStatement importStatement) {

        PsiJavaFile sourceFile = getJavaFile(importStatement);

        if (sourceFile == null) {
            return null;
        }

        PsiClass targetClass = resolveImportedClass(importStatement);

        if (targetClass == null) {
            return null;
        }

        String sourcePackage = sourceFile.getPackageName();
        String targetPackage = getPackageName(targetClass);

        if (sourcePackage.isEmpty()
                || targetPackage == null
                || targetPackage.isEmpty()) {
            return null;
        }

        ModulithModule sourceModule =
                moduleResolver.resolveModule(
                        sourceFile,
                        sourcePackage
                );

        ModulithModule targetModule =
                moduleResolver.resolveModule(
                        sourceFile,
                        targetPackage
                );

        if (sourceModule == null || targetModule == null) {
            return null;
        }

        if (sourceModule.getPackageName()
                .equals(targetModule.getPackageName())) {
            return null;
        }

        return "Modulith dependency: "
                + sourceModule.getName()
                + " -> "
                + targetModule.getName();
    }

    @Nullable
    private PsiJavaFile getJavaFile(
            @NotNull PsiImportStatement importStatement) {

        if (importStatement.getContainingFile()
                instanceof PsiJavaFile) {

            return (PsiJavaFile)
                    importStatement.getContainingFile();
        }

        return null;
    }

    @Nullable
    private PsiClass resolveImportedClass(
            @NotNull PsiImportStatement importStatement) {

        PsiElement resolved = importStatement.resolve();

        if (resolved instanceof PsiClass) {
            return (PsiClass) resolved;
        }

        return null;
    }

    @Nullable
    private String getPackageName(
            @NotNull PsiClass psiClass) {

        PsiJavaFile javaFile =
                getContainingJavaFile(psiClass);

        if (javaFile == null) {
            return null;
        }

        return javaFile.getPackageName();
    }

    @Nullable
    private PsiJavaFile getContainingJavaFile(
            @NotNull PsiClass psiClass) {

        if (psiClass.getContainingFile()
                instanceof PsiJavaFile) {

            return (PsiJavaFile)
                    psiClass.getContainingFile();
        }

        return null;
    }
}