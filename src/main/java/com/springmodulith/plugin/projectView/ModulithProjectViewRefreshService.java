package com.springmodulith.plugin.projectView;

import com.intellij.ide.projectView.ProjectView;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.Disposable;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiTreeChangeAdapter;
import com.intellij.psi.PsiTreeChangeEvent;
import org.jetbrains.annotations.NotNull;

@Service(Service.Level.PROJECT)
public final class ModulithProjectViewRefreshService implements Disposable {
    private final Project project;
    private final PsiManager psiManager;
    private final PsiTreeChangeAdapter listener;

    public ModulithProjectViewRefreshService(@NotNull Project project) {
        this.project = project;
        this.psiManager = PsiManager.getInstance(project);
        this.listener = new PsiTreeChangeAdapter() {
            @Override
            public void childrenChanged(@NotNull PsiTreeChangeEvent event) {
                refreshIfPackageInfoChanged(event.getFile());
            }

            @Override
            public void childAdded(@NotNull PsiTreeChangeEvent event) {
                refreshIfPackageInfoChanged(event.getFile());
            }

            @Override
            public void childRemoved(@NotNull PsiTreeChangeEvent event) {
                refreshIfPackageInfoChanged(event.getFile());
            }

            @Override
            public void childReplaced(@NotNull PsiTreeChangeEvent event) {
                refreshIfPackageInfoChanged(event.getFile());
            }

            @Override
            public void propertyChanged(@NotNull PsiTreeChangeEvent event) {
                refreshIfPackageInfoChanged(event.getFile());
            }
        };

        psiManager.addPsiTreeChangeListener(listener, this);
    }

    private void refreshIfPackageInfoChanged(PsiFile file) {
        if (file == null || !"package-info.java".equals(file.getName())) return;

        ApplicationManager.getApplication().invokeLater(() -> {
            if (!project.isDisposed()) {
                ProjectView.getInstance(project).refresh();
            }
        });
    }

    @Override
    public void dispose() {
        psiManager.removePsiTreeChangeListener(listener);
    }
}
