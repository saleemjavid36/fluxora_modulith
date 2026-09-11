package com.springmodulith.plugin.toolwindow;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBPanel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.content.ContentFactory;
import com.intellij.util.ui.JBUI;
import com.springmodulith.plugin.analyzer.ModulithDependencyGraphAnalyzer;
import com.springmodulith.plugin.model.ModulithDependencyGraph;
import org.jetbrains.annotations.NotNull;

import javax.swing.JSplitPane;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JPanel;
import java.awt.BorderLayout;

public final class ModulithToolWindowFactory
        implements ToolWindowFactory {
    private ModulithDependencyGraphPanel graphPanel;
    private ModulithModuleDetailsPanel moduleDetailsPanel;

    @Override
    public void createToolWindowContent(
            @NotNull Project project,
            @NotNull ToolWindow toolWindow) {

        JPanel root =
                new JBPanel<>(
                        new BorderLayout(
                                8,
                                8
                        )
                );

        root.setBorder(
                JBUI.Borders.empty(8)
        );

        JPanel header =
                new JBPanel<>(
                        new BorderLayout(
                                8,
                                8
                        )
                );

        JBLabel title =
                new JBLabel(
                        "Spring Modulith Module Graph"
                );

        header.add(
                title,
                BorderLayout.WEST
        );

        JButton refreshButton =
                new JButton(
                        "Refresh"
                );

        header.add(
                refreshButton,
                BorderLayout.EAST
        );

        root.add(
                header,
                BorderLayout.NORTH
        );

        graphPanel =
                new ModulithDependencyGraphPanel(project);

        moduleDetailsPanel =
                new ModulithModuleDetailsPanel(project);

        graphPanel.setModuleSelectionListener(
                moduleDetailsPanel::showModule
        );
        graphPanel.setDependencySelectionListener(
                moduleDetailsPanel::showDependency
        );

        JBScrollPane graphScrollPane =
                new JBScrollPane(
                        graphPanel
                );

        graphScrollPane.setBorder(
                BorderFactory.createEmptyBorder()
        );

        JSplitPane splitPane =
                new JSplitPane(
                        JSplitPane.HORIZONTAL_SPLIT,
                        graphScrollPane,
                        moduleDetailsPanel
                );

        splitPane.setResizeWeight(0.65);
        splitPane.setDividerLocation(0.65);

        root.add(
                splitPane,
                BorderLayout.CENTER
        );

        refreshButton.addActionListener(
                event ->
                        loadGraph(
                                project,
                                graphPanel,
                                refreshButton
                        )
        );

        loadGraph(
                project,
                graphPanel,
                refreshButton
        );

        toolWindow
                .getContentManager()
                .addContent(
                        ContentFactory
                                .getInstance()
                                .createContent(
                                        root,
                                        "Module Graph",
                                        false
                                )
                );

    }

    private void loadGraph(
            @NotNull Project project,
            @NotNull ModulithDependencyGraphPanel graphPanel,
            @NotNull JButton refreshButton) {

        refreshButton.setEnabled(false);
        graphPanel.clearGraph();

        new Task.Backgroundable(
                project,
                "Analyzing Spring Modulith modules",
                true
        ) {
            @Override
            public void run(@NotNull ProgressIndicator indicator) {

                indicator.setIndeterminate(true);

                ModulithDependencyGraph graph =
                        DumbService.getInstance(project)
                                .runReadActionInSmartMode(
                                        () -> new ModulithDependencyGraphAnalyzer(project)
                                                .analyze()
                                );

                ApplicationManager.getApplication().invokeLater(() -> {
                    if (project.isDisposed()) {
                        return;
                    }

                    graphPanel.setGraph(graph);
                    moduleDetailsPanel.setGraph(graph);
                    refreshButton.setEnabled(true);
                });
            }

            @Override
            public void onThrowable(@NotNull Throwable error) {
                ApplicationManager.getApplication().invokeLater(() -> {
                    if (!project.isDisposed()) {
                        refreshButton.setEnabled(true);
                    }
                });
            }
        }.queue();
    }
}