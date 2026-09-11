package com.springmodulith.plugin.analyzer;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaCodeReferenceElement;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiRecursiveElementVisitor;
import com.intellij.psi.PsiManager;
import com.springmodulith.plugin.model.ModulithDependencyGraph;
import com.springmodulith.plugin.model.ModulithModule;
import com.springmodulith.plugin.model.NamedInterface;
import com.springmodulith.plugin.resolver.ModulithModuleResolver;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ModulithDependencyGraphAnalyzer {

    private final Project project;
    private final ProjectFileIndex fileIndex;
    private final PsiManager psiManager;
    private final ModulithModuleResolver resolver;

    public ModulithDependencyGraphAnalyzer(@NotNull Project project) {
        this.project = project;
        this.fileIndex = ProjectRootManager.getInstance(project).getFileIndex();
        this.psiManager = PsiManager.getInstance(project);
        this.resolver = new ModulithModuleResolver(project);
    }

    @NotNull
    public ModulithDependencyGraph analyze() {
        List<ModulithModule> modules = resolver.resolveModules();
        Map<String, ModulithDependencyGraph.ModuleDependency> dependencies = new LinkedHashMap<>();

        fileIndex.iterateContent(virtualFile -> {
            if (!fileIndex.isInSourceContent(virtualFile)
                    || fileIndex.isInTestSourceContent(virtualFile)
                    || !"java".equalsIgnoreCase(virtualFile.getExtension())) {
                return true;
            }

            PsiFile psiFile = psiManager.findFile(virtualFile);
            if (psiFile instanceof PsiJavaFile javaFile) {
                analyzeFile(javaFile, modules, dependencies);
            }
            return true;
        });

        return new ModulithDependencyGraph(modules, new java.util.LinkedHashSet<>(dependencies.values()));
    }

    private void analyzeFile(
            @NotNull PsiJavaFile javaFile,
            @NotNull List<ModulithModule> modules,
            @NotNull Map<String, ModulithDependencyGraph.ModuleDependency> dependencies) {

        String sourcePackage = javaFile.getPackageName();
        ModulithModule source = findModule(modules, sourcePackage);
        if (source == null) return;

        javaFile.accept(new PsiRecursiveElementVisitor() {
            @Override
            public void visitElement(@NotNull PsiElement element) {
                if (element instanceof PsiJavaCodeReferenceElement reference) {
                    analyzeReference(reference, javaFile, source, modules, dependencies);
                }
                super.visitElement(element);
            }
        });
    }

    private void analyzeReference(
            @NotNull PsiJavaCodeReferenceElement reference,
            @NotNull PsiJavaFile sourceFile,
            @NotNull ModulithModule source,
            @NotNull List<ModulithModule> modules,
            @NotNull Map<String, ModulithDependencyGraph.ModuleDependency> dependencies) {

        PsiElement resolved = reference.resolve();
        if (!(resolved instanceof PsiClass targetClass)) return;

        PsiFile targetFile = targetClass.getContainingFile();
        if (!(targetFile instanceof PsiJavaFile targetJavaFile)
                || targetFile.getVirtualFile() == null
                || !fileIndex.isInSourceContent(targetFile.getVirtualFile())
                || fileIndex.isInTestSourceContent(targetFile.getVirtualFile())) {
            return;
        }

        String targetPackage = targetJavaFile.getPackageName();
        ModulithModule target = findModule(modules, targetPackage);
        if (target == null || source.getPackageName().equals(target.getPackageName())) return;

        String qualifiedType = targetClass.getQualifiedName();
        if (qualifiedType == null) return;

        boolean allowed = !source.isAllowedDependenciesConfigured()
                || source.allowsType(qualifiedType, targetPackage, target);

        boolean apiViolation = !target.exposes(qualifiedType, targetPackage);
        NamedInterface namedInterface = findAllowedNamedInterface(source, target, qualifiedType, targetPackage);

        ModulithDependencyGraph.EdgeKind kind;
        if (namedInterface != null) {
            kind = ModulithDependencyGraph.EdgeKind.NAMED_INTERFACE;
        } else if (allowed && !apiViolation) {
            kind = ModulithDependencyGraph.EdgeKind.ALLOWED;
        } else {
            kind = ModulithDependencyGraph.EdgeKind.FORBIDDEN;
        }

        String key = source.getPackageName() + "->" + target.getPackageName();
        ModulithDependencyGraph.ModuleDependency existing = dependencies.get(key);

        if (existing == null) {
            dependencies.put(key, new ModulithDependencyGraph.ModuleDependency(
                    source.getPackageName(),
                    target.getPackageName(),
                    kind,
                    apiViolation,
                    namedInterface == null ? null : namedInterface.getName()
            ));
            return;
        }

        existing.incrementReferenceCount();
        if (existing.kind() != ModulithDependencyGraph.EdgeKind.FORBIDDEN && kind == ModulithDependencyGraph.EdgeKind.FORBIDDEN) {
            dependencies.put(key, new ModulithDependencyGraph.ModuleDependency(
                    existing.sourcePackage(),
                    existing.targetPackage(),
                    kind,
                    existing.isApiViolation() || apiViolation,
                    namedInterface == null ? existing.namedInterface() : namedInterface.getName(),
                    existing.referenceCount()
            ));
        }
    }

    @Nullable
    private NamedInterface findAllowedNamedInterface(
            @NotNull ModulithModule source,
            @NotNull ModulithModule target,
            @NotNull String qualifiedType,
            @NotNull String targetPackage) {

        if (!source.isAllowedDependenciesConfigured()) return null;

        for (String dependency : source.getAllowedDependencies()) {
            ModulithModule.DependencyRule rule = ModulithModule.DependencyRule.parse(dependency);
            if (rule == null || rule.interfaceId() == null || "*".equals(rule.interfaceId())) continue;
            if (!target.getName().equals(rule.moduleId()) && !target.getPackageName().equals(rule.moduleId())) continue;

            NamedInterface namedInterface = target.findNamedInterface(rule.interfaceId());
            if (namedInterface != null
                    && (namedInterface.containsType(qualifiedType) || namedInterface.containsPackage(targetPackage))) {
                return namedInterface;
            }
        }
        return null;
    }

    @Nullable
    private ModulithModule findModule(
            @NotNull List<ModulithModule> modules,
            @NotNull String packageName) {
        ModulithModule bestMatch = null;
        for (ModulithModule module : modules) {
            if (!module.containsPackage(packageName)) continue;
            if (bestMatch == null || module.getPackageName().length() > bestMatch.getPackageName().length()) {
                bestMatch = module;
            }
        }
        return bestMatch;
    }
}
