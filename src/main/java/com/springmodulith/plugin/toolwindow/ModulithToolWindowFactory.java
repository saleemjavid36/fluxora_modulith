package com.springmodulith.plugin.toolwindow;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
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

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JPanel;
import java.awt.BorderLayout;

public final class ModulithToolWindowFactory
        implements ToolWindowFactory {

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

        ModulithDependencyGraphPanel graphPanel =
                new ModulithDependencyGraphPanel();

        JBScrollPane scrollPane =
                new JBScrollPane(
                        graphPanel
                );

        scrollPane.setBorder(
                BorderFactory.createEmptyBorder()
        );

        root.add(
                scrollPane,
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

        refreshButton.setEnabled(
                false
        );

        graphPanel.clearGraph();

        new Task.Backgroundable(
                project,
                "Analyzing Spring Modulith modules",
                true
        ) {

            @Override
            public void run(
                    @NotNull ProgressIndicator indicator) {

                indicator.setIndeterminate(
                        true
                );

                ModulithDependencyGraph graph =
                        ReadAction.compute(
                                () ->
                                        new ModulithDependencyGraphAnalyzer(
                                                project
                                        ).analyze()
                        );

                ApplicationManager
                        .getApplication()
                        .invokeLater(
                                () -> {

                                    graphPanel.setGraph(
                                            graph
                                    );

                                    refreshButton.setEnabled(
                                            true
                                    );
                                }
                        );
            }
        }.queue();
    }
}