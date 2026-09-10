package com.springmodulith.plugin.quickfix;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.project.Project;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;

public final class MarkClassNamedInterfaceFix implements LocalQuickFix {
    private final String name;
    public MarkClassNamedInterfaceFix(@NotNull String name) { this.name = name; }
    @Override public @NotNull String getFamilyName() { return "Spring Modulith"; }
    @Override public @NotNull String getName() { return "Mark class as named interface '" + name + "'"; }
    @Override public void applyFix(@NotNull Project project, @NotNull com.intellij.codeInspection.ProblemDescriptor descriptor) {
        PsiClass psiClass = PsiTreeUtil.getParentOfType(descriptor.getPsiElement(), PsiClass.class);
        if (psiClass == null) return;
        WriteCommandAction.runWriteCommandAction(project, () -> {
            PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);
            if (name.isEmpty()) psiClass.getModifierList().add(factory.createAnnotationFromText("@org.springframework.modulith.NamedInterface", psiClass));
            else psiClass.getModifierList().add(factory.createAnnotationFromText("@org.springframework.modulith.NamedInterface(\"" + name + "\")", psiClass));
        });
    }
}
