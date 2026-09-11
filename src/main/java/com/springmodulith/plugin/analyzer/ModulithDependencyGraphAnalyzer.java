package com.springmodulith.plugin.analyzer;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaCodeReferenceElement;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiRecursiveElementVisitor;
import com.springmodulith.plugin.model.ModulithDependencyGraph;
import com.springmodulith.plugin.model.ModulithModule;
import com.springmodulith.plugin.resolver.ModulithModuleResolver;
import org.jetbrains.annotations.NotNull;

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

        ModulithDependencyAnalyzer analyzer =
                new ModulithDependencyAnalyzer(
                        resolver,
                        project
                );

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
                    analyzer,
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
            @NotNull ModulithDependencyAnalyzer analyzer,
            @NotNull Set<ModulithDependencyGraph.ModuleDependency> dependencies) {

        javaFile.accept(
                new PsiRecursiveElementVisitor() {

                    @Override
                    public void visitElement(
                            @NotNull com.intellij.psi.PsiElement element) {

                        if (element instanceof PsiJavaCodeReferenceElement reference) {

                            ModulithDependencyAnalyzer.Dependency dependency =
                                    analyzer.analyze(reference);

                            if (dependency != null) {

                                dependencies.add(
                                        new ModulithDependencyGraph.ModuleDependency(
                                                dependency.source().getPackageName(),
                                                dependency.target().getPackageName()
                                        )
                                );
                            }
                        }

                        super.visitElement(element);
                    }
                }
        );
    }
}