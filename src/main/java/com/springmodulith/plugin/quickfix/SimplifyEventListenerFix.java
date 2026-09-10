package com.springmodulith.plugin.quickfix;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.project.Project;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifierList;
import org.jetbrains.annotations.NotNull;

public final class SimplifyEventListenerFix implements LocalQuickFix {
    private static final String ASYNC = "org.springframework.scheduling.annotation.Async";
    private static final String TRANSACTIONAL = "org.springframework.transaction.annotation.Transactional";
    private static final String EVENT_LISTENER = "org.springframework.transaction.event.TransactionalEventListener";

    @Override public @NotNull String getFamilyName() { return "Spring Modulith"; }
    @Override public @NotNull String getName() { return "Replace with @ApplicationModuleListener"; }

    @Override public void applyFix(@NotNull Project project, @NotNull com.intellij.codeInspection.ProblemDescriptor descriptor) {
        PsiMethod method = PsiTreeUtil.getParentOfType(descriptor.getPsiElement(), PsiMethod.class);
        if (method == null) return;
        WriteCommandAction.runWriteCommandAction(project, () -> {
            PsiModifierList list = method.getModifierList();
            remove(list, ASYNC);
            remove(list, TRANSACTIONAL);
            remove(list, EVENT_LISTENER);
            PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);
            list.addAnnotation("org.springframework.modulith.ApplicationModuleListener");
        });
    }

    private void remove(PsiModifierList list, String fqn) {
        for (PsiAnnotation annotation : list.getAnnotations()) if (fqn.equals(annotation.getQualifiedName())) annotation.delete();
    }
}
