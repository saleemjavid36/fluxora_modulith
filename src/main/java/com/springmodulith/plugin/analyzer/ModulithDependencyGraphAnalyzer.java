package com.springmodulith.plugin.analyzer;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.psi.*;
import com.springmodulith.plugin.model.ModulithDependencyGraph;
import com.springmodulith.plugin.model.ModulithModule;
import com.springmodulith.plugin.resolver.ModulithModuleResolver;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class ModulithDependencyGraphAnalyzer {

    private final Project project;
    private final ProjectFileIndex fileIndex;
    private final PsiManager psiManager;
    private final ModulithModuleResolver resolver;

    public ModulithDependencyGraphAnalyzer(
            @NotNull Project project) {

        this.project = project;
        this.fileIndex =
                ProjectRootManager
                        .getInstance(project)
                        .getFileIndex();

        this.psiManager =
                PsiManager.getInstance(project);

        this.resolver =
                new ModulithModuleResolver(project);
    }

    @NotNull
    public ModulithDependencyGraph analyze() {

        List<ModulithModule> modules =
                resolver.resolveModules();

        Set<ModulithDependencyGraph.ModuleDependency> dependencies =
                new LinkedHashSet<>();

        fileIndex.iterateContent(virtualFile -> {

            if (!fileIndex.isInSourceContent(virtualFile)) {
                return true;
            }

            if (!"java".equalsIgnoreCase(
                    virtualFile.getExtension())) {
                return true;
            }

            PsiFile psiFile =
                    psiManager.findFile(virtualFile);

            if (!(psiFile instanceof PsiJavaFile javaFile)) {
                return true;
            }

            analyzeFile(
                    javaFile,
                    modules,
                    dependencies
            );

            return true;
        });

        return new ModulithDependencyGraph(
                modules,
                dependencies
        );
    }

    private void analyzeFile(
            @NotNull PsiJavaFile javaFile,
            @NotNull List<ModulithModule> modules,
            @NotNull Set<ModulithDependencyGraph.ModuleDependency> dependencies) {

        String sourcePackage =
                javaFile.getPackageName();

        ModulithModule source =
                findModule(
                        modules,
                        sourcePackage
                );

        if (source == null) {
            return;
        }

        javaFile.accept(
                new PsiRecursiveElementVisitor() {

                    @Override
                    public void visitElement(
                            @NotNull com.intellij.psi.PsiElement element) {

                        if (element instanceof PsiJavaCodeReferenceElement reference) {

                            PsiElement resolved =
                                    reference.resolve();

                            if (!(resolved instanceof com.intellij.psi.PsiClass targetClass)) {
                                super.visitElement(element);
                                return;
                            }

                            PsiFile targetFile =
                                    targetClass.getContainingFile();

                            if (!(targetFile instanceof PsiJavaFile targetJavaFile)) {
                                super.visitElement(element);
                                return;
                            }

                            if (targetFile.getVirtualFile() == null
                                    || !fileIndex.isInSourceContent(
                                    targetFile.getVirtualFile())) {
                                super.visitElement(element);
                                return;
                            }

                            String targetPackage =
                                    targetJavaFile.getPackageName();

                            ModulithModule target =
                                    findModule(
                                            modules,
                                            targetPackage
                                    );

                            if (target != null
                                    && source != target) {

                                dependencies.add(
                                        new ModulithDependencyGraph.ModuleDependency(
                                                source.getPackageName(),
                                                target.getPackageName()
                                        )
                                );
                            }
                        }

                        super.visitElement(element);
                    }
                }
        );
    }
    @Nullable
    private ModulithModule findModule(
            @NotNull List<ModulithModule> modules,
            @NotNull String packageName) {

        ModulithModule bestMatch = null;

        for (ModulithModule module : modules) {

            if (!module.containsPackage(packageName)) {
                continue;
            }

            if (bestMatch == null
                    || module.getPackageName().length()
                    > bestMatch.getPackageName().length()) {

                bestMatch = module;
            }
        }

        return bestMatch;
    }
}