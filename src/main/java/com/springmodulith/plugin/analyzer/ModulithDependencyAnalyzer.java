package com.springmodulith.plugin.analyzer;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaCodeReferenceElement;
import com.intellij.psi.PsiJavaFile;
import com.springmodulith.plugin.model.ModulithModule;
import com.springmodulith.plugin.resolver.ModulithModuleResolver;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class ModulithDependencyAnalyzer {
    private final ModulithModuleResolver resolver;
    private final ProjectFileIndex fileIndex;

    public ModulithDependencyAnalyzer(
            @NotNull ModulithModuleResolver resolver,
            @NotNull Project project) {
        this.resolver = resolver;
        this.fileIndex = ProjectRootManager.getInstance(project).getFileIndex();
    }

    public boolean isViolation(@NotNull PsiJavaCodeReferenceElement reference) {
        Dependency dependency = analyze(reference);

        if (dependency == null) {
            return false;
        }

        String qualifiedType = dependency.targetClass().getQualifiedName();

        if (qualifiedType == null) {
            return false;
        }

        String targetPackage = getPackageName(dependency.targetClass());

        if (targetPackage.isEmpty()) {
            return false;
        }

        return !dependency.source().allowsType(
                qualifiedType,
                targetPackage,
                dependency.target()
        );
    }

    @Nullable
    public String getMessage(@NotNull PsiJavaCodeReferenceElement reference) {
        Dependency dependency = analyze(reference);

        if (dependency == null) {
            return null;
        }

        String qualifiedType = dependency.targetClass().getQualifiedName();

        if (qualifiedType == null) {
            return null;
        }

        String targetPackage = getPackageName(dependency.targetClass());

        if (targetPackage.isEmpty()) {
            return null;
        }

        boolean allowed = dependency.source().allowsType(
                qualifiedType,
                targetPackage,
                dependency.target()
        );

        if (allowed) {
            return null;
        }

        String dependencyName = dependency.source().getName()
                + " -> "
                + dependency.target().getName();

        return "Modulith dependency is not allowed: " + dependencyName;
    }

    @Nullable
    public Dependency analyze(@NotNull PsiJavaCodeReferenceElement reference) {
        PsiElement resolved = reference.resolve();

        if (!(resolved instanceof PsiClass targetClass)) {
            return null;
        }

        PsiJavaFile sourceFile = containingJavaFile(reference);
        PsiJavaFile targetFile = containingJavaFile(targetClass);

        if (sourceFile == null || targetFile == null) {
            return null;
        }

        if (!isProjectSource(sourceFile) || !isProjectSource(targetFile)) {
            return null;
        }

        String sourcePackage = sourceFile.getPackageName();
        String targetPackage = targetFile.getPackageName();

        if (sourcePackage.isEmpty() || targetPackage.isEmpty()) {
            return null;
        }

        ModulithModule source =
                resolver.resolveModule(sourceFile, sourcePackage);

        ModulithModule target =
                resolver.resolveModule(targetFile, targetPackage);

        if (source == null || target == null) {
            return null;
        }

        if (source.getPackageName().equals(target.getPackageName())) {
            return null;
        }

        return new Dependency(source, target, targetClass);
    }

    @NotNull
    private String getPackageName(@NotNull PsiClass psiClass) {
        PsiFile containingFile = psiClass.getContainingFile();

        if (!(containingFile instanceof PsiJavaFile javaFile)) {
            return "";
        }

        return javaFile.getPackageName();
    }

    private boolean isProjectSource(@NotNull PsiFile file) {
        return file.getVirtualFile() != null
                && fileIndex.isInSourceContent(file.getVirtualFile());
    }

    @Nullable
    private PsiJavaFile containingJavaFile(@NotNull PsiElement element) {
        PsiFile file = element.getContainingFile();

        if (file instanceof PsiJavaFile javaFile) {
            return javaFile;
        }

        return null;
    }

    public record Dependency(
            @NotNull ModulithModule source,
            @NotNull ModulithModule target,
            @NotNull PsiClass targetClass) {
    }
}