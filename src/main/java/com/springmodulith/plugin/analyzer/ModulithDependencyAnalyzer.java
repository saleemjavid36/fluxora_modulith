package com.springmodulith.plugin.analyzer;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.roots.GeneratedSourcesFilter;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaCodeReferenceElement;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiRecursiveElementVisitor;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.util.PsiModificationTracker;
import com.intellij.openapi.project.DumbService;
import com.springmodulith.plugin.model.ModulithDependencyAnalysis;
import com.springmodulith.plugin.model.ModulithDependencyReference;
import com.springmodulith.plugin.model.ModulithModule;
import com.springmodulith.plugin.model.NamedInterface;
import com.springmodulith.plugin.configuration.ModulithSettings;
import com.springmodulith.plugin.resolver.ModulithModuleResolver;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Central dependency analyzer used by inspections and the module graph.
 * All consumers use the same dependency classification and module rules.
 */
public final class ModulithDependencyAnalyzer {
    private final Project project;
    private final ModulithModuleResolver resolver;
    private final ProjectFileIndex fileIndex;
    private final PsiDocumentManager documentManager;
    private final Map<String, List<ModulithModule>> modulesByContextPackage = new HashMap<>();
    private long modulesModificationCount = -1L;

    public ModulithDependencyAnalyzer(
            @NotNull ModulithModuleResolver resolver,
            @NotNull Project project) {
        this.project = project;
        this.resolver = resolver;
        this.fileIndex = ProjectRootManager.getInstance(project).getFileIndex();
        this.documentManager = PsiDocumentManager.getInstance(project);
    }

    @Nullable
    public ModulithDependencyAnalysis analyze(
            @NotNull PsiJavaCodeReferenceElement reference) {
        return analyze(reference, getModules(reference.getContainingFile()));
    }

    @NotNull
    public List<ModulithDependencyAnalysis> analyzeProject() {
        if (project.isDisposed() || DumbService.isDumb(project)) {
            return List.of();
        }

        List<ModulithModule> modules = getModules(null);
        if (modules.isEmpty()) {
            return List.of();
        }

        List<ModulithDependencyAnalysis> result = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();

        fileIndex.iterateContent(virtualFile -> {
            if (project.isDisposed()
                    || !fileIndex.isInSourceContent(virtualFile)
                    || fileIndex.isInTestSourceContent(virtualFile)
                    || GeneratedSourcesFilter.isGeneratedSourceByAnyFilter(virtualFile, project)
                    || !"java".equalsIgnoreCase(virtualFile.getExtension())) {
                return !project.isDisposed();
            }

            PsiFile psiFile = com.intellij.psi.PsiManager.getInstance(project).findFile(virtualFile);
            if (!(psiFile instanceof PsiJavaFile javaFile)) {
                return true;
            }

            String sourcePackage = javaFile.getPackageName();
            ModulithModule source = findModule(modules, sourcePackage);
            if (source == null) {
                return true;
            }

            javaFile.accept(new PsiRecursiveElementVisitor() {
                @Override
                public void visitElement(@NotNull PsiElement element) {
                    if (element instanceof PsiJavaCodeReferenceElement reference) {
                        ModulithDependencyAnalysis analysis =
                                analyze(reference, modules);
                        if (analysis != null) {
                            String key = referenceKey(analysis);
                            if (seen.add(key)) {
                                result.add(analysis);
                            }
                        }
                    }
                    super.visitElement(element);
                }
            });

            return true;
        });

        return Collections.unmodifiableList(result);
    }

    @NotNull
    private List<ModulithModule> getModules(@Nullable PsiFile contextFile) {
        if (contextFile == null) {
            return resolver.resolveModules();
        }

        String contextPackage = contextFile instanceof PsiJavaFile javaFile
                ? javaFile.getPackageName()
                : contextFile.getName();
        long currentModificationCount = PsiModificationTracker.getInstance(project).getModificationCount();
        if (modulesModificationCount != currentModificationCount) {
            modulesByContextPackage.clear();
            modulesModificationCount = currentModificationCount;
        }

        return modulesByContextPackage.computeIfAbsent(
                contextPackage,
                ignored -> List.copyOf(resolver.resolveModules(contextFile))
        );
    }

    private boolean isAllowedByConfiguredRules(
            @NotNull ModulithModule source,
            @NotNull ModulithModule target,
            @NotNull String qualifiedType,
            @NotNull String targetPackage) {
        java.util.Set<String> override = ModulithSettings.getInstance(project)
                .getDependencyOverrideMap()
                .get(source.getPackageName());
        if (override != null) {
            for (String ruleText : override) {
                ModulithModule.DependencyRule rule = ModulithModule.DependencyRule.parse(ruleText);
                if (rule == null || !target.matchesModuleId(rule.moduleId())) continue;
                if (rule.interfaceId() == null) {
                    return targetPackage.equals(target.getPackageName());
                }

                if ("*".equals(rule.interfaceId())) {
                    return target.findNamedInterfaceForType(
                            qualifiedType,
                            targetPackage
                    ) != null;
                }
                NamedInterface namedInterface = target.findNamedInterface(rule.interfaceId());
                if (namedInterface != null && namedInterface.contains(qualifiedType, targetPackage)) return true;
            }
            return false;
        }
        return !source.isAllowedDependenciesConfigured()
                || source.allowsType(qualifiedType, targetPackage, target);
    }

    private String referenceKey(@NotNull ModulithDependencyAnalysis analysis) {
        PsiFile file = analysis.reference().getContainingFile();
        String path = file.getVirtualFile() == null
                ? file.getName()
                : file.getVirtualFile().getPath();
        return path + "@" + analysis.reference().getTextOffset()
                + "->" + analysis.targetPackage()
                + "::" + analysis.targetClass().getQualifiedName();
    }

    @Nullable
    private ModulithDependencyAnalysis analyze(
            @NotNull PsiJavaCodeReferenceElement reference,
            @NotNull List<ModulithModule> modules) {

        PsiElement resolved = reference.resolve();
        if (!(resolved instanceof PsiClass targetClass)) {
            return null;
        }

        PsiJavaFile sourceFile = containingJavaFile(reference);
        PsiJavaFile targetFile = containingJavaFile(targetClass);

        if (sourceFile == null || targetFile == null
                || !isProjectSource(sourceFile)
                || !isProjectSource(targetFile)) {
            return null;
        }

        String sourcePackage = sourceFile.getPackageName();
        String targetPackage = targetFile.getPackageName();
        if (sourcePackage.isEmpty() || targetPackage.isEmpty()) {
            return null;
        }

        ModulithModule source = findModule(modules, sourcePackage);
        ModulithModule target = findModule(modules, targetPackage);
        if (source == null || target == null
                || source.getPackageName().equals(target.getPackageName())) {
            return null;
        }
        /*
         * Do not perform dependency analysis unless the source module
         * explicitly declares allowedDependencies.
         */
        if (!source.isAllowedDependenciesConfigured()) {
            return null;
        }

        String qualifiedType = targetClass.getQualifiedName();
        if (qualifiedType == null || qualifiedType.isEmpty()) {
            return null;
        }

        boolean allowed = isAllowedByConfiguredRules(source, target, qualifiedType, targetPackage);
        boolean apiViolation = !target.exposes(qualifiedType, targetPackage);

        NamedInterface namedInterface =
                source.findAllowedNamedInterface(
                        qualifiedType,
                        targetPackage,
                        target
                );

        ModulithDependencyAnalysis.Status status;
        if (apiViolation || !allowed) {
            status = ModulithDependencyAnalysis.Status.FORBIDDEN;
        } else if (namedInterface != null) {
            status = ModulithDependencyAnalysis.Status.NAMED_INTERFACE;
        } else {
            status = ModulithDependencyAnalysis.Status.ALLOWED;
        }

        ModulithDependencyReference sourceReference = createDependencyReference(reference);
        if (sourceReference == null) {
            return null;
        }

        return new ModulithDependencyAnalysis(
                source,
                target,
                targetClass,
                reference,
                sourceReference,
                status,
                apiViolation,
                namedInterface
        );
    }

    public boolean isViolation(@NotNull PsiJavaCodeReferenceElement reference) {
        ModulithDependencyAnalysis analysis = analyze(reference);
        return analysis != null && analysis.isForbidden();
    }

    @Nullable
    public String getMessage(@NotNull PsiJavaCodeReferenceElement reference) {
        ModulithDependencyAnalysis analysis = analyze(reference);
        return analysis == null ? null : getMessage(analysis);
    }

    @Nullable
    public String getMessage(@NotNull ModulithDependencyAnalysis analysis) {
        if (!analysis.source().isAllowedDependenciesConfigured()) {
            if (analysis.apiViolation()) {
                return "Modulith API violation: "
                        + analysis.source().getName()
                        + " accesses internal type "
                        + analysis.targetClass().getQualifiedName()
                        + " from module "
                        + analysis.target().getName();
            }
            return "Modulith module dependency: "
                    + analysis.source().getName()
                    + " -> "
                    + analysis.target().getName()
                    + " (add allowedDependencies to make the dependency explicit)";
        }

        if (!analysis.isForbidden()) {
            return null;
        }

        String dependencyName =
                analysis.source().getName()
                        + " -> "
                        + analysis.target().getName();

        String qualifiedType = analysis.targetClass().getQualifiedName();
        NamedInterface namedInterface = qualifiedType == null
                ? null
                : analysis.target().findNamedInterfaceForType(
                qualifiedType,
                packageName(analysis.targetClass())
        );

        if (namedInterface != null) {
            return "Modulith named-interface dependency is not allowed: "
                    + dependencyName
                    + " :: "
                    + namedInterface.getName();
        }

        if (analysis.apiViolation()) {
            return "Modulith API violation: "
                    + analysis.source().getName()
                    + " accesses internal type "
                    + analysis.targetClass().getQualifiedName()
                    + " from module "
                    + analysis.target().getName();
        }

        return "Modulith dependency is not allowed: " + dependencyName;
    }

    @Nullable
    private ModulithDependencyReference createDependencyReference(
            @NotNull PsiJavaCodeReferenceElement reference) {
        PsiFile sourceFile = reference.getContainingFile();
        if (sourceFile == null || sourceFile.getVirtualFile() == null) {
            return null;
        }

        PsiElement nameElement = reference.getReferenceNameElement();
        int offset = Math.max(
                0,
                nameElement == null
                        ? reference.getTextOffset()
                        : nameElement.getTextOffset()
        );
        int lineNumber = 1;
        Document document = documentManager.getDocument(sourceFile);
        if (document != null) {
            lineNumber = document.getLineNumber(
                    Math.min(offset, document.getTextLength())
            ) + 1;
        }

        return new ModulithDependencyReference(
                sourceFile.getVirtualFile(),
                offset,
                lineNumber,
                sourceFile.getName() + ":" + lineNumber
        );
    }

    @NotNull
    private String packageName(@NotNull PsiClass psiClass) {
        PsiFile containingFile = psiClass.getContainingFile();
        return containingFile instanceof PsiJavaFile javaFile
                ? javaFile.getPackageName()
                : "";
    }

    private boolean isProjectSource(@NotNull PsiFile file) {
        return file.getVirtualFile() != null
                && fileIndex.isInSourceContent(file.getVirtualFile())
                && !fileIndex.isInTestSourceContent(file.getVirtualFile());
    }

    @Nullable
    private PsiJavaFile containingJavaFile(@NotNull PsiElement element) {
        PsiFile file = element.getContainingFile();
        return file instanceof PsiJavaFile javaFile ? javaFile : null;
    }

    @Nullable
    private ModulithModule findModule(
            @NotNull List<ModulithModule> modules,
            @NotNull String packageName) {
        ModulithModule best = null;
        for (ModulithModule module : modules) {
            if (!module.containsPackage(packageName)) {
                continue;
            }
            if (best == null
                    || module.getPackageName().length() > best.getPackageName().length()) {
                best = module;
            }
        }
        return best;
    }
}
