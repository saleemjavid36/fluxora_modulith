package com.springmodulith.plugin.action;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import org.jetbrains.annotations.NotNull;

public final class ModulithToggleToolWindowAction extends AnAction {

    private static final String TOOL_WINDOW_ID = "Fluxora Modulith";

    @Override
    public void actionPerformed(@NotNull AnActionEvent event) {

        Project project = event.getProject();

        if (project == null) {
            return;
        }

        ToolWindow toolWindow =
                ToolWindowManager
                        .getInstance(project)
                        .getToolWindow(TOOL_WINDOW_ID);

        if (toolWindow == null) {
            return;
        }

        if (toolWindow.isVisible()) {
            toolWindow.hide(null);
        } else {
            toolWindow.show(null);
        }
    }

    @Override
    public void update(@NotNull AnActionEvent event) {

        event.getPresentation().setEnabled(
                event.getProject() != null
        );
    }
}