package com.springmodulith.plugin.codeInsight;

import com.intellij.codeInsight.completion.CompletionContributor;
import com.intellij.codeInsight.completion.CompletionParameters;
import com.intellij.codeInsight.completion.CompletionProvider;
import com.intellij.codeInsight.completion.CompletionResultSet;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiAnnotationMemberValue;
import com.intellij.psi.PsiArrayInitializerMemberValue;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiLiteralExpression;
import com.intellij.psi.PsiNameValuePair;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ProcessingContext;
import com.springmodulith.plugin.model.ModulithModule;
import com.springmodulith.plugin.resolver.ModulithModuleResolver;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.Set;

import static com.intellij.codeInsight.completion.CompletionType.BASIC;

public final class ModulithDependencyCompletionContributor extends CompletionContributor {

    private static final String APPLICATION_MODULE =
            ModulithModuleResolver.APPLICATION_MODULE;

    private static final String ALLOWED_DEPENDENCIES =
            "allowedDependencies";

    public ModulithDependencyCompletionContributor() {
        extend(
                BASIC,
                PlatformPatterns.psiElement(),
                new Provider()
        );
    }

    private static final class Provider extends CompletionProvider<CompletionParameters> {

        @Override
        protected void addCompletions(
                @NotNull CompletionParameters parameters,
                @NotNull ProcessingContext context,
                @NotNull CompletionResultSet result) {

            PsiElement position = parameters.getOriginalPosition();

            if (position == null) {
                return;
            }

            PsiNameValuePair attribute =
                    PsiTreeUtil.getParentOfType(position, PsiNameValuePair.class);

            if (attribute == null ||
                    !ALLOWED_DEPENDENCIES.equals(attribute.getAttributeName())) {
                return;
            }

            PsiAnnotation annotation =
                    PsiTreeUtil.getParentOfType(position, PsiAnnotation.class);

            if (annotation == null ||
                    !APPLICATION_MODULE.equals(annotation.getQualifiedName())) {
                return;
            }

            PsiAnnotationMemberValue value = attribute.getValue();

            if (value == null ||
                    (position != value &&
                            !PsiTreeUtil.isAncestor(value, position, false))) {
                return;
            }

            PsiJavaFile javaFile =
                    position.getContainingFile() instanceof PsiJavaFile file
                            ? file
                            : null;

            if (javaFile == null) {
                return;
            }

            ModulithModuleResolver resolver =
                    new ModulithModuleResolver(position.getProject());

            Set<String> alreadyDeclared =
                    readExistingDependencies(value);

            String sourcePackage = javaFile.getPackageName();

            ModulithModule source =
                    resolver.resolveModule(javaFile, sourcePackage);

            for (ModulithModule module : resolver.resolveModules(javaFile)) {

                if (source != null &&
                        source.getPackageName().equals(module.getPackageName())) {
                    continue;
                }

                if (alreadyDeclared.contains(module.getName())) {
                    continue;
                }

                result.addElement(
                        LookupElementBuilder
                                .create(module.getName())
                                .withTypeText(module.getPackageName())
                );

                for (var namedInterface : module.getNamedInterfaces()) {

                    String interfaceName = namedInterface.getName();

                    if (interfaceName == null ||
                            interfaceName.isBlank()) {
                        continue;
                    }

                    if ("<<UNNAMED>>".equals(interfaceName)) {
                        continue;
                    }

                    String dependency =
                            module.getName() + " :: " + interfaceName;

                    if (alreadyDeclared.contains(dependency)) {
                        continue;
                    }

                    result.addElement(
                            LookupElementBuilder
                                    .create(dependency)
                                    .withTypeText(namedInterface.getPackageName())
                    );
                }
            }

            result.stopHere();
        }

        private Set<String> readExistingDependencies(
                @NotNull PsiAnnotationMemberValue value) {

            Set<String> dependencies = new HashSet<>();

            if (value instanceof PsiLiteralExpression literal) {
                addStringValue(literal, dependencies);
                return dependencies;
            }

            if (value instanceof PsiArrayInitializerMemberValue array) {
                for (PsiAnnotationMemberValue initializer :
                        array.getInitializers()) {

                    if (initializer instanceof PsiLiteralExpression literal) {
                        addStringValue(literal, dependencies);
                    }
                }
            }

            return dependencies;
        }

        private void addStringValue(
                @NotNull PsiLiteralExpression literal,
                @NotNull Set<String> dependencies) {

            Object rawValue = literal.getValue();

            if (rawValue instanceof String text &&
                    !text.isBlank()) {

                dependencies.add(text.trim());
            }
        }
    }
}