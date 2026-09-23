package com.springmodulith.plugin.inspection;

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
import com.intellij.psi.PsiJavaCodeReferenceElement;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiImportStatement;
import com.springmodulith.plugin.analyzer.ModulithDependencyAnalyzer;
import com.springmodulith.plugin.configuration.ModulithSettings;
import com.springmodulith.plugin.model.ModulithDependencyAnalysis;
import com.springmodulith.plugin.model.ModulithModule;
import com.springmodulith.plugin.model.NamedInterface;
import com.springmodulith.plugin.resolver.ModulithModuleResolver;
import org.jetbrains.annotations.NotNull;

import java.awt.Color;
import java.util.Set;

public final class ModulithBoundaryAnnotator implements Annotator {

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

        holder.newAnnotation(
                        HighlightSeverity.ERROR,
                        message
                )
                .range(nameElement)
                .tooltip(tooltip)
                .enforcedTextAttributes(
                        createRedUnderlineAttributes()
                )
                .create();
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

    @NotNull
    private static String packageName(
            @NotNull com.intellij.psi.PsiClass psiClass) {

        if (psiClass.getContainingFile()
                instanceof PsiJavaFile javaFile) {

            return javaFile.getPackageName();
        }

        return "";
    }

    @NotNull
    private static String escape(@NotNull String value) {
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