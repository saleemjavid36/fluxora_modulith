package com.springmodulith.plugin.toolwindow;

import com.intellij.openapi.components.Service;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import javax.swing.JTabbedPane;

@Service(Service.Level.PROJECT)
public final class ModulithToolWindowController {
    private final Project project;
    private JTabbedPane tabs;
    private ModulithVerificationPanel verificationPanel;

    public ModulithToolWindowController(@NotNull Project project) {
        this.project = project;
    }

    public void register(@NotNull JTabbedPane tabs, @NotNull ModulithVerificationPanel verificationPanel) {
        this.tabs = tabs;
        this.verificationPanel = verificationPanel;
    }

    public void showVerification() {
        if (tabs == null || verificationPanel == null) return;
        tabs.setSelectedComponent(verificationPanel);
        verificationPanel.verify();
    }
}
