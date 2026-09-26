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
import com.springmodulith.plugin.configuration.ModulithProjectModelService;
import com.springmodulith.plugin.model.ModulithDependencyGraph;
import org.jetbrains.annotations.NotNull;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JTabbedPane;
import javax.swing.event.ChangeListener;
import javax.swing.JPanel;
import javax.swing.JSplitPane;
import java.awt.BorderLayout;

public final class ModulithToolWindowFactory implements ToolWindowFactory {
    @Override
    public void createToolWindowContent(@NotNull Project project, @NotNull ToolWindow toolWindow) {
        JPanel root = new JBPanel<>(new BorderLayout(8, 8));
        root.setBorder(JBUI.Borders.empty(8));

        JPanel header = new JBPanel<>(new BorderLayout(8, 8));
        header.add(new JBLabel("Spring Modulith Architecture"), BorderLayout.WEST);
        JPanel buttons = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.RIGHT, 6, 0));

        JButton zoomOutButton = new JButton("−");
        JButton zoomLabelButton = new JButton("100%");
        JButton zoomInButton = new JButton("+");
        JButton refreshButton = new JButton("Refresh");
        JButton autoRefreshButton = new JButton("Auto Refresh: OFF");
        JButton exportButton = new JButton("Export JSON");

        zoomOutButton.setToolTipText("Zoom out");
        zoomLabelButton.setToolTipText("Reset zoom");
        zoomInButton.setToolTipText("Zoom in");

        buttons.add(zoomOutButton);
        buttons.add(zoomLabelButton);
        buttons.add(zoomInButton);
        buttons.add(autoRefreshButton);
        buttons.add(exportButton);
        buttons.add(refreshButton);
        header.add(buttons, BorderLayout.EAST);
        root.add(header, BorderLayout.NORTH);

        ModulithDependencyGraphPanel graphPanel = new ModulithDependencyGraphPanel(project);
        ModulithModuleDetailsPanel detailsPanel = new ModulithModuleDetailsPanel(project);
        graphPanel.setModuleSelectionListener(detailsPanel::showModule);
        graphPanel.setDependencySelectionListener(detailsPanel::showDependency);

        JBScrollPane graphScrollPane = new JBScrollPane(graphPanel);
        graphScrollPane.setHorizontalScrollBarPolicy(
                JBScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED
        );
        graphScrollPane.setVerticalScrollBarPolicy(
                JBScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED
        );
        graphScrollPane.getHorizontalScrollBar().setUnitIncrement(24);
        graphScrollPane.getVerticalScrollBar().setUnitIncrement(24);

        JSplitPane graphSplit = new JSplitPane(
                JSplitPane.HORIZONTAL_SPLIT,
                graphScrollPane,
                detailsPanel);
        graphSplit.setResizeWeight(0.65);
        graphSplit.setDividerLocation(0.65);

        ModulithStructureTreePanel structurePanel = new ModulithStructureTreePanel(project);
        ModulithVerificationPanel verificationPanel = new ModulithVerificationPanel(project);

        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Module Graph", graphSplit);
        tabs.addTab("Module Structure", structurePanel);
        tabs.addTab("Verification", verificationPanel);
        root.add(tabs, BorderLayout.CENTER);

        exportButton.setVisible(false);
        exportButton.setEnabled(false);
        verificationPanel.addPropertyChangeListener("verificationResult", event ->
                exportButton.setEnabled(verificationPanel.hasVerificationResult())
        );

        // Zoom controls are meaningful only for the Module Graph tab.
        ChangeListener tabChangeListener = event -> {
            boolean graphTabSelected = tabs.getSelectedIndex() == 0;
            boolean verificationTabSelected = tabs.getSelectedIndex() == 2;

            zoomOutButton.setVisible(graphTabSelected);
            zoomLabelButton.setVisible(graphTabSelected);
            zoomInButton.setVisible(graphTabSelected);
            exportButton.setVisible(verificationTabSelected);
            exportButton.setEnabled(
                    verificationTabSelected && verificationPanel.hasVerificationResult()
            );

            buttons.revalidate();
            buttons.repaint();
        };
        tabs.addChangeListener(tabChangeListener);
        tabChangeListener.stateChanged(null);

        Runnable refreshAll = () -> {
            if (!refreshButton.isEnabled()) {
                return;
            }

            loadGraph(project, graphPanel, detailsPanel, structurePanel, refreshButton);
            if (verificationPanel.hasVerificationResult()) {
                verificationPanel.verify();
            }
        };

        ModulithToolWindowController controller =
                project.getService(ModulithToolWindowController.class);

        Runnable autoRefresh = () -> {
            if (!refreshButton.isEnabled()) {
                return;
            }
            loadGraph(project, graphPanel, detailsPanel, structurePanel, refreshButton);
        };

        controller.register(tabs, verificationPanel, refreshAll, autoRefresh);
        controller.setAutoRefreshEnabled(false);

        autoRefreshButton.addActionListener(e -> {
            boolean enabled = autoRefreshButton.getText().endsWith("OFF");
            controller.setAutoRefreshEnabled(enabled);
            autoRefreshButton.setText(
                    enabled ? "Auto Refresh: ON" : "Auto Refresh: OFF"
            );
        });

        Runnable updateZoomLabel = () ->
                zoomLabelButton.setText(
                        Math.round(graphPanel.getZoom() * 100) + "%"
                );

        zoomOutButton.addActionListener(e -> {
            graphPanel.zoomOut();
            updateZoomLabel.run();
        });

        zoomLabelButton.addActionListener(e -> {
            graphPanel.resetZoom();
            updateZoomLabel.run();
        });

        zoomInButton.addActionListener(e -> {
            graphPanel.zoomIn();
            updateZoomLabel.run();
        });

        refreshButton.addActionListener(e -> refreshAll.run());
        exportButton.addActionListener(e -> verificationPanel.exportVerificationResult());
        loadGraph(project, graphPanel, detailsPanel, structurePanel, refreshButton);

        toolWindow.getContentManager().addContent(
                ContentFactory.getInstance().createContent(root, "Module Graph", false));
    }

    private void loadGraph(
            @NotNull Project project,
            @NotNull ModulithDependencyGraphPanel graphPanel,
            @NotNull ModulithModuleDetailsPanel detailsPanel,
            @NotNull ModulithStructureTreePanel structurePanel,
            @NotNull JButton refreshButton) {

        refreshButton.setEnabled(false);
        graphPanel.clearGraph();
        detailsPanel.clear();

        project.getService(ModulithProjectModelService.class).invalidate();

        new Task.Backgroundable(
                project,
                "Analyzing Spring Modulith modules",
                true) {

            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                indicator.setIndeterminate(true);

                ModulithDependencyGraph graph =
                        DumbService.getInstance(project)
                                .runReadActionInSmartMode(() ->
                                        project.getService(
                                                ModulithProjectModelService.class
                                        ).getGraph()
                                );

                ApplicationManager.getApplication().invokeLater(() -> {
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
            public void onThrowable(@NotNull Throwable error) {
                ApplicationManager.getApplication().invokeLater(() ->
                        refreshButton.setEnabled(true)
                );
            }
        }.queue();
    }
}
