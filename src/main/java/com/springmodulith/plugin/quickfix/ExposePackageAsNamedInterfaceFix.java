package com.springmodulith.plugin.quickfix;

import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.project.Project;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiFileFactory;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiModifierList;
import com.intellij.psi.PsiPackageStatement;
import com.intellij.psi.PsiElementFactory;
import com.springmodulith.plugin.resolver.ModulithModuleResolver;
import org.jetbrains.annotations.NotNull;

public final class ExposePackageAsNamedInterfaceFix implements LocalQuickFix {
    private static final String NAMED_INTERFACE = ModulithModuleResolver.NAMED_INTERFACE;
    private final String packageName;
    private final String interfaceName;

    public ExposePackageAsNamedInterfaceFix(@NotNull String packageName) {
        this.packageName = packageName;
        int index = packageName.lastIndexOf('.');
        this.interfaceName = index >= 0 ? packageName.substring(index + 1) : packageName;
    }

    @Override
    public @NotNull String getFamilyName() {
        return "Spring Modulith";
    }

    @Override
    public @NotNull String getName() {
        return "Expose package as named interface '" + interfaceName + "'";
    }

    @Override
    public void applyFix(@NotNull Project project,
                         @NotNull com.intellij.codeInspection.ProblemDescriptor descriptor) {
        ModulithModuleResolver resolver = new ModulithModuleResolver(project);
        PsiDirectory directory = resolver.findDirectoryForPackage(packageName);
        if (directory == null) return;

        WriteCommandAction.runWriteCommandAction(project, () -> {
            PsiJavaFile packageInfo = findPackageInfo(directory);
            PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);

            if (packageInfo == null) {
                String text = "@org.springframework.modulith.NamedInterface(\"" + interfaceName + "\")\n"
                        + "package " + packageName + ";\n";
                PsiJavaFile created = (PsiJavaFile) PsiFileFactory.getInstance(project)
                        .createFileFromText("package-info.java", JavaFileType.INSTANCE, text);
                directory.add(created);
                return;
            }

            PsiPackageStatement packageStatement = packageInfo.getPackageStatement();
            if (packageStatement == null) return;

            PsiModifierList annotationList = packageStatement.getAnnotationList();
            if (annotationList == null) return;

            for (PsiAnnotation annotation : annotationList.getAnnotations()) {
                if (NAMED_INTERFACE.equals(annotation.getQualifiedName())) return;
            }

            annotationList.add(factory.createAnnotationFromText(
                    "@org.springframework.modulith.NamedInterface(\"" + interfaceName + "\")",
                    packageStatement));
        });
    }

    private PsiJavaFile findPackageInfo(@NotNull PsiDirectory directory) {
        return directory.findFile("package-info.java") instanceof PsiJavaFile javaFile ? javaFile : null;
    }
}
