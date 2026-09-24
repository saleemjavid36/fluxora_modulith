package com.springmodulith.plugin.codeInsight;

import com.intellij.codeInsight.highlighting.HighlightedReference;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.project.Project;
import com.intellij.psi.ElementManipulators;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiLiteralExpression;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiReferenceBase;
import com.intellij.psi.PsiReferenceContributor;
import com.intellij.psi.PsiReferenceProvider;
import com.intellij.psi.PsiReferenceRegistrar;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.util.ProcessingContext;
import com.springmodulith.plugin.model.ModulithModule;
import com.springmodulith.plugin.resolver.ModulithModuleResolver;
import org.jetbrains.annotations.NotNull;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaFile;
import org.jetbrains.annotations.Nullable;

public final class ModulithDependencyReferenceContributor extends PsiReferenceContributor {
    @Override
    public void registerReferenceProviders(@NotNull PsiReferenceRegistrar registrar) {
        registrar.registerReferenceProvider(PlatformPatterns.psiElement(PsiLiteralExpression.class), new PsiReferenceProvider() {
            @Override
            public PsiReference @NotNull [] getReferencesByElement(@NotNull PsiElement element, @NotNull ProcessingContext context) {
                PsiAnnotation annotation = PsiTreeUtil.getParentOfType(element, PsiAnnotation.class);
                if (annotation == null || !"org.springframework.modulith.ApplicationModule".equals(annotation.getQualifiedName()) ||
                        annotation.findDeclaredAttributeValue("allowedDependencies") == null)
                    return PsiReference.EMPTY_ARRAY;
                String value = ElementManipulators.getValueText(element);
                if (ModulithModule.DependencyRule.parse(value) == null) return PsiReference.EMPTY_ARRAY;
                return new PsiReference[]{new DependencyReference(element, ElementManipulators.getValueTextRange(element))};
            }
        });
    }

    private static final class DependencyReference
            extends PsiReferenceBase<PsiElement>
            implements HighlightedReference {

        DependencyReference(
                @NotNull PsiElement element,
                @NotNull TextRange range) {

            super(element, range, true);
        }

        @Override
        public boolean isHighlightedWhenSoft() {
            return true;
        }

        @Override
        public PsiElement resolve() {
            Project project = getElement().getProject();

            ModulithModule.DependencyRule rule =
                    ModulithModule.DependencyRule.parse(getValue());

            if (rule == null) {
                return null;
            }

            ModulithModuleResolver resolver =
                    new ModulithModuleResolver(project);

            for (ModulithModule module :
                    resolver.resolveModules(getElement().getContainingFile())) {

                if (!module.matchesModuleId(rule.moduleId())) {
                    continue;
                }

                String targetPackage = module.getPackageName();

                /*
                 * Example:
                 *
                 * "student :: repository"
                 *
                 * Find the package represented by the named interface.
                 */
                if (rule.interfaceId() != null
                        && !"*".equals(rule.interfaceId())) {

                    com.springmodulith.plugin.model.NamedInterface namedInterface =
                            module.findNamedInterface(rule.interfaceId());

                    if (namedInterface == null) {
                        return null;
                    }

                    targetPackage = namedInterface.getPackageName();
                }

                return findPackageInfoFile(
                        resolver,
                        targetPackage
                );
            }

            return null;
        }

        @Nullable
        private PsiFile findPackageInfoFile(
                @NotNull ModulithModuleResolver resolver,
                @NotNull String packageName) {

            PsiDirectory directory =
                    resolver.findDirectoryForPackage(packageName);

            if (directory == null) {
                return null;
            }

            for (PsiFile file : directory.getFiles()) {
                if (file instanceof PsiJavaFile
                        && "package-info.java".equals(file.getName())) {
                    return file;
                }
            }

            return null;
        }
    }
}


//hello
