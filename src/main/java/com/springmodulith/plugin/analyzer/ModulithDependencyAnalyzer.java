package com.springmodulith.plugin.analyzer;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaCodeReferenceElement;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiImportStatement;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.springmodulith.plugin.model.ModulithModule;
import com.springmodulith.plugin.resolver.ModulithModuleResolver;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class ModulithDependencyAnalyzer {
    private final ModulithModuleResolver resolver;
    private final ProjectFileIndex fileIndex;

    public ModulithDependencyAnalyzer(@NotNull ModulithModuleResolver resolver, @NotNull com.intellij.openapi.project.Project project) {
        this.resolver = resolver;
        this.fileIndex = ProjectRootManager.getInstance(project).getFileIndex();
    }

    public boolean isViolation(@NotNull PsiJavaCodeReferenceElement reference) {
        Dependency dependency = analyze(reference);
        return dependency != null && !dependency.source().allowsDependency(dependency.target());
    }

    @Nullable
    public String getMessage(@NotNull PsiJavaCodeReferenceElement reference) {
        Dependency dependency = analyze(reference);
        if (dependency == null || dependency.source().allowsDependency(dependency.target())) return null;
        return "Modulith dependency is not allowed: " + dependency.source().getName() + " -> " + dependency.target().getName();
    }

    @Nullable
    public Dependency analyze(@NotNull PsiJavaCodeReferenceElement reference) {
        if (isInsideImport(reference)) return null;
        PsiElement resolved = reference.resolve();
        if (!(resolved instanceof PsiClass targetClass)) return null;
        PsiJavaFile sourceFile = containingJavaFile(reference);
        PsiJavaFile targetFile = containingJavaFile(targetClass);
        if (sourceFile == null || targetFile == null) return null;
        if (!isProjectSource(sourceFile) || !isProjectSource(targetFile)) return null;
        String sourcePackage = sourceFile.getPackageName();
        String targetPackage = targetFile.getPackageName();
        if (sourcePackage.isEmpty() || targetPackage.isEmpty() || sourcePackage.equals(targetPackage)) return null;
        ModulithModule source = resolver.resolveModule(sourceFile, sourcePackage);
        ModulithModule target = resolver.resolveModule(sourceFile, targetPackage);
        if (source == null || target == null || source.getPackageName().equals(target.getPackageName())) return null;
        return new Dependency(source, target, targetClass);
    }

    private boolean isInsideImport(@NotNull PsiElement element) {
        return element.getParent() instanceof PsiImportStatement || element.getParent() != null &&
                com.intellij.psi.util.PsiTreeUtil.getParentOfType(element, PsiImportStatement.class) != null;
    }

    private boolean isProjectSource(@NotNull PsiFile file) {
        return file.getVirtualFile() != null && fileIndex.isInSourceContent(file.getVirtualFile());
    }

    @Nullable
    private PsiJavaFile containingJavaFile(@NotNull PsiElement element) {
        PsiFile file = element.getContainingFile();
        return file instanceof PsiJavaFile ? (PsiJavaFile) file : null;
    }

    public record Dependency(@NotNull ModulithModule source, @NotNull ModulithModule target, @NotNull PsiClass targetClass) {}
}
