package com.springmodulith.plugin.resolver;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.JavaDirectoryService;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiPackage;
import com.intellij.psi.PsiStatement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiArrayInitializerMemberValue;
import com.intellij.psi.PsiAnnotationMemberValue;
import com.intellij.psi.PsiNameValuePair;
import com.springmodulith.plugin.configuration.ModulithSettings;
import com.springmodulith.plugin.model.ModulithModule;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import com.intellij.psi.PsiModifierList;
import com.intellij.psi.PsiPackageStatement;
import com.intellij.psi.PsiModifierList;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class ModulithModuleResolver {

    private static final String APPLICATION_MODULE =
            "org.springframework.modulith.ApplicationModule";

    private static final String APPLICATION_MODULE_SIMPLE_NAME =
            "ApplicationModule";

    private static final String ALLOWED_DEPENDENCIES =
            "allowedDependencies";

    @NotNull
    private final Project project;

    public ModulithModuleResolver(@NotNull Project project) {
        this.project = project;
    }

    @NotNull
    public List<ModulithModule> resolveModules(
            @NotNull PsiFile psiFile) {

        String rootPackage = resolveRootPackage();

        if (rootPackage == null || rootPackage.isEmpty()) {
            return Collections.emptyList();
        }

        List<ModulithModule> modules = new ArrayList<>();

        ProjectFileIndex fileIndex =
                ProjectRootManager
                        .getInstance(project)
                        .getFileIndex();

        PsiManager psiManager =
                PsiManager.getInstance(project);

        fileIndex.iterateContent(file -> {

            if (!file.isDirectory()
                    || !fileIndex.isInSourceContent(file)) {
                return true;
            }

            PsiDirectory directory =
                    psiManager.findDirectory(file);

            if (directory == null) {
                return true;
            }

            collectModules(
                    directory,
                    rootPackage,
                    modules
            );

            return true;
        });

        modules.sort(
                Comparator.comparing(
                        ModulithModule::getPackageName
                )
        );

        return modules;
    }

    @Nullable
    public ModulithModule resolveModule(
            @NotNull PsiFile psiFile,
            @NotNull String packageName) {

        List<ModulithModule> modules =
                resolveModules(psiFile);

        ModulithModule bestMatch = null;

        for (ModulithModule module : modules) {

            String modulePackage =
                    module.getPackageName();

            if (packageName.equals(modulePackage)
                    || packageName.startsWith(
                    modulePackage + ".")) {

                if (bestMatch == null
                        || modulePackage.length()
                        > bestMatch.getPackageName().length()) {

                    bestMatch = module;
                }
            }
        }

        return bestMatch;
    }

    @Nullable
    private String resolveRootPackage() {

        String configuredRoot =
                ModulithSettings
                        .getInstance(project)
                        .getRootPackage();

        if (configuredRoot != null
                && !configuredRoot.trim().isEmpty()) {

            return configuredRoot.trim();
        }

        return findSpringBootRootPackage();
    }

    @Nullable
    private String findSpringBootRootPackage() {

        final String[] rootPackage = {null};

        ProjectFileIndex fileIndex =
                ProjectRootManager
                        .getInstance(project)
                        .getFileIndex();

        PsiManager psiManager =
                PsiManager.getInstance(project);

        fileIndex.iterateContent(file -> {

            if (!file.isDirectory()
                    || !fileIndex.isInSourceContent(file)) {
                return true;
            }

            PsiDirectory directory =
                    psiManager.findDirectory(file);

            if (directory == null) {
                return true;
            }

            if (!containsSpringBootApplication(directory)) {
                return true;
            }

            rootPackage[0] =
                    getPackageName(directory);

            return false;
        });

        return rootPackage[0];
    }

    private void collectModules(
            @NotNull PsiDirectory directory,
            @NotNull String rootPackage,
            @NotNull List<ModulithModule> modules) {

        String packageName =
                getPackageName(directory);

        if (packageName == null
                || packageName.isEmpty()) {
            return;
        }

        if (!packageName.equals(rootPackage)
                && !packageName.startsWith(
                rootPackage + ".")) {
            return;
        }

        PsiAnnotation applicationModule =
                findApplicationModule(directory);

        if (applicationModule != null) {

            String moduleName =
                    getModuleName(packageName);

            Set<String> allowedDependencies =
                    readAllowedDependencies(
                            applicationModule
                    );

            modules.add(
                    new ModulithModule(
                            moduleName,
                            packageName,
                            allowedDependencies
                    )
            );

            return;
        }

        for (PsiDirectory subDirectory :
                directory.getSubdirectories()) {

            collectModules(
                    subDirectory,
                    rootPackage,
                    modules
            );
        }
    }

    @Nullable
    private PsiAnnotation findApplicationModule(
            @NotNull PsiDirectory directory) {

        for (PsiFile file : directory.getFiles()) {

            if (!(file instanceof PsiJavaFile)) {
                continue;
            }

            PsiJavaFile javaFile =
                    (PsiJavaFile) file;

            PsiPackage packageStatement =
                    javaFile.getPackageStatement() == null
                            ? null
                            : JavaDirectoryService
                            .getInstance()
                            .getPackage(directory);

            if (packageStatement == null) {
                continue;
            }

            PsiAnnotation annotation =
                    findApplicationModule(
                            javaFile
                    );

            if (annotation != null) {
                return annotation;
            }
        }

        return null;
    }

    @Nullable
    private PsiAnnotation findApplicationModule(@NotNull PsiJavaFile javaFile) {
        PsiPackageStatement packageStatement =
                javaFile.getPackageStatement();

        if (packageStatement == null) {
            return null;
        }

        PsiModifierList annotationList =
                packageStatement.getAnnotationList();

        if (annotationList == null) {
            return null;
        }

        PsiAnnotation[] annotations =
                annotationList.getAnnotations();

        for (PsiAnnotation annotation : annotations) {
            String qualifiedName =
                    annotation.getQualifiedName();

            if (APPLICATION_MODULE.equals(qualifiedName)) {
                return annotation;
            }
        }

        return null;
    }

    @NotNull
    private Set<String> readAllowedDependencies(
            @NotNull PsiAnnotation annotation) {

        Set<String> dependencies =
                new LinkedHashSet<>();

        PsiNameValuePair[] attributes =
                annotation.getParameterList()
                        .getAttributes();

        for (PsiNameValuePair attribute : attributes) {

            if (!ALLOWED_DEPENDENCIES.equals(
                    attribute.getName())) {
                continue;
            }

            PsiAnnotationMemberValue value =
                    attribute.getValue();

            if (value == null) {
                continue;
            }

            if (value instanceof
                    PsiArrayInitializerMemberValue) {

                PsiArrayInitializerMemberValue array =
                        (PsiArrayInitializerMemberValue) value;

                for (PsiAnnotationMemberValue initializer :
                        array.getInitializers()) {

                    String dependency =
                            getStringValue(initializer);

                    if (dependency != null
                            && !dependency.isEmpty()) {

                        dependencies.add(dependency);
                    }
                }

            } else {

                String dependency =
                        getStringValue(value);

                if (dependency != null
                        && !dependency.isEmpty()) {

                    dependencies.add(dependency);
                }
            }
        }

        return dependencies;
    }

    @Nullable
    private String getStringValue(
            @NotNull PsiAnnotationMemberValue value) {

        if (value instanceof PsiExpression) {

            PsiExpression expression =
                    (PsiExpression) value;

            return expression
                    .getText()
                    .replace("\"", "")
                    .replace("'", "")
                    .trim();
        }

        return null;
    }

    @NotNull
    private String getModuleName(
            @NotNull String packageName) {

        int lastDot =
                packageName.lastIndexOf('.');

        if (lastDot < 0) {
            return packageName;
        }

        return packageName.substring(lastDot + 1);
    }

    @Nullable
    private String getPackageName(
            @NotNull PsiDirectory directory) {

        PsiPackage psiPackage =
                JavaDirectoryService
                        .getInstance()
                        .getPackage(directory);

        return psiPackage == null
                ? null
                : psiPackage.getQualifiedName();
    }

    private boolean containsSpringBootApplication(
            @NotNull PsiDirectory directory) {

        for (PsiFile file : directory.getFiles()) {

            if (!(file instanceof PsiJavaFile)) {
                continue;
            }

            PsiJavaFile javaFile =
                    (PsiJavaFile) file;

            if (javaFile.getClasses().length == 0) {
                continue;
            }

            for (com.intellij.psi.PsiClass psiClass :
                    javaFile.getClasses()) {

                if (psiClass.hasAnnotation(
                        "org.springframework.boot.autoconfigure.SpringBootApplication")) {

                    return true;
                }
            }
        }

        return false;
    }
}