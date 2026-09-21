package com.springmodulith.plugin.resolver;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.springmodulith.plugin.configuration.ModulithSettings;
import com.springmodulith.plugin.model.ModulithModule;
import com.springmodulith.plugin.model.NamedInterface;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class ModulithModuleResolver {
    public static final String APPLICATION_MODULE = "org.springframework.modulith.ApplicationModule";
    public static final String NAMED_INTERFACE = "org.springframework.modulith.NamedInterface";
    public static final String SPRING_BOOT_APPLICATION = "org.springframework.boot.autoconfigure.SpringBootApplication";
    public static final String SPRING_BOOT_CONFIGURATION = "org.springframework.boot.SpringBootConfiguration";

    private static final String ALLOWED_DEPENDENCIES = "allowedDependencies";
    private static final String TYPE = "type";
    private static final String OPEN = "open";
    private final Project project;

    public ModulithModuleResolver(@NotNull Project project) { this.project = project; }

    @NotNull
    public List<ModulithModule> resolveModules() {
        return resolveModules(null);
    }

    @NotNull
    public List<ModulithModule> resolveModules(@Nullable PsiFile contextFile) {
        String rootPackage = resolveRootPackage(contextFile);
        List<ModulithModule> result = new ArrayList<>();

        ModulithSettings settings = ModulithSettings.getInstance(project);
        String strategy = settings.getDetectionStrategy();

        if (!rootPackage.isEmpty()) {
            List<PsiDirectory> roots = findDirectoriesForPackage(rootPackage);

            for (PsiDirectory rootDirectory : roots) {
                if (hasApplicationModule(rootDirectory)
                        && containsJavaSource(rootDirectory)) {
                    result.add(toModule(rootDirectory, rootPackage));
                }

                if (ModulithSettings.EXPLICITLY_ANNOTATED.equals(strategy)) {
                    collectExplicitModules(rootDirectory, rootPackage, result);
                } else {
                    collectDirectModules(rootDirectory, result);
                    collectNestedExplicitModules(rootDirectory, result);
                }
            }
        }

        /*
         * Generic fallback:
         *
         * If there is no configured Spring Boot/Modulith root package,
         * derive the root from the common package of Java source files.
         *
         * Example:
         *
         * io.company.beta.BetaService
         * io.company.gamma.GammaService
         *
         * common package = io.company
         *
         * Therefore:
         * io.company.beta   -> module
         * io.company.gamma  -> module
         *
         * No module names are hardcoded.
         */
        if (result.isEmpty()
                && rootPackage.isEmpty()
                && !ModulithSettings.EXPLICITLY_ANNOTATED.equals(strategy)) {

            String inferredRootPackage = findSourcePackageRoot();

            if (!inferredRootPackage.isEmpty()) {
                PsiDirectory inferredRoot =
                        findDirectoryForPackage(inferredRootPackage);

                if (inferredRoot != null) {
                    collectDirectModules(inferredRoot, result);
                    collectNestedExplicitModules(inferredRoot, result);
                }
            }
        }

        /*
         * Explicit @ApplicationModule declarations remain authoritative.
         */
        if (result.isEmpty()) {
            collectAllExplicitModules(result);
        }

        /*
         * User-configured additional module packages remain supported.
         */
        for (String additionalPackage : settings.getAdditionalModulePackages()) {
            for (PsiDirectory directory : findDirectoriesForPackage(additionalPackage)) {
                if (containsJavaSource(directory)) {
                    result.add(toModule(directory, additionalPackage));
                }
            }
        }

        List<ModulithModule> filtered = deduplicate(result);

        filtered.removeIf(module ->
                isExcluded(
                        module.getPackageName(),
                        settings.getExcludedPackagePrefixes()
                )
        );

        filtered.sort(
                Comparator.comparing(ModulithModule::getPackageName)
        );

        return filtered;
    }
    @NotNull
    private String findSourcePackageRoot() {
        ProjectFileIndex index =
                ProjectRootManager.getInstance(project).getFileIndex();

        PsiManager manager =
                PsiManager.getInstance(project);

        List<String> sourcePackages = new ArrayList<>();

        index.iterateContent(file -> {
            if (!file.isInLocalFileSystem()
                    || file.isDirectory()
                    || !index.isInSourceContent(file)
                    || index.isInTestSourceContent(file)
                    || !file.getName().endsWith(".java")) {
                return true;
            }

            PsiFile psiFile = manager.findFile(file);

            if (psiFile instanceof PsiJavaFile javaFile) {
                PsiPackageStatement statement =
                        javaFile.getPackageStatement();

                if (statement != null) {
                    String packageName =
                            statement.getPackageName();

                    if (packageName != null
                            && !packageName.isBlank()) {
                        sourcePackages.add(packageName);
                    }
                }
            }

            return true;
        });

        return findCommonPackage(sourcePackages);
    }

    private boolean isExcluded(@NotNull String packageName, @NotNull List<String> prefixes) {
        for (String prefix : prefixes) {
            if (packageName.equals(prefix) || packageName.startsWith(prefix + ".")) return true;
        }
        return false;
    }

    private void collectDirectModules(
            @NotNull PsiDirectory rootDirectory,
            @NotNull List<ModulithModule> result) {
        for (PsiDirectory child : rootDirectory.getSubdirectories()) {
            String packageName = packageName(child);
            if (!packageName.isEmpty()
                    && containsJavaSource(child)) {
                result.add(toModule(child, packageName));
            }
        }
    }

    private boolean containsJavaSource(@NotNull PsiDirectory directory) {
        for (PsiFile file : directory.getFiles()) {
            if (file instanceof PsiJavaFile) return true;
        }
        for (PsiDirectory child : directory.getSubdirectories()) {
            if (containsJavaSource(child)) return true;
        }
        return false;
    }

    private void collectAllExplicitModules(@NotNull List<ModulithModule> result) {
        ProjectFileIndex index = ProjectRootManager.getInstance(project).getFileIndex();
        PsiManager manager = PsiManager.getInstance(project);
        index.iterateContent(file -> {
            if (!file.isDirectory()
                    || !index.isInSourceContent(file)
                    || index.isInTestSourceContent(file)) {
                return true;
            }
            PsiDirectory directory = manager.findDirectory(file);
            if (directory != null && hasApplicationModule(directory)) {
                String packageName = packageName(directory);
                if (!packageName.isEmpty()) {
                    result.add(toModule(directory, packageName));
                }
            }
            return true;
        });
    }

    @Nullable
    public ModulithModule resolveModule(@NotNull PsiFile contextFile, @NotNull String packageName) {
        ModulithModule best = null;
        for (ModulithModule module : resolveModules(contextFile)) {
            if (module.containsPackage(packageName) && (best == null || module.getPackageName().length() > best.getPackageName().length())) {
                best = module;
            }
        }
        return best;
    }


    @NotNull
    public List<PsiDirectory> findDirectoriesForPackage(
            @NotNull String qualifiedName) {
        PsiPackage psiPackage =
                JavaPsiFacade.getInstance(project).findPackage(qualifiedName);
        if (psiPackage == null) return List.of();

        ProjectFileIndex index =
                ProjectRootManager.getInstance(project).getFileIndex();
        List<PsiDirectory> result = new ArrayList<>();
        for (PsiDirectory directory : psiPackage.getDirectories()) {
            VirtualFile virtualFile = directory.getVirtualFile();
            if (virtualFile != null
                    && index.isInSourceContent(virtualFile)
                    && !index.isInTestSourceContent(virtualFile)) {
                result.add(directory);
            }
        }
        return result;
    }

    @Nullable
    public PsiDirectory findDirectoryForPackage(
            @NotNull String qualifiedName) {
        List<PsiDirectory> directories = findDirectoriesForPackage(qualifiedName);
        return directories.isEmpty() ? null : directories.get(0);
    }

    private void collectExplicitModules(
            @NotNull PsiDirectory directory,
            @NotNull String rootPackage,
            @NotNull List<ModulithModule> result) {
        String pkg = packageName(directory);
        if (!pkg.isEmpty()
                && (pkg.equals(rootPackage) || pkg.startsWith(rootPackage + "."))
                && hasApplicationModule(directory)) {
            result.add(toModule(directory, pkg));
        }
        for (PsiDirectory child : directory.getSubdirectories()) {
            collectExplicitModules(child, rootPackage, result);
        }
    }

    private void collectNestedExplicitModules(
            @NotNull PsiDirectory directory,
            @NotNull List<ModulithModule> result) {
        for (PsiDirectory child : directory.getSubdirectories()) {
            if (hasApplicationModule(child)) {
                String packageName = packageName(child);
                if (!packageName.isEmpty() && containsJavaSource(child)) {
                    result.add(toModule(child, packageName));
                }
            }
            collectNestedExplicitModules(child, result);
        }
    }

    @NotNull
    private ModulithModule toModule(
            @NotNull PsiDirectory directory,
            @NotNull String packageName) {

        PsiAnnotation applicationModule =
                findPackageAnnotation(directory, APPLICATION_MODULE);

        Set<String> allowed =
                applicationModule == null
                        ? Set.of()
                        : readStringAttribute(
                        applicationModule,
                        ALLOWED_DEPENDENCIES
                );

        PsiAnnotationMemberValue allowedDependenciesValue =
                applicationModule == null
                        ? null
                        : applicationModule.findDeclaredAttributeValue(
                        ALLOWED_DEPENDENCIES
                );

        boolean allowedConfigured =
                applicationModule != null
                        && allowedDependenciesValue != null;

        boolean open =
                applicationModule != null
                        && isOpenModule(applicationModule);

        return new ModulithModule(
                getModuleName(packageName),
                packageName,
                open,
                allowedConfigured,
                allowed,
                collectNamedInterfaces(directory, packageName)
        );
    }
    private boolean isOpenModule(
            @NotNull PsiAnnotation annotation) {

        PsiAnnotationMemberValue typeValue =
                annotation.findDeclaredAttributeValue(TYPE);

        if (typeValue != null) {

            String text =
                    typeValue.getText();

            if (text != null
                    && text.trim().endsWith("OPEN")) {

                return true;
            }
        }

        /*
         * Compatibility fallback.
         */
        return readBooleanAttribute(
                annotation,
                OPEN,
                false
        );
    }

    @NotNull
    private List<NamedInterface> collectNamedInterfaces(@NotNull PsiDirectory moduleDirectory, @NotNull String modulePackage) {
        List<NamedInterface> result = new ArrayList<>();
        collectNamedInterfaces(moduleDirectory, modulePackage, result, new HashSet<>());
        return result;
    }

    private void collectNamedInterfaces(@NotNull PsiDirectory directory, @NotNull String modulePackage,
                                        @NotNull List<NamedInterface> result, @NotNull Set<String> visited) {
        String pkg = packageName(directory);
        if (!pkg.equals(modulePackage) && !pkg.startsWith(modulePackage + ".")) return;
        PsiAnnotation packageAnnotation = findPackageAnnotation(directory, NAMED_INTERFACE);
        if (packageAnnotation != null) {
            for (String name : readNamedInterfaceNames(packageAnnotation, pkg.substring(modulePackage.length() + (pkg.equals(modulePackage) ? 0 : 1)))) {
                String interfaceName = name.isEmpty() ? simplePackageName(pkg) : name;
                String key = interfaceName + "@" + pkg;
                if (visited.add(key)) result.add(new NamedInterface(interfaceName, pkg, collectPublicTypesRecursively(directory, pkg)));
            }
        }
        for (PsiFile file : directory.getFiles()) {
            if (!(file instanceof PsiJavaFile javaFile)) continue;
            for (PsiClass psiClass : javaFile.getClasses()) {
                PsiAnnotation annotation = psiClass.getAnnotation(NAMED_INTERFACE);
                if (annotation != null) {
                    for (String name : readNamedInterfaceNames(annotation, "")) {
                        String interfaceName = name.isEmpty() ? psiClass.getName() : name;
                        if (interfaceName != null && visited.add(interfaceName + "@" + pkg + "#" + psiClass.getQualifiedName())) {
                            Set<String> types = new LinkedHashSet<>();
                            String qn = psiClass.getQualifiedName();
                            if (qn != null) types.add(qn);
                            result.add(new NamedInterface(interfaceName, pkg, types));
                        }
                    }
                }
            }
        }
        for (PsiDirectory child : directory.getSubdirectories()) collectNamedInterfaces(child, modulePackage, result, visited);
    }

    @NotNull
    private Set<String> collectPublicTypesRecursively(@NotNull PsiDirectory directory, @NotNull String namedPackage) {
        Set<String> result = new LinkedHashSet<>();
        collectPublicTypesRecursively(directory, namedPackage, result);
        return result;
    }

    private void collectPublicTypesRecursively(@NotNull PsiDirectory directory, @NotNull String namedPackage,
                                               @NotNull Set<String> result) {
        if (!namedPackage.equals(packageName(directory)) && !packageName(directory).startsWith(namedPackage + ".")) return;
        for (PsiFile file : directory.getFiles()) {
            if (!(file instanceof PsiJavaFile javaFile)) continue;
            for (PsiClass psiClass : javaFile.getClasses()) {
                String qualifiedName = psiClass.getQualifiedName();
                if (qualifiedName != null && psiClass.hasModifierProperty("public")) result.add(qualifiedName);
            }
        }
        for (PsiDirectory child : directory.getSubdirectories()) {
            collectPublicTypesRecursively(child, namedPackage, result);
        }
    }

    @NotNull
    private List<String> readNamedInterfaceNames(@NotNull PsiAnnotation annotation, @NotNull String defaultName) {
        List<String> result = new ArrayList<>();
        PsiAnnotationMemberValue value = annotation.findDeclaredAttributeValue("value");
        if (value == null) value = annotation.findDeclaredAttributeValue("name");
        if (value instanceof PsiArrayInitializerMemberValue array) {
            for (PsiAnnotationMemberValue item : array.getInitializers()) {
                String text = stringValue(item);
                if (text != null) result.add(text);
            }
        } else if (value != null) {
            String text = stringValue(value);
            if (text != null) result.add(text);
        }
        if (result.isEmpty()) result.add(defaultName);
        return result;
    }

    @NotNull
    private Set<String> readStringAttribute(@NotNull PsiAnnotation annotation, @NotNull String attribute) {
        Set<String> result = new LinkedHashSet<>();
        PsiAnnotationMemberValue value = annotation.findDeclaredAttributeValue(attribute);
        if (value instanceof PsiArrayInitializerMemberValue array) {
            for (PsiAnnotationMemberValue item : array.getInitializers()) {
                String text = stringValue(item);
                if (text != null && !text.isBlank()) result.add(text.trim());
            }
        } else if (value != null) {
            String text = stringValue(value);
            if (text != null && !text.isBlank()) result.add(text.trim());
        }
        return result;
    }

    private boolean readBooleanAttribute(@NotNull PsiAnnotation annotation, @NotNull String attribute, boolean defaultValue) {
        PsiAnnotationMemberValue value = annotation.findDeclaredAttributeValue(attribute);
        if (value instanceof PsiLiteralExpression literal) {
            Object v = literal.getValue();
            return v instanceof Boolean ? (Boolean) v : defaultValue;
        }
        return defaultValue;
    }

    @Nullable
    private String stringValue(@NotNull PsiAnnotationMemberValue value) {
        if (value instanceof PsiLiteralExpression literal && literal.getValue() instanceof String) return (String) literal.getValue();
        String text = value.getText();
        if (text != null && text.length() >= 2 && ((text.startsWith("\"") && text.endsWith("\"")) || (text.startsWith("'") && text.endsWith("'")))) {
            return text.substring(1, text.length() - 1);
        }
        return null;
    }

    @Nullable
    private PsiAnnotation findPackageAnnotation(
            @NotNull PsiDirectory directory,
            @NotNull String fqn) {

        for (PsiFile file : directory.getFiles()) {

            if (!(file instanceof PsiJavaFile javaFile)) {
                continue;
            }

            PsiPackageStatement statement =
                    javaFile.getPackageStatement();

            if (statement == null) {
                continue;
            }

            PsiModifierList list =
                    statement.getAnnotationList();

            if (list == null) {
                continue;
            }

            for (PsiAnnotation annotation :
                    list.getAnnotations()) {

                PsiJavaCodeReferenceElement reference =
                        annotation.getNameReferenceElement();

                if (reference == null) {
                    continue;
                }

                String annotationText =
                        reference.getText();

                if (fqn.equals(annotationText)
                        || "ApplicationModule".equals(annotationText)
                        && APPLICATION_MODULE.equals(fqn)
                        || "NamedInterface".equals(annotationText)
                        && NAMED_INTERFACE.equals(fqn)) {

                    return annotation;
                }
            }
        }

        return null;
    }

    private boolean hasApplicationModule(@NotNull PsiDirectory directory) { return findPackageAnnotation(directory, APPLICATION_MODULE) != null; }

    @NotNull
    private String resolveRootPackage(
            @Nullable PsiFile contextFile) {

        ModulithSettings settings =
                ModulithSettings.getInstance(project);

        if (!settings.getRootPackage().isEmpty()) {
            return settings.getRootPackage();
        }

        if (contextFile != null) {

            PsiDirectory contextDirectory =
                    PsiTreeUtil.getParentOfType(
                            contextFile,
                            PsiDirectory.class
                    );

            if (contextDirectory != null) {

                String fromContext =
                        findNearestSpringBootPackage(
                                contextDirectory
                        );

                if (!fromContext.isEmpty()) {
                    return fromContext;
                }
            }
        }

        String springBootRoot =
                findSpringBootRootPackage();

        if (!springBootRoot.isEmpty()) {
            return springBootRoot;
        }

        /*
         * If no Spring Boot application class can be found,
         * infer the root from @ApplicationModule packages.
         */
        String applicationModuleRoot =
                findApplicationModuleRootPackage();

        if (!applicationModuleRoot.isEmpty()) {
            return applicationModuleRoot;
        }

        return "";
    }
    @NotNull
    private String findApplicationModuleRootPackage() {

        ProjectFileIndex index =
                ProjectRootManager
                        .getInstance(project)
                        .getFileIndex();

        PsiManager manager =
                PsiManager.getInstance(project);

        List<String> modulePackages =
                new ArrayList<>();

        index.iterateContent(file -> {

            if (!file.isDirectory()
                    || !index.isInSourceContent(file)
                    || index.isInTestSourceContent(file)) {
                return true;
            }

            PsiDirectory directory =
                    manager.findDirectory(file);

            if (directory == null) {
                return true;
            }

            if (findPackageAnnotation(
                    directory,
                    APPLICATION_MODULE
            ) != null) {

                String packageName =
                        packageName(directory);

                if (!packageName.isEmpty()) {
                    modulePackages.add(packageName);
                }
            }

            return true;
        });

        if (modulePackages.isEmpty()) {
            return "";
        }

        return findCommonPackage(modulePackages);
    }
    @NotNull
    private String findCommonPackage(
            @NotNull List<String> packages) {

        if (packages.isEmpty()) {
            return "";
        }

        String[] common =
                packages.get(0).split("\\.");

        int commonLength =
                common.length;

        for (int i = 1; i < packages.size(); i++) {
            String[] current =
                    packages.get(i).split("\\.");

            commonLength =
                    Math.min(
                            commonLength,
                            current.length
                    );

            for (int j = 0; j < commonLength; j++) {
                if (!common[j].equals(current[j])) {
                    commonLength = j;
                    break;
                }
            }
        }

        if (commonLength == 0) {
            return "";
        }

        return String.join(
                ".",
                java.util.Arrays.copyOf(
                        common,
                        commonLength
                )
        );
    }

    @NotNull
    private String findNearestSpringBootPackage(@NotNull PsiDirectory directory) {
        PsiDirectory current = directory;
        while (current != null) {
            if (containsSpringBootApplication(current)) return packageName(current);
            current = current.getParentDirectory();
        }
        return "";
    }

    @NotNull
    private String findSpringBootRootPackage() {
        ProjectFileIndex index = ProjectRootManager.getInstance(project).getFileIndex();
        PsiManager manager = PsiManager.getInstance(project);
        List<String> candidates = new ArrayList<>();

        index.iterateContent(file -> {
            if (!file.isDirectory()
                    || !index.isInSourceContent(file)
                    || index.isInTestSourceContent(file)) {
                return true;
            }
            PsiDirectory directory = manager.findDirectory(file);
            if (directory != null && containsSpringBootApplication(directory)) {
                String packageName = packageName(directory);
                if (!packageName.isEmpty()) {
                    candidates.add(packageName);
                }
            }
            return true;
        });

        return findMostSpecificCommonPackage(candidates);
    }

    @NotNull
    private String findMostSpecificCommonPackage(@NotNull List<String> packages) {
        if (packages.isEmpty()) return "";
        if (packages.size() == 1) return packages.get(0);

        // Multiple Spring Boot entry points are possible. Use their common
        // package instead of arbitrarily selecting the first one.
        return findCommonPackage(packages);
    }

    private boolean containsSpringBootApplication(
            @NotNull PsiDirectory directory) {

        for (PsiFile file : directory.getFiles()) {

            if (!(file instanceof PsiJavaFile javaFile)) {
                continue;
            }

            for (PsiClass psiClass : javaFile.getClasses()) {

                PsiModifierList modifierList =
                        psiClass.getModifierList();

                if (modifierList == null) {
                    continue;
                }

                for (PsiAnnotation annotation :
                        modifierList.getAnnotations()) {

                    PsiJavaCodeReferenceElement reference =
                            annotation.getNameReferenceElement();

                    if (reference == null) {
                        continue;
                    }

                    String annotationName =
                            reference.getText();

                    if ("SpringBootApplication".equals(annotationName)
                            || SPRING_BOOT_APPLICATION.equals(annotationName)
                            || "SpringBootConfiguration".equals(annotationName)
                            || SPRING_BOOT_CONFIGURATION.equals(annotationName)) {

                        return true;
                    }
                }
            }
        }

        return false;
    }

    @NotNull
    private String packageName(@NotNull PsiDirectory directory) {
        PsiPackage pkg = JavaDirectoryService.getInstance().getPackage(directory);
        return pkg == null ? "" : pkg.getQualifiedName();
    }

    @NotNull
    private String getModuleName(@NotNull String packageName) {
        int index = packageName.lastIndexOf('.');
        return index < 0 ? packageName : packageName.substring(index + 1);
    }

    @NotNull
    private String simplePackageName(@NotNull String packageName) {
        int index = packageName.lastIndexOf('.');
        return index < 0 ? packageName : packageName.substring(index + 1);
    }

    @NotNull
    private List<ModulithModule> deduplicate(@NotNull List<ModulithModule> modules) {
        List<ModulithModule> result = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (ModulithModule module : modules) if (seen.add(module.getPackageName())) result.add(module);
        return result;
    }

}
