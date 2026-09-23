package com.springmodulith.plugin.codeInsight;

import com.intellij.codeInsight.completion.CompletionContributor;
import com.intellij.codeInsight.completion.CompletionParameters;
import com.intellij.codeInsight.completion.CompletionProvider;
import com.intellij.codeInsight.completion.CompletionResultSet;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.icons.AllIcons;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiLiteralExpression;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ProcessingContext;
import com.springmodulith.plugin.model.ModulithModule;
import com.springmodulith.plugin.resolver.ModulithModuleResolver;
import org.jetbrains.annotations.NotNull;

import static com.intellij.codeInsight.completion.CompletionType.BASIC;

public final class ModulithDependencyCompletionContributor
        extends CompletionContributor {

    public ModulithDependencyCompletionContributor() {
        extend(
                BASIC,
                PlatformPatterns.psiElement(),
                new Provider()
        );
    }

    private static final class Provider
            extends CompletionProvider<CompletionParameters> {

        @Override
        protected void addCompletions(
                @NotNull CompletionParameters parameters,
                @NotNull ProcessingContext context,
                @NotNull CompletionResultSet result) {

            PsiElement element = parameters.getPosition();

            PsiLiteralExpression literal =
                    PsiTreeUtil.getParentOfType(
                            element,
                            PsiLiteralExpression.class
                    );

            if (literal == null) {
                return;
            }

            PsiAnnotation annotation =
                    PsiTreeUtil.getParentOfType(
                            literal,
                            PsiAnnotation.class
                    );

            if (!isAllowedDependenciesAnnotation(annotation)) {
                return;
            }

            ModulithModuleResolver resolver =
                    new ModulithModuleResolver(
                            element.getProject()
                    );

            for (ModulithModule module :
                    resolver.resolveModules(
                            literal.getContainingFile())) {

                addModuleSuggestion(
                        result,
                        module
                );

                /*
                 * Spring Modulith wildcard:
                 *
                 * "student :: *"
                 *
                 * Means the complete set of named interfaces
                 * exposed by the target module.
                 */
                result.addElement(
                        LookupElementBuilder
                                .create(
                                        module.getName() + " :: *"
                                )
                                .withIcon(AllIcons.Nodes.Package)
                                .withTypeText(
                                        module.getPackageName(),
                                        true
                                )
                );

                for (var namedInterface :
                        module.getNamedInterfaces()) {

                    result.addElement(
                            LookupElementBuilder
                                    .create(
                                            module.getName()
                                                    + " :: "
                                                    + namedInterface.getName()
                                    )
                                    .withIcon(
                                            AllIcons.Nodes.Package
                                    )
                                    .withTypeText(
                                            namedInterface.getPackageName(),
                                            true
                                    )
                    );
                }
            }
        }

        private static void addModuleSuggestion(
                @NotNull CompletionResultSet result,
                @NotNull ModulithModule module) {

            result.addElement(
                    LookupElementBuilder
                            .create(module.getName())
                            .withIcon(AllIcons.Nodes.Package)
                            .withTypeText(
                                    module.getPackageName(),
                                    true
                            )
            );
        }

        private static boolean isAllowedDependenciesAnnotation(
                PsiAnnotation annotation) {

            if (annotation == null) {
                return false;
            }

            if (!"org.springframework.modulith.ApplicationModule"
                    .equals(annotation.getQualifiedName())) {
                return false;
            }

            return annotation.findDeclaredAttributeValue(
                    "allowedDependencies"
            ) != null;
        }
    }
}