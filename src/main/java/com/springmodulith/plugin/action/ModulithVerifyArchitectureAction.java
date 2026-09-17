package com.springmodulith.plugin.action;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.springmodulith.plugin.toolwindow.ModulithToolWindowController;
import org.jetbrains.annotations.NotNull;

public final class ModulithVerifyArchitectureAction extends AnAction {
    @Override public void actionPerformed(@NotNull AnActionEvent event) {
        Project project = event.getProject();
        if (project == null) return;
        ToolWindow window = ToolWindowManager.getInstance(project).getToolWindow("Spring Modulith");
        if (window != null) {
            window.show(() -> project.getService(ModulithToolWindowController.class).showVerification());
        }
    }

    @Override public void update(@NotNull AnActionEvent event) {
        event.getPresentation().setEnabledAndVisible(event.getProject() != null);
    }
}
