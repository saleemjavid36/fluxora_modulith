package com.springmodulith.plugin.inspection;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.Annotator;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.editor.colors.CodeInsightColors;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.markup.EffectType;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiImportStatement;
import com.intellij.psi.PsiJavaCodeReferenceElement;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiClass;
import com.springmodulith.plugin.analyzer.ModulithDependencyAnalyzer;
import com.springmodulith.plugin.configuration.ModulithSettings;
import com.springmodulith.plugin.model.ModulithDependencyAnalysis;
import com.springmodulith.plugin.model.ModulithModule;
import com.springmodulith.plugin.model.NamedInterface;
import com.springmodulith.plugin.quickfix.AddAllowedDependencyFix;
import com.springmodulith.plugin.quickfix.ExposePackageAsNamedInterfaceFix;
import com.springmodulith.plugin.resolver.ModulithModuleResolver;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.Color;
import java.util.Set;

public final class ModulithBoundaryAnnotator implements Annotator {

    @Nullable
    private static IntentionAction createApiPopupFix(
            @NotNull ModulithDependencyAnalysis analysis) {

        PsiClass targetClass =
                analysis.targetClass();

        String qualifiedType =
                targetClass.getQualifiedName();

        if (qualifiedType == null
                || qualifiedType.isBlank()) {
            return null;
        }

        String targetPackage =
                packageName(targetClass);

        /*
         * If the type/package is already exposed, there is no
         * API exposure quick fix to offer.
         */
        if (analysis.target().exposes(
                qualifiedType,
                targetPackage
        )) {
            return null;
        }

        return new ExposePackageAsNamedInterfaceFix(
                targetPackage
        );
    }
    @Override
    public void annotate(
            @NotNull PsiElement element,
            @NotNull AnnotationHolder holder) {

        if (!(element instanceof PsiJavaCodeReferenceElement reference)) {
            return;
        }

        /*
         * Import references are handled by ModulithDependencyInspection.
         * Avoid creating duplicate annotations here.
         */
        if (reference.getParent() instanceof PsiImportStatement) {
            return;
        }

        Project project = element.getProject();

        ModulithSettings settings =
                ModulithSettings.getInstance(project);

        if (!settings.isInspectApiUsage()
                && !settings.isInspectAllowedDependencies()) {
            return;
        }

        ModulithDependencyAnalyzer analyzer =
                new ModulithDependencyAnalyzer(
                        new ModulithModuleResolver(project),
                        project
                );

        ModulithDependencyAnalysis analysis =
                analyzer.analyze(reference);

        if (analysis == null || !analysis.isForbidden()) {
            return;
        }

        boolean apiViolation =
                settings.isInspectApiUsage()
                        && analysis.apiViolation();

        boolean dependencyViolation =
                settings.isInspectAllowedDependencies()
                        && !isAllowedDependency(analysis);

        if (!apiViolation && !dependencyViolation) {
            return;
        }

        PsiElement nameElement =
                reference.getReferenceNameElement();

        if (nameElement == null) {
            return;
        }

        String message =
                createShortMessage(
                        analysis,
                        apiViolation,
                        dependencyViolation
                );

        String tooltip =
                createViolationTooltip(
                        analysis,
                        apiViolation,
                        dependencyViolation
                );

        var annotation = holder.newAnnotation(
                        HighlightSeverity.ERROR,
                        message
                )
                .range(nameElement)
                .tooltip(tooltip)
                .enforcedTextAttributes(
                        createRedUnderlineAttributes()
                );

        /*
         * Add the most appropriate quick fix directly to the
         * IntelliJ annotation popup.
         *
         * API exposure has priority over dependency configuration.
         *
         * Example:
         *
         * StudentDto is inside:
         *
         *     student.dto
         *
         * but student.dto is not exposed.
         *
         * The correct fix is:
         *
         *     Expose package '...student.dto'
         *     as named interface 'dto'
         *
         * NOT:
         *
         *     Add 'student' as an allowed dependency
         *
         * because 'student' only permits the module root API.
         */
        if (apiViolation) {

            IntentionAction apiFix =
                    createApiPopupFix(analysis);

            if (apiFix != null) {
                annotation.withFix(apiFix);
            }

        } else if (dependencyViolation) {

            IntentionAction dependencyFix =
                    createAllowedDependencyPopupFix(analysis);

            if (dependencyFix != null) {
                annotation.withFix(dependencyFix);
            }
        }

        annotation.create();
    }

    private static boolean isAllowedDependency(
            @NotNull ModulithDependencyAnalysis analysis) {

        return !analysis.source().isAllowedDependenciesConfigured()
                || analysis.source().allowsType(
                analysis.targetClass().getQualifiedName(),
                packageName(analysis.targetClass()),
                analysis.target()
        );
    }

    @NotNull
    private static String createShortMessage(
            @NotNull ModulithDependencyAnalysis analysis,
            boolean apiViolation,
            boolean dependencyViolation) {

        if (apiViolation && dependencyViolation) {
            return "Modulith API and dependency violation: "
                    + analysis.source().getName()
                    + " → "
                    + analysis.target().getName();
        }

        if (apiViolation) {
            return "Modulith API violation: "
                    + analysis.source().getName()
                    + " accesses internal type from "
                    + analysis.target().getName();
        }

        return "Modulith dependency is not allowed: "
                + analysis.source().getName()
                + " → "
                + analysis.target().getName();
    }

    @NotNull
    private static String createViolationTooltip(
            @NotNull ModulithDependencyAnalysis analysis,
            boolean apiViolation,
            boolean dependencyViolation) {

        ModulithModule source = analysis.source();
        ModulithModule target = analysis.target();

        String qualifiedType =
                analysis.targetClass().getQualifiedName();

        String targetPackage =
                packageName(analysis.targetClass());

        StringBuilder html =
                new StringBuilder();

        html.append("<html>");

        /*
         * Header
         */
        if (apiViolation && dependencyViolation) {
            html.append("<b>Modulith API and dependency violation</b>");
        } else if (apiViolation) {
            html.append("<b>Modulith API violation</b>");
        } else {
            html.append("<b>Modulith dependency violation</b>");
        }

        html.append("<br><br>");

        /*
         * Dependency path
         */
        html.append("<b>Dependency</b><br>");
        html.append("<code>")
                .append(escape(source.getName()))
                .append(" → ")
                .append(escape(target.getName()))
                .append("</code>");

        html.append("<br><br>");

        /*
         * Source module
         */
        html.append("<b>Source module</b><br>");
        html.append(escape(source.getName()));

        html.append("<br><br>");

        /*
         * Target module
         */
        html.append("<b>Target module</b><br>");
        html.append(escape(target.getName()));

        /*
         * Accessed type
         */
        if (qualifiedType != null
                && !qualifiedType.isBlank()) {

            html.append("<br><br>");
            html.append("<b>Accessed type</b><br>");
            html.append("<code>")
                    .append(escape(qualifiedType))
                    .append("</code>");
        }

        /*
         * Target package
         */
        if (!targetPackage.isBlank()) {

            html.append("<br><br>");
            html.append("<b>Target package</b><br>");
            html.append("<code>")
                    .append(escape(targetPackage))
                    .append("</code>");
        }

        /*
         * API violation explanation
         */
        if (apiViolation) {

            html.append("<br><br>");
            html.append("<b>Why this is a violation</b><br>");

            html.append(
                    "The accessed type is internal to the "
                            + "target module and is not exposed "
                            + "through its Modulith API."
            );

            NamedInterface exposedInterface =
                    qualifiedType == null
                            ? null
                            : target.findNamedInterfaceForType(
                            qualifiedType,
                            targetPackage
                    );

            if (exposedInterface != null) {

                html.append("<br><br>");
                html.append("<b>Target named interface</b><br>");
                html.append("<code>")
                        .append(
                                escape(
                                        exposedInterface.getName()
                                )
                        )
                        .append("</code>");
            }
        }

        /*
         * Dependency rule explanation
         */
        if (dependencyViolation) {

            html.append("<br><br>");
            html.append("<b>Why this is a violation</b><br>");

            html.append(
                    "The source module does not allow this "
                            + "dependency through its configured "
                            + "<code>allowedDependencies</code>."
            );

            Set<String> configured =
                    source.getAllowedDependencies();

            if (!configured.isEmpty()) {

                html.append("<br><br>");
                html.append("<b>Configured dependencies</b><br>");

                for (String dependency : configured) {

                    html.append("• ")
                            .append(escape(dependency))
                            .append("<br>");
                }

            } else {

                html.append("<br><br>");
                html.append("<b>Configured dependencies</b><br>");
                html.append("None");
            }
        }

        /*
         * Helpful rule suggestion
         */
        NamedInterface namedInterface =
                qualifiedType == null
                        ? null
                        : target.findNamedInterfaceForType(
                        qualifiedType,
                        targetPackage
                );

        if (namedInterface != null
                && dependencyViolation) {

            html.append("<br><br>");
            html.append("<b>Possible dependency rule</b><br>");

            html.append("<code>")
                    .append(escape(target.getName()))
                    .append(" :: ")
                    .append(
                            escape(
                                    namedInterface.getName()
                            )
                    )
                    .append("</code>");
        }

        html.append("</html>");

        return html.toString();
    }

    /**
     * Creates the quick fix displayed directly inside the
     * IntelliJ annotation/violation popup.
     *
     * This quick fix is only offered when:
     *
     * 1. The target type belongs to a recognized named interface.
     * 2. That named-interface dependency is not already configured.
     *
     * If the target type is not exposed through a named interface,
     * the API quick fix is responsible for exposing it.
     */
    @Nullable
    private static IntentionAction createAllowedDependencyPopupFix(
            @NotNull ModulithDependencyAnalysis analysis) {

        ModulithModule source =
                analysis.source();

        ModulithModule target =
                analysis.target();

        PsiClass targetClass =
                analysis.targetClass();

        String qualifiedType =
                targetClass.getQualifiedName();

        if (qualifiedType == null
                || qualifiedType.isBlank()) {
            return null;
        }

        String targetPackage =
                packageName(targetClass);

        /*
         * Find the named interface containing this exact type/package.
         */
        NamedInterface namedInterface =
                target.findNamedInterfaceForType(
                        qualifiedType,
                        targetPackage
                );

        /*
         * IMPORTANT:
         *
         * If the type is not exposed through a named interface,
         * do NOT generate:
         *
         *     student
         *
         * The API quick fix should expose the package first.
         */
        if (namedInterface == null) {
            return null;
        }

        String dependency =
                target.getName()
                        + " :: "
                        + namedInterface.getName();

        /*
         * IMPORTANT:
         *
         * Do not offer the quick fix when the dependency is
         * already present in allowedDependencies.
         */
        if (hasAllowedDependency(
                source,
                dependency
        )) {
            return null;
        }

        String fixName =
                "Add '"
                        + dependency
                        + "' as an allowed dependency of the '"
                        + source.getName()
                        + "' module";

        return new AddAllowedDependencyFix(
                dependency,
                source.getPackageName(),
                fixName
        );
    }
    private static boolean hasAllowedDependency(
            @NotNull ModulithModule source,
            @NotNull String dependency) {

        ModulithModule.DependencyRule expected =
                ModulithModule.DependencyRule.parse(
                        dependency
                );

        if (expected == null) {
            return false;
        }

        for (String configuredDependency :
                source.getAllowedDependencies()) {

            ModulithModule.DependencyRule configured =
                    ModulithModule.DependencyRule.parse(
                            configuredDependency
                    );

            if (configured == null) {
                continue;
            }

            /*
             * Module must match.
             */
            if (!expected.moduleId()
                    .equals(configured.moduleId())) {
                continue;
            }

            /*
             * "student" and "student :: repository"
             * are different rules.
             */
            if (expected.interfaceId() == null) {

                if (configured.interfaceId() == null) {
                    return true;
                }

                continue;
            }

            /*
             * Exact named-interface dependency.
             */
            if (expected.interfaceId()
                    .equals(configured.interfaceId())) {

                return true;
            }
        }

        return false;
    }

    @NotNull
    private static String packageName(
            @NotNull PsiClass psiClass) {

        if (psiClass.getContainingFile()
                instanceof PsiJavaFile javaFile) {

            return javaFile.getPackageName();
        }

        return "";
    }

    @NotNull
    private static String escape(
            @NotNull String value) {

        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    @NotNull
    private static TextAttributes createRedUnderlineAttributes() {

        EditorColorsScheme scheme =
                EditorColorsManager
                        .getInstance()
                        .getGlobalScheme();

        TextAttributes errorAttributes =
                scheme.getAttributes(
                        CodeInsightColors.ERRORS_ATTRIBUTES
                );

        Color effectColor =
                errorAttributes == null
                        ? com.intellij.ui.JBColor.RED
                        : errorAttributes.getEffectColor();

        if (effectColor == null) {
            effectColor = com.intellij.ui.JBColor.RED;
        }

        TextAttributes attributes =
                new TextAttributes();

        attributes.setEffectType(
                EffectType.WAVE_UNDERSCORE
        );

        attributes.setEffectColor(effectColor);

        return attributes;
    }
}