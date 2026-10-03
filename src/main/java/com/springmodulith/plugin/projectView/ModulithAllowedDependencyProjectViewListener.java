package com.springmodulith.plugin.projectView;

import com.intellij.ide.projectView.ProjectView;
import com.intellij.openapi.editor.event.EditorMouseEvent;
import com.intellij.openapi.editor.event.EditorMouseEventArea;
import com.intellij.openapi.editor.event.EditorMouseListener;
import com.intellij.openapi.project.Project;
import com.intellij.psi.ElementManipulators;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiLiteralExpression;
import com.intellij.psi.util.PsiTreeUtil;
import com.springmodulith.plugin.model.ModulithModule;
import com.springmodulith.plugin.resolver.ModulithModuleResolver;
import org.jetbrains.annotations.NotNull;

import java.awt.event.MouseEvent;

/**
 * Keeps allowedDependencies navigation inside the Project View.
 *
 * Ctrl-clicking an allowed dependency selects its package-info.java in the
 * Project View instead of opening that file in the editor.
 */
public final class ModulithAllowedDependencyProjectViewListener
        implements EditorMouseListener {

    @Override
    public void mousePressed(@NotNull EditorMouseEvent event) {
        if (event.getArea() != EditorMouseEventArea.EDITING_AREA) {
            return;
        }

        MouseEvent mouseEvent = event.getMouseEvent();
        if (!mouseEvent.isControlDown()
                || (mouseEvent.getButton() != MouseEvent.BUTTON1
                && mouseEvent.getButton() != MouseEvent.BUTTON3)) {
            return;
        }

        Project project = event.getEditor().getProject();
        if (project == null || project.isDisposed()) {
            return;
        }
        PsiFile psiFile = findPsiFile(event, project);
        if (psiFile == null) {
            return;
        }

        PsiLiteralExpression literal =
                PsiTreeUtil.getParentOfType(
                        psiFile.findElementAt(event.getOffset()),
                        PsiLiteralExpression.class
                );

        if (literal == null || !isAllowedDependencyLiteral(literal)) {
            return;
        }

        PsiFile targetFile = resolvePackageInfoFile(project, literal);
        if (targetFile == null
                || targetFile.getVirtualFile() == null
                || !targetFile.getVirtualFile().isValid()) {
            return;
        }

        ProjectView.getInstance(project).select(
                targetFile,
                targetFile.getVirtualFile(),
                true
        );

        event.consume();
    }

    private static PsiFile findPsiFile(
            @NotNull EditorMouseEvent event,
            @NotNull Project project) {

        return com.intellij.psi.PsiDocumentManager.getInstance(project)
                .getPsiFile(event.getEditor().getDocument());
    }

    private static boolean isAllowedDependencyLiteral(
            @NotNull PsiLiteralExpression literal) {

        PsiAnnotation annotation =
                PsiTreeUtil.getParentOfType(literal, PsiAnnotation.class);

        if (annotation == null
                || !"org.springframework.modulith.ApplicationModule"
                .equals(annotation.getQualifiedName())) {
            return false;
        }

        PsiElement allowedDependencies =
                annotation.findDeclaredAttributeValue("allowedDependencies");

        return allowedDependencies != null
                && PsiTreeUtil.isAncestor(allowedDependencies, literal, false);
    }

    private static PsiFile resolvePackageInfoFile(
            @NotNull Project project,
            @NotNull PsiLiteralExpression literal) {

        String value = ElementManipulators.getValueText(literal);
        ModulithModule.DependencyRule rule =
                ModulithModule.DependencyRule.parse(value);

        if (rule == null) {
            return null;
        }

        ModulithModuleResolver resolver =
                new ModulithModuleResolver(project);

        for (ModulithModule module :
                resolver.resolveModules(literal.getContainingFile())) {

            if (!module.matchesModuleId(rule.moduleId())) {
                continue;
            }

            String targetPackage = module.getPackageName();

            if (rule.interfaceId() != null
                    && !"*".equals(rule.interfaceId())) {

                com.springmodulith.plugin.model.NamedInterface namedInterface =
                        module.findNamedInterface(rule.interfaceId());

                if (namedInterface == null) {
                    return null;
                }

                targetPackage = namedInterface.getPackageName();
            }

            PsiDirectory directory =
                    resolver.findDirectoryForPackage(targetPackage);

            if (directory == null) {
                return null;
            }

            for (PsiFile file : directory.getFiles()) {
                if (file instanceof PsiJavaFile
                        && "package-info.java".equals(file.getName())) {
                    return file;
                }
            }

            return null;
        }

        return null;
    }
}
