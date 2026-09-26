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
    private Runnable autoRefreshAction;
    private boolean autoRefreshEnabled;

    public ModulithToolWindowController(@NotNull Project project) {
        this.project = project;

        refreshTimer = new Timer(350, event -> {
            if (project.isDisposed() || !autoRefreshEnabled || autoRefreshAction == null) {
                return;
            }
            autoRefreshAction.run();
        });
        refreshTimer.setRepeats(false);

        project.getMessageBus().connect().subscribe(
                PsiModificationTracker.TOPIC,
                new PsiModificationTracker.Listener() {
                    @Override
                    public void modificationCountChanged() {
                        if (!project.isDisposed() && autoRefreshEnabled) {
                            refreshTimer.restart();
                        }
                    }
                }
        );
    }

    public void register(
            @NotNull JTabbedPane tabs,
            @NotNull ModulithVerificationPanel verificationPanel,
            @NotNull Runnable refreshAction,
            @NotNull Runnable autoRefreshAction) {
        this.tabs = tabs;
        this.verificationPanel = verificationPanel;
        this.refreshAction = refreshAction;
        this.autoRefreshAction = autoRefreshAction;
    }

    public void setAutoRefreshEnabled(boolean enabled) {
        autoRefreshEnabled = enabled;
        if (!enabled) {
            refreshTimer.stop();
        }
    }

    public void showVerification() {
        if (tabs == null || verificationPanel == null) {
            return;
        }

        tabs.setSelectedComponent(verificationPanel);
        verificationPanel.verify();
    }
}
