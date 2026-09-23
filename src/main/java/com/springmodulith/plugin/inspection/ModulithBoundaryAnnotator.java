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
import com.intellij.psi.PsiImportStatement;
import com.intellij.psi.PsiJavaCodeReferenceElement;
import com.intellij.ui.JBColor;
import com.springmodulith.plugin.analyzer.ModulithDependencyAnalyzer;
import com.springmodulith.plugin.configuration.ModulithSettings;
import com.springmodulith.plugin.model.ModulithDependencyAnalysis;
import com.springmodulith.plugin.resolver.ModulithModuleResolver;
import org.jetbrains.annotations.NotNull;

import java.awt.Color;

public final class ModulithBoundaryAnnotator implements Annotator {

    @Override
    public void annotate(
            @NotNull PsiElement element,
            @NotNull AnnotationHolder holder) {

        if (!(element instanceof PsiJavaCodeReferenceElement reference)) {
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
                        && analysis.isForbidden();

        if (!apiViolation && !dependencyViolation) {
            return;
        }

        PsiElement nameElement =
                reference.getReferenceNameElement();

        if (nameElement == null) {
            return;
        }

        String message =
                analyzer.getMessage(analysis);

        if (message == null) {
            message = "Modulith boundary violation";
        }

        holder.newAnnotation(
                        HighlightSeverity.ERROR,
                        message
                )
                .range(nameElement)
                .enforcedTextAttributes(
                        createRedUnderlineAttributes()
                )
                .create();
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
                        ? JBColor.RED
                        : errorAttributes.getEffectColor();

        if (effectColor == null) {
            effectColor = JBColor.RED;
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