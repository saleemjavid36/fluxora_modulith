package com.springmodulith.plugin.quickfix;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.project.Project;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.PsiModifierList;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;

public final class MarkClassNamedInterfaceFix
        implements LocalQuickFix {

    private static final String NAMED_INTERFACE =
            "org.springframework.modulith.NamedInterface";

    private final String name;

    public MarkClassNamedInterfaceFix(
            @NotNull String name) {

        this.name = name;
    }

    @Override
    public @NotNull String getFamilyName() {
        return "Spring Modulith";
    }

    @Override
    public @NotNull String getName() {
        return "Mark class as named interface '" + name + "'";
    }

    @Override
    public void applyFix(
            @NotNull Project project,
            @NotNull com.intellij.codeInspection.ProblemDescriptor descriptor) {

        PsiClass psiClass =
                PsiTreeUtil.getParentOfType(
                        descriptor.getPsiElement(),
                        PsiClass.class
                );

        if (psiClass == null) {
            return;
        }

        PsiModifierList modifierList =
                psiClass.getModifierList();

        if (modifierList == null) {
            return;
        }

        if (modifierList.findAnnotation(
                NAMED_INTERFACE) != null) {
            return;
        }

        WriteCommandAction.runWriteCommandAction(
                project,
                () -> {

                    PsiElementFactory factory =
                            JavaPsiFacade.getElementFactory(project);

                    PsiAnnotation annotation =
                            factory.createAnnotationFromText(
                                    "@org.springframework.modulith.NamedInterface",
                                    psiClass
                            );

                    modifierList.addBefore(
                            annotation,
                            modifierList.getFirstChild()
                    );
                }
        );
    }
}