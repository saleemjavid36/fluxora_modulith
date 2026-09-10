package com.springmodulith.plugin.inspection;

import com.intellij.codeInspection.AbstractBaseJavaLocalInspectionTool;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.psi.JavaElementVisitor;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiMethod;
import com.intellij.openapi.project.Project;
import com.springmodulith.plugin.configuration.ModulithSettings;
import com.springmodulith.plugin.quickfix.SimplifyEventListenerFix;
import org.jetbrains.annotations.NotNull;

public final class ModulithEventListenerInspection extends AbstractBaseJavaLocalInspectionTool {
    private static final String ASYNC = "org.springframework.scheduling.annotation.Async";
    private static final String TRANSACTIONAL = "org.springframework.transaction.annotation.Transactional";
    private static final String EVENT_LISTENER = "org.springframework.transaction.event.TransactionalEventListener";

    @Override public @NotNull PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
        Project project = holder.getProject();
        if (!ModulithSettings.getInstance(project).isInspectEventListeners()) return PsiElementVisitor.EMPTY_VISITOR;
        return new JavaElementVisitor() {
            @Override public void visitMethod(@NotNull PsiMethod method) {
                if (!method.hasAnnotation(ASYNC) || !method.hasAnnotation(EVENT_LISTENER)) return;
                boolean transactional = method.hasAnnotation(TRANSACTIONAL);
                if (!transactional && method.getContainingClass() != null) transactional = method.getContainingClass().hasAnnotation(TRANSACTIONAL);
                if (!transactional) return;
                holder.registerProblem(method.getNameIdentifier(), "This async transactional event listener can be simplified to @ApplicationModuleListener", new SimplifyEventListenerFix());
            }
        };
    }
}
