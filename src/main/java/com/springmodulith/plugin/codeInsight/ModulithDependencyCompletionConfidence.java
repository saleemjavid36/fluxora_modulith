package com.springmodulith.plugin.codeInsight;

import com.intellij.codeInsight.completion.CompletionConfidence;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiLiteralExpression;
import com.intellij.util.ThreeState;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;

public final class ModulithDependencyCompletionConfidence
        extends CompletionConfidence {

    @Override
    public @NotNull ThreeState shouldSkipAutopopup(
            @NotNull PsiElement contextElement,
            @NotNull PsiFile psiFile,
            int offset) {

        PsiLiteralExpression literal =
                PsiTreeUtil.getParentOfType(
                        contextElement,
                        PsiLiteralExpression.class
                );

        if (literal == null) {
            return ThreeState.UNSURE;
        }

        PsiAnnotation annotation =
                PsiTreeUtil.getParentOfType(
                        literal,
                        PsiAnnotation.class
                );

        if (annotation == null) {
            return ThreeState.UNSURE;
        }

        if (!"org.springframework.modulith.ApplicationModule"
                .equals(annotation.getQualifiedName())) {
            return ThreeState.UNSURE;
        }

        if (annotation.findDeclaredAttributeValue(
                "allowedDependencies"
        ) == null) {
            return ThreeState.UNSURE;
        }

        /*
         * IMPORTANT:
         *
         * ThreeState.NO means:
         * "Do NOT skip the completion auto-popup."
         *
         * Therefore IntelliJ will allow the normal Java
         * completion popup while the user is typing here.
         */
        return ThreeState.NO;
    }
}