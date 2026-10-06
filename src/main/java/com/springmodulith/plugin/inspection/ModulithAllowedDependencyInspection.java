package com.springmodulith.plugin.inspection;

import com.intellij.codeInspection.AbstractBaseJavaLocalInspectionTool;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.psi.JavaElementVisitor;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiAnnotationMemberValue;
import com.intellij.psi.PsiArrayInitializerMemberValue;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiLiteralExpression;
import com.intellij.psi.PsiNameValuePair;
import com.intellij.psi.PsiPackageStatement;
import com.intellij.psi.PsiModifierList;
import com.intellij.openapi.project.Project;
import com.springmodulith.plugin.configuration.ModulithSettings;
import com.springmodulith.plugin.model.ModulithModule;
import com.springmodulith.plugin.resolver.ModulithModuleResolver;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;


public final class ModulithAllowedDependencyInspection extends AbstractBaseJavaLocalInspectionTool {
    private static final String APPLICATION_MODULE = ModulithModuleResolver.APPLICATION_MODULE;
    private static final String ALLOWED = "allowedDependencies";

    @Override
    public @NotNull PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
        Project project = holder.getProject();
        if (!ModulithSettings.getInstance(project).isInspectAllowedDependencies()) return PsiElementVisitor.EMPTY_VISITOR;
        ModulithModuleResolver resolver = new ModulithModuleResolver(project);
        return new JavaElementVisitor() {
            @Override public void visitAnnotation(@NotNull PsiAnnotation annotation) {
                if (!APPLICATION_MODULE.equals(annotation.getQualifiedName())) return;
                PsiAnnotationMemberValue value = annotation.findDeclaredAttributeValue(ALLOWED);
                if (value == null) return;
                for (PsiLiteralExpression literal : literals(value)) {
                    Object raw = literal.getValue();
                    if (!(raw instanceof String text)) continue;
                    validate(text, literal, holder, resolver);
                }
            }
        };
    }

    private void validate(
            String text,
            PsiElement element,
            ProblemsHolder holder,
            ModulithModuleResolver resolver) {

        String value = text.trim();

        /*
         * Empty allowedDependencies means that there is no
         * explicit dependency restriction.
         *
         * Examples:
         *
         * allowedDependencies = ""
         * allowedDependencies = { }
         */
        if (value.isEmpty()) {
            return;
        }

        ModulithModule.DependencyRule rule =
                ModulithModule.DependencyRule.parse(value);

        if (rule == null) {
            holder.registerProblem(
                    element,
                    "Malformed Modulith dependency. " +
                            "Use 'module' or 'module :: interface'"
            );
            return;
        }

        PsiJavaFile javaFile =
                element.getContainingFile() instanceof PsiJavaFile
                        ? (PsiJavaFile) element.getContainingFile()
                        : null;

        if (javaFile == null) {
            return;
        }

        List<ModulithModule> modules =
                resolver.resolveModules(javaFile);

        ModulithModule target =
                modules.stream()
                        .filter(m -> m.matchesModuleId(rule.moduleId()))
                        .findFirst()
                        .orElse(null);

        if (target == null) {
            holder.registerProblem(
                    element,
                    "Modulith module not found: "
                            + rule.moduleId()
            );
            return;
        }

        if (rule.interfaceId() != null
                && !"*".equals(rule.interfaceId())
                && !target.hasNamedInterface(
                rule.interfaceId())) {

            holder.registerProblem(
                    element,
                    "Named interface not found in module '"
                            + target.getName()
                            + "': "
                            + rule.interfaceId()
            );
        }
    }

    private List<PsiLiteralExpression> literals(PsiAnnotationMemberValue value) {
        List<PsiLiteralExpression> result = new ArrayList<>();
        if (value instanceof PsiLiteralExpression literal) result.add(literal);
        else if (value instanceof PsiArrayInitializerMemberValue array) {
            for (PsiAnnotationMemberValue item : array.getInitializers()) if (item instanceof PsiLiteralExpression literal) result.add(literal);
        }
        return result;
    }
}
