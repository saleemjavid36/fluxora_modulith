package com.springmodulith.plugin.resolver;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.psi.JavaDirectoryService;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiAnnotationMemberValue;
import com.intellij.psi.PsiArrayInitializerMemberValue;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiLiteralExpression;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiModifierList;
import com.intellij.psi.PsiNameValuePair;
import com.intellij.psi.PsiPackage;
import com.intellij.psi.PsiPackageStatement;
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
    private static final String OPEN = "open";
    private final Project project;

    public ModulithModuleResolver(@NotNull Project project) { this.project = project; }

    @NotNull
    public List<ModulithModule> resolveModules() {
        String rootPackage = resolveRootPackage(null);
        if (rootPackage.isEmpty()) return List.of();
        PsiDirectory rootDirectory = findDirectoryForPackage(rootPackage);
        if (rootDirectory == null) return List.of();
        List<ModulithModule> result = new ArrayList<>();
        String strategy = ModulithSettings.getInstance(project).getDetectionStrategy();
        if (ModulithSettings.EXPLICITLY_ANNOTATED.equals(strategy)) {
            collectExplicitModules(rootDirectory, rootPackage, result);
        } else {
            for (PsiDirectory child : rootDirectory.getSubdirectories()) {
                String packageName = packageName(child);
                if (!packageName.isEmpty()) result.add(toModule(child, packageName));
            }
            collectNestedExplicitModules(rootDirectory, result);
        }
        result.sort(Comparator.comparing(ModulithModule::getPackageName));
        return deduplicate(result);
    }

    @NotNull
    public List<ModulithModule> resolveModules(@NotNull PsiFile contextFile) {
        String rootPackage = resolveRootPackage(contextFile);
        if (rootPackage.isEmpty()) return List.of();
        PsiDirectory rootDirectory = findDirectoryForPackage(rootPackage);
        if (rootDirectory == null) return List.of();

        List<ModulithModule> result = new ArrayList<>();
        String strategy = ModulithSettings.getInstance(project).getDetectionStrategy();
        if (ModulithSettings.EXPLICITLY_ANNOTATED.equals(strategy)) {
            collectExplicitModules(rootDirectory, rootPackage, result);
        } else {
            for (PsiDirectory child : rootDirectory.getSubdirectories()) {
                String packageName = packageName(child);
                if (!packageName.isEmpty()) result.add(toModule(child, packageName));
            }
            collectNestedExplicitModules(rootDirectory, result);
        }
        result.sort(Comparator.comparing(ModulithModule::getPackageName));
        return deduplicate(result);
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


    @Nullable
    public PsiDirectory findDirectoryForPackage(@NotNull String qualifiedName) {
        ProjectFileIndex index = ProjectRootManager.getInstance(project).getFileIndex();
        PsiManager manager = PsiManager.getInstance(project);
        final PsiDirectory[] found = {null};
        index.iterateContent(file -> {
            if (!file.isDirectory() || !index.isInSourceContent(file)) return true;
            PsiDirectory directory = manager.findDirectory(file);
            if (directory != null && qualifiedName.equals(packageName(directory))) {
                found[0] = directory;
                return false;
            }
            return true;
        });
        return found[0];
    }

    private void collectExplicitModules(@NotNull PsiDirectory directory, @NotNull String rootPackage, @NotNull List<ModulithModule> result) {
        String pkg = packageName(directory);
        if (!pkg.isEmpty() && pkg.startsWith(rootPackage) && hasApplicationModule(directory)) result.add(toModule(directory, pkg));
        for (PsiDirectory child : directory.getSubdirectories()) collectExplicitModules(child, rootPackage, result);
    }

    private void collectNestedExplicitModules(@NotNull PsiDirectory directory, @NotNull List<ModulithModule> result) {
        for (PsiDirectory child : directory.getSubdirectories()) {
            if (hasApplicationModule(child)) {
                result.add(toModule(child, packageName(child)));
            }
            collectNestedExplicitModules(child, result);
        }
    }

    @NotNull
    private ModulithModule toModule(@NotNull PsiDirectory directory, @NotNull String packageName) {
        PsiAnnotation applicationModule = findPackageAnnotation(directory, APPLICATION_MODULE);
        boolean allowedConfigured = applicationModule != null && applicationModule.findDeclaredAttributeValue(ALLOWED_DEPENDENCIES) != null;
        Set<String> allowed = applicationModule == null ? Set.of() : readStringAttribute(applicationModule, ALLOWED_DEPENDENCIES);
        boolean open = applicationModule != null && readBooleanAttribute(applicationModule, OPEN, false);
        return new ModulithModule(getModuleName(packageName), packageName, open, allowedConfigured, allowed, collectNamedInterfaces(directory, packageName));
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
        if (!pkg.startsWith(modulePackage)) return;
        PsiAnnotation packageAnnotation = findPackageAnnotation(directory, NAMED_INTERFACE);
        if (packageAnnotation != null) {
            for (String name : readNamedInterfaceNames(packageAnnotation, pkg.substring(modulePackage.length() + (pkg.equals(modulePackage) ? 0 : 1)))) {
                String interfaceName = name.isEmpty() ? simplePackageName(pkg) : name;
                String key = interfaceName + "@" + pkg;
                if (visited.add(key)) result.add(new NamedInterface(interfaceName, pkg, collectPublicTypes(directory, modulePackage)));
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
    private Set<String> collectPublicTypes(@NotNull PsiDirectory directory, @NotNull String modulePackage) {
        Set<String> result = new LinkedHashSet<>();
        for (PsiFile file : directory.getFiles()) {
            if (!(file instanceof PsiJavaFile javaFile)) continue;
            for (PsiClass psiClass : javaFile.getClasses()) {
                if (psiClass.getQualifiedName() != null && psiClass.hasModifierProperty("public")) result.add(psiClass.getQualifiedName());
            }
        }
        return result;
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
    private PsiAnnotation findPackageAnnotation(@NotNull PsiDirectory directory, @NotNull String fqn) {
        for (PsiFile file : directory.getFiles()) {
            if (!(file instanceof PsiJavaFile javaFile)) continue;
            PsiPackageStatement statement = javaFile.getPackageStatement();
            if (statement == null) continue;
            PsiModifierList list = statement.getAnnotationList();
            if (list == null) continue;
            for (PsiAnnotation annotation : list.getAnnotations()) if (fqn.equals(annotation.getQualifiedName())) return annotation;
        }
        return null;
    }

    private boolean hasApplicationModule(@NotNull PsiDirectory directory) { return findPackageAnnotation(directory, APPLICATION_MODULE) != null; }

    @NotNull
    private String resolveRootPackage(@Nullable PsiFile contextFile) {
        ModulithSettings settings = ModulithSettings.getInstance(project);
        if (!settings.getRootPackage().isEmpty()) return settings.getRootPackage();
        PsiDirectory contextDirectory = contextFile == null ? null : PsiTreeUtil.getParentOfType(contextFile, PsiDirectory.class);
        String fromContext = contextDirectory == null ? "" : findNearestSpringBootPackage(contextDirectory);
        if (!fromContext.isEmpty()) return fromContext;
        return findSpringBootRootPackage();
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
        final String[] result = {""};
        index.iterateContent(file -> {
            if (!file.isDirectory() || !index.isInSourceContent(file)) return true;
            PsiDirectory directory = manager.findDirectory(file);
            if (directory != null && containsSpringBootApplication(directory)) {
                result[0] = packageName(directory);
                return false;
            }
            return true;
        });
        return result[0];
    }

    private boolean containsSpringBootApplication(@NotNull PsiDirectory directory) {
        for (PsiFile file : directory.getFiles()) {
            if (!(file instanceof PsiJavaFile javaFile)) continue;
            for (PsiClass psiClass : javaFile.getClasses()) {
                if (psiClass.hasAnnotation(SPRING_BOOT_APPLICATION) || psiClass.hasAnnotation(SPRING_BOOT_CONFIGURATION)) return true;
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
