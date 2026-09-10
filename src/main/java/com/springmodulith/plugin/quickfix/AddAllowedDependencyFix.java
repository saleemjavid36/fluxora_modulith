package com.springmodulith.plugin.quickfix;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.project.Project;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiAnnotationMemberValue;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiModifierList;
import com.intellij.psi.PsiPackageStatement;
import com.intellij.psi.PsiFileFactory;
import com.intellij.psi.PsiDirectory;
import org.jetbrains.annotations.NotNull;

public final class AddAllowedDependencyFix implements LocalQuickFix {
    private final String dependency;
    private final String sourceModulePackage;

    public AddAllowedDependencyFix(@NotNull String dependency, @NotNull String sourceModulePackage) {
        this.dependency = dependency;
        this.sourceModulePackage = sourceModulePackage;
    }

    @Override public @NotNull String getFamilyName() { return "Spring Modulith"; }
    @Override public @NotNull String getName() { return "Allow dependency '" + dependency + "'"; }

    @Override public void applyFix(@NotNull Project project, @NotNull com.intellij.codeInspection.ProblemDescriptor descriptor) {
        PsiDirectory directory = findPackageDirectory(project, sourceModulePackage);
        if (directory == null) return;
        WriteCommandAction.runWriteCommandAction(project, () -> {
            PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);
            PsiJavaFile packageInfo = directory.findFile("package-info.java") instanceof PsiJavaFile
                    ? (PsiJavaFile) directory.findFile("package-info.java") : null;
            if (packageInfo == null) {
                String text = "@org.springframework.modulith.ApplicationModule(allowedDependencies = {\"" + dependency + "\"})\npackage " + sourceModulePackage + ";\n";
                packageInfo = (PsiJavaFile) PsiFileFactory.getInstance(project).createFileFromText("package-info.java", com.intellij.ide.highlighter.JavaFileType.INSTANCE, text);
                directory.add(packageInfo);
                return;
            }
            PsiPackageStatement statement = packageInfo.getPackageStatement();
            if (statement == null) return;
            PsiModifierList list = statement.getAnnotationList();
            if (list == null) return;
            PsiAnnotation annotation = null;
            for (PsiAnnotation candidate : list.getAnnotations()) {
                if ("org.springframework.modulith.ApplicationModule".equals(candidate.getQualifiedName())) {
                    annotation = candidate;
                    break;
                }
            }
            if (annotation == null) {
                list.add(factory.createAnnotationFromText("@org.springframework.modulith.ApplicationModule(allowedDependencies = {\"" + dependency + "\"})", statement));
                return;
            }
            PsiAnnotationMemberValue value = annotation.findDeclaredAttributeValue("allowedDependencies");
            if (value == null) {
                annotation.setDeclaredAttributeValue("allowedDependencies", factory.createExpressionFromText("{\"" + dependency + "\"}", annotation));
            } else if (value.getText().startsWith("{") && value.getText().endsWith("}")) {
                String body = value.getText().substring(1, value.getText().length() - 1).trim();
                String newText = "{" + (body.isEmpty() ? "" : body + ", ") + "\"" + dependency + "\"}";
                value.replace(factory.createExpressionFromText(newText, value));
            }
        });
    }

    private PsiDirectory findPackageDirectory(Project project, String packageName) {
        return new com.springmodulith.plugin.resolver.ModulithModuleResolver(project).findDirectoryForPackage(packageName);
    }
}
