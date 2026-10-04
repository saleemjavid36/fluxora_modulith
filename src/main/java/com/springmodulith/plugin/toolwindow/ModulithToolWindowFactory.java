package com.springmodulith.plugin.toolwindow;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBPanel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.content.ContentFactory;
import com.intellij.util.ui.JBUI;
import com.springmodulith.plugin.action.ModulithExportArchitectureAction;
import com.springmodulith.plugin.configuration.ModulithProjectModelService;
import com.springmodulith.plugin.model.ModulithDependencyGraph;
import org.jetbrains.annotations.NotNull;

import javax.swing.JButton;
import javax.swing.JTabbedPane;
import javax.swing.JPanel;
import javax.swing.JSplitPane;
import javax.swing.event.ChangeListener;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Dimension;

public final class ModulithToolWindowFactory implements ToolWindowFactory {

    @Override
    public void createToolWindowContent(
            @NotNull Project project,
            @NotNull ToolWindow toolWindow) {

        // Keep the tool-window stripe label branded and make the global shortcut discoverable.
        toolWindow.setStripeTitle("Fluxora Modulith Ctrl+Alt+Shift+M");

        JPanel root = new JBPanel<>(new BorderLayout(8, 8));
        root.setBorder(JBUI.Borders.empty(8));

        JPanel header = new JBPanel<>(new BorderLayout(8, 8));

        header.add(
                new JBLabel("Spring Modulith Architecture"),
                BorderLayout.WEST
        );

        JPanel buttons = new JPanel(
                new java.awt.FlowLayout(
                        java.awt.FlowLayout.RIGHT,
                        6,
                        0
                )
        );

        JButton zoomOutButton = new JButton("−");
        JButton zoomLabel = new JButton("100%");
        JButton zoomInButton = new JButton("+");
        JButton autoRefreshButton = new JButton("Auto Refresh: OFF");
        JButton refreshButton = new JButton("Refresh");
        JButton exportArchitectureButton = new JButton("Export Architecture");
        exportArchitectureButton.setToolTipText(
                "Export architecture as JSON, Mermaid, PlantUML, or Graphviz DOT"
        );
        exportArchitectureButton.setFocusPainted(false);

        zoomOutButton.setToolTipText("Zoom out");
        zoomLabel.setToolTipText("Reset zoom to 100%");
        zoomInButton.setToolTipText("Zoom in");
        autoRefreshButton.setToolTipText("Automatically refresh the Modulith graph after code changes");

        buttons.add(zoomOutButton);
        buttons.add(zoomLabel);
        buttons.add(zoomInButton);
        buttons.add(autoRefreshButton);
        buttons.add(refreshButton);
        buttons.add(exportArchitectureButton);

        // Header actions are scoped to the tab where they are applicable.
        // Zoom controls belong to Module Graph; export actions belong to Verification.
        exportArchitectureButton.setVisible(false);

        header.add(buttons, BorderLayout.EAST);

        root.add(
                header,
                BorderLayout.NORTH
        );

        ModulithDependencyGraphPanel graphPanel =
                new ModulithDependencyGraphPanel(project);

        ModulithModuleDetailsPanel detailsPanel =
                new ModulithModuleDetailsPanel(project);

        JBScrollPane graphScrollPane =
                new JBScrollPane(graphPanel);

        JSplitPane graphSplit = new JSplitPane(
                JSplitPane.HORIZONTAL_SPLIT,
                graphScrollPane,
                detailsPanel
        );

        /*
         * Module Graph:
         * Keep the existing graph UI.
         *
         * Only the details panel behavior is changed:
         * - hidden initially
         * - shown after selecting a module/dependency
         * - tiny divider
         */
        graphSplit.setResizeWeight(0.65);
        graphSplit.setDividerSize(3);
        graphSplit.setBorder(null);

        graphSplit.setBackground(
                JBColor.namedColor(
                        "ToolWindow.background",
                        JBColor.background()
                )
        );

        graphScrollPane.setBorder(null);

        graphScrollPane.setMinimumSize(
                new Dimension(0, 0)
        );

        detailsPanel.setMinimumSize(
                new Dimension(0, 0)
        );

        /*
         * Show details when a module is selected.
         */
        graphPanel.setModuleSelectionListener(module -> {

            if (module == null) {
                collapseGraphDetails(
                        graphSplit,
                        detailsPanel
                );
                return;
            }

            detailsPanel.showModule(module);

            expandGraphDetails(
                    graphSplit,
                    detailsPanel
            );
        });

        /*
         * Show details when a dependency is selected.
         */
        graphPanel.setDependencySelectionListener(dependency -> {

            if (dependency == null) {
                collapseGraphDetails(
                        graphSplit,
                        detailsPanel
                );
                return;
            }

            detailsPanel.showDependency(dependency);

            expandGraphDetails(
                    graphSplit,
                    detailsPanel
            );
        });

        /*
         * Details are hidden when Module Graph is opened.
         */
        collapseGraphDetails(
                graphSplit,
                detailsPanel
        );

        ModulithStructureTreePanel structurePanel =
                new ModulithStructureTreePanel(project);

        ModulithVerificationPanel verificationPanel =
                new ModulithVerificationPanel(project);

        JTabbedPane tabs = new JTabbedPane();

        tabs.addTab(
                "Module Graph",
                graphSplit
        );

        tabs.addTab(
                "Module Structure",
                structurePanel
        );

        tabs.addTab(
                "Verification",
                verificationPanel
        );

        root.add(
                tabs,
                BorderLayout.CENTER
        );

        ChangeListener tabVisibilityListener = event -> {
            boolean moduleGraphSelected = tabs.getSelectedIndex() == 0;
            boolean verificationSelected = tabs.getSelectedIndex() == 2;

            zoomOutButton.setVisible(moduleGraphSelected);
            zoomLabel.setVisible(moduleGraphSelected);
            zoomInButton.setVisible(moduleGraphSelected);
            exportArchitectureButton.setVisible(verificationSelected);

            buttons.revalidate();
            buttons.repaint();
        };

        tabs.addChangeListener(tabVisibilityListener);
        tabVisibilityListener.stateChanged(null);

        /*
         * The controller in the current project requires:
         *
         * register(
         *     tabs,
         *     verificationPanel,
         *     refreshAction,
         *     autoRefreshAction
         * )
         *
         * Do not change that controller API.
         */

        Runnable refreshAction = () ->
                loadGraph(
                        project,
                        graphPanel,
                        detailsPanel,
                        graphSplit,
                        structurePanel,
                        refreshButton
                );

        Runnable autoRefreshAction = () ->
                loadGraph(
                        project,
                        graphPanel,
                        detailsPanel,
                        graphSplit,
                        structurePanel,
                        refreshButton
                );

        ModulithToolWindowController controller =
                project.getService(
                        ModulithToolWindowController.class
                );

        controller.register(
                tabs,
                verificationPanel,
                refreshAction,
                autoRefreshAction
        );

        /*
         * Auto Refresh is intentionally OFF by default.
         *
         * This prevents PSI changes from continuously rebuilding
         * the Modulith graph and consuming IDE resources.
         */
        controller.setAutoRefreshEnabled(false);

        /*
         * Auto Refresh is deliberately OFF when the tool window is created.
         * The user can opt in from the existing header control.
         */
        autoRefreshButton.addActionListener(e -> {
            boolean enabled = !autoRefreshButton.getText().endsWith("ON");
            controller.setAutoRefreshEnabled(enabled);
            autoRefreshButton.setText(
                    enabled
                            ? "Auto Refresh: ON"
                            : "Auto Refresh: OFF"
            );
        });

        zoomOutButton.addActionListener(e -> {
            graphPanel.zoomOut();
            updateZoomLabel(zoomLabel, graphPanel);
        });

        zoomInButton.addActionListener(e -> {
            graphPanel.zoomIn();
            updateZoomLabel(zoomLabel, graphPanel);
        });

        zoomLabel.addActionListener(e -> {
            graphPanel.resetZoom();
            updateZoomLabel(zoomLabel, graphPanel);
        });

        refreshButton.addActionListener(
                e -> refreshAction.run()
        );

        exportArchitectureButton.addActionListener(
                e -> {
                    ModulithExportArchitectureAction.showExportMenu(
                            project,
                            exportArchitectureButton,
                            verificationPanel
                    );
                    SwingUtilities.invokeLater(() -> {
                        exportArchitectureButton.setFocusPainted(false);
                        exportArchitectureButton.repaint();
                    });
                }
        );

        /*
         * Initial graph loading.
         */
        refreshAction.run();

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

    private void updateZoomLabel(
            @NotNull JButton zoomLabel,
            @NotNull ModulithDependencyGraphPanel graphPanel) {

        zoomLabel.setText(
                Math.round(graphPanel.getZoom() * 100.0d) + "%"
        );
    }

    private void expandGraphDetails(
            @NotNull JSplitPane graphSplit,
            @NotNull ModulithModuleDetailsPanel detailsPanel) {

        detailsPanel.setVisible(true);

        SwingUtilities.invokeLater(() -> {

            if (!graphSplit.isShowing()) {
                return;
            }

            graphSplit.setDividerLocation(0.65);
        });
    }

    private void collapseGraphDetails(
            @NotNull JSplitPane graphSplit,
            @NotNull ModulithModuleDetailsPanel detailsPanel) {

        detailsPanel.setVisible(false);

        SwingUtilities.invokeLater(() -> {

            if (!graphSplit.isShowing()) {
                return;
            }

            /*
             * Keep only the tiny divider visible.
             */
            graphSplit.setDividerLocation(1.0);
        });
    }

    private void loadGraph(
            @NotNull Project project,
            @NotNull ModulithDependencyGraphPanel graphPanel,
            @NotNull ModulithModuleDetailsPanel detailsPanel,
            @NotNull JSplitPane graphSplit,
            @NotNull ModulithStructureTreePanel structurePanel,
            @NotNull JButton refreshButton) {

        refreshButton.setEnabled(false);

        graphPanel.clearGraph();

        detailsPanel.clear();

        /*
         * Every explicit refresh starts with the details panel hidden.
         */
        collapseGraphDetails(
                graphSplit,
                detailsPanel
        );

        project
                .getService(
                        ModulithProjectModelService.class
                )
                .invalidate();

        new Task.Backgroundable(
                project,
                "Analyzing Spring Modulith modules",
                true) {

            @Override
            public void run(
                    @NotNull ProgressIndicator indicator) {

                indicator.setIndeterminate(true);

                ModulithDependencyGraph graph =
                        DumbService
                                .getInstance(project)
                                .runReadActionInSmartMode(() ->
                                        project
                                                .getService(
                                                        ModulithProjectModelService.class
                                                )
                                                .getGraph()
                                );

                ApplicationManager
                        .getApplication()
                        .invokeLater(() -> {

                            if (project.isDisposed()) {
                                return;
                            }

                            graphPanel.setGraph(graph);

                            detailsPanel.setGraph(graph);

                            structurePanel.setGraph(graph);

                            refreshButton.setEnabled(true);
                        });
            }

            @Override
            public void onThrowable(
                    @NotNull Throwable error) {

                ApplicationManager
                        .getApplication()
                        .invokeLater(() ->
                                refreshButton.setEnabled(true)
                        );
            }
        }.queue();
    }
}