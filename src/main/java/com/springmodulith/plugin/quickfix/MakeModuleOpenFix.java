package com.springmodulith.plugin.quickfix;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.project.Project;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiModifierList;
import com.intellij.psi.PsiPackageStatement;
import com.intellij.psi.PsiDirectory;
import org.jetbrains.annotations.NotNull;

public final class MakeModuleOpenFix implements LocalQuickFix {
    private final String targetPackage;
    public MakeModuleOpenFix(@NotNull String targetPackage) { this.targetPackage = targetPackage; }
    @Override public @NotNull String getFamilyName() { return "Spring Modulith"; }
    @Override public @NotNull String getName() { return "Make module '" + targetPackage + "' open"; }
    @Override public void applyFix(@NotNull Project project, @NotNull com.intellij.codeInspection.ProblemDescriptor descriptor) {
        PsiDirectory directory = new com.springmodulith.plugin.resolver.ModulithModuleResolver(project).findDirectoryForPackage(targetPackage);
        if (directory == null) return;
        WriteCommandAction.runWriteCommandAction(project, () -> {
            PsiJavaFile packageInfo = directory.findFile("package-info.java") instanceof PsiJavaFile ? (PsiJavaFile) directory.findFile("package-info.java") : null;
            if (packageInfo == null || packageInfo.getPackageStatement() == null) return;
            PsiModifierList list = packageInfo.getPackageStatement().getAnnotationList();
            if (list == null) return;
            PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);
            for (PsiAnnotation annotation : list.getAnnotations()) {
                if ("org.springframework.modulith.ApplicationModule".equals(annotation.getQualifiedName())) {
                    if (annotation.findDeclaredAttributeValue("open") == null) annotation.setDeclaredAttributeValue("open", factory.createExpressionFromText("true", annotation));
                    return;
                }
            }
        });
    }
}
