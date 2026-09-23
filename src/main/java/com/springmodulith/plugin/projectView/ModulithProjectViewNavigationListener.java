package com.springmodulith.plugin.projectView;

import com.intellij.ide.projectView.ProjectView;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.FileEditorManagerListener;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import org.jetbrains.annotations.NotNull;

public final class ModulithProjectViewNavigationListener
        implements FileEditorManagerListener {

    @Override
    public void fileOpened(
            @NotNull FileEditorManager source,
            @NotNull VirtualFile file) {

        if (!"package-info.java".equals(file.getName())) {
            return;
        }

        Project project = source.getProject();

        PsiFile psiFile =
                PsiManager.getInstance(project).findFile(file);

        if (psiFile == null) {
            return;
        }

        PsiDirectory packageDirectory =
                psiFile.getContainingDirectory();

        if (packageDirectory == null) {
            return;
        }

        ProjectView.getInstance(project)
                .select(
                        packageDirectory,
                        packageDirectory.getVirtualFile(),
                        true
                );
    }
}