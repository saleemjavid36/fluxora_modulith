package com.springmodulith.plugin.toolwindow;

import com.intellij.openapi.components.Service;
import com.intellij.openapi.project.Project;
import com.intellij.psi.util.PsiModificationTracker;
import org.jetbrains.annotations.NotNull;

import javax.swing.JTabbedPane;
import javax.swing.Timer;

@Service(Service.Level.PROJECT)
public final class ModulithToolWindowController {
    private final Project project;
    private final Timer refreshTimer;
    private JTabbedPane tabs;
    private ModulithVerificationPanel verificationPanel;
    private Runnable refreshAction;

    public ModulithToolWindowController(@NotNull Project project) {
        this.project = project;

        refreshTimer = new Timer(350, event -> {
            if (project.isDisposed() || refreshAction == null) {
                return;
            }
            refreshAction.run();
        });
        refreshTimer.setRepeats(false);

        project.getMessageBus().connect().subscribe(
                PsiModificationTracker.TOPIC,
                new PsiModificationTracker.Listener() {
                    @Override
                    public void modificationCountChanged() {
                        if (!project.isDisposed()) {
                            refreshTimer.restart();
                        }
                    }
                }
        );
    }

    public void register(
            @NotNull JTabbedPane tabs,
            @NotNull ModulithVerificationPanel verificationPanel,
            @NotNull Runnable refreshAction) {
        this.tabs = tabs;
        this.verificationPanel = verificationPanel;
        this.refreshAction = refreshAction;
    }

    public void showVerification() {
        if (tabs == null || verificationPanel == null) {
            return;
        }

        tabs.setSelectedComponent(verificationPanel);
        verificationPanel.verify();
    }
}
