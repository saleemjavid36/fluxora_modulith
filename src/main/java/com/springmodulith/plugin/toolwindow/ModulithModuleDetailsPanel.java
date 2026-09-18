package com.springmodulith.plugin.toolwindow;

import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBList;
import com.intellij.util.ui.JBUI;
import com.springmodulith.plugin.model.ModulithDependencyGraph;
import com.springmodulith.plugin.model.ModulithDependencyReference;
import com.springmodulith.plugin.model.ModulithModule;
import com.springmodulith.plugin.model.NamedInterface;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.util.ArrayList;
import java.util.List;

public final class ModulithModuleDetailsPanel extends JPanel {

    private final JBLabel moduleName = new JBLabel();
    private final JBLabel packageName = new JBLabel();
    private final JBLabel status = new JBLabel();
    private final JPanel content = new JPanel();
    private final com.intellij.openapi.project.Project project;
    private ModulithDependencyGraph graph;

    public ModulithModuleDetailsPanel(
            @NotNull com.intellij.openapi.project.Project project) {
        this.project = project;
        setLayout(new BorderLayout());
        setBorder(JBUI.Borders.empty(12));

        JPanel header = new JPanel();
        header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));

        moduleName.setFont(moduleName.getFont().deriveFont(18.0f));
        packageName.setBorder(JBUI.Borders.emptyTop(4));
        status.setBorder(JBUI.Borders.emptyTop(4));

        header.add(moduleName);
        header.add(packageName);
        header.add(status);
        add(header, BorderLayout.NORTH);

        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        add(content, BorderLayout.CENTER);
        clear();
    }

    public void setGraph(@Nullable ModulithDependencyGraph graph) {
        this.graph = graph;

        if (graph != null && graph.getModules().isEmpty()) {
            showNoModulesFound();
        } else {
            clear();
        }
    }
    private void showNoModulesFound() {
        moduleName.setText("No Spring Modulith modules found");
        packageName.setText("");
        status.setText("");

        content.removeAll();

        content.add(Box.createVerticalStrut(12));

        content.add(new JBLabel(
                "No recognized application modules were detected in this project."
        ));

        content.add(Box.createVerticalStrut(6));

        content.add(new JBLabel(
                "Add @ApplicationModule or configure module detection to"
        ));

        content.add(new JBLabel(
                "populate the architecture graph."
        ));

        revalidate();
        repaint();
    }

    public void showModule(@Nullable ModulithModule module) {
        if (module == null) {
            clear();
            return;
        }

        moduleName.setText(module.getName());
        packageName.setText("Package: " + module.getPackageName());
        status.setText("Status: " + (module.isOpen() ? "OPEN" : "CLOSED")
                + "  •  " + (module.isAllowedDependenciesConfigured()
                ? "explicit allowedDependencies"
                : "no explicit allowedDependencies"));

        content.removeAll();

        addSection("Allowed dependencies", new ArrayList<>(module.getAllowedDependencies()));
        addSection("Named interfaces", getNamedInterfaces(module));
        addSection("Outgoing dependencies", getOutgoingDependencies(module));
        addSection("Incoming dependencies", getIncomingDependencies(module));
        addSection("Cycle status", getCycleStatus(module));

        revalidate();
        repaint();
    }

    public void showDependency(
            @Nullable ModulithDependencyGraph.ModuleDependency dependency) {
        if (dependency == null) {
            clear();
            return;
        }

        moduleName.setText(
                dependency.sourcePackage()
                        + " → "
                        + dependency.targetPackage()
        );
        packageName.setText(
                dependency.namedInterface() == null
                        ? "Module dependency"
                        : "Named interface: " + dependency.namedInterface()
        );
        status.setText("Status: " + edgeStatus(dependency));

        content.removeAll();
        addReferenceSection(dependency);

        revalidate();
        repaint();
    }

    private void addReferenceSection(
            @NotNull ModulithDependencyGraph.ModuleDependency dependency) {
        content.add(Box.createVerticalStrut(14));
        JLabel titleLabel = new JBLabel(
                "References (" + dependency.referenceCount() + ")"
        );
        titleLabel.setFont(titleLabel.getFont().deriveFont(14.0f));
        content.add(titleLabel);

        List<ModulithDependencyReference> references =
                dependency.references();

        if (references.isEmpty()) {
            content.add(new JBLabel("No source references recorded"));
            return;
        }

        JBList<ModulithDependencyReference> list =
                new JBList<>(references);
        list.setVisibleRowCount(Math.min(references.size(), 10));
        list.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
        list.setToolTipText("Double-click a reference to open it");
        list.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent event) {
                if (event.getClickCount() != 2) return;
                int index = list.locationToIndex(event.getPoint());
                if (index < 0) return;
                ModulithDependencyReference reference = list.getModel().getElementAt(index);
                ModulithModuleNavigation.openReference(project, reference);
            }
        });
        content.add(list);
    }

    private List<String> getNamedInterfaces(@NotNull ModulithModule module) {
        List<String> result = new ArrayList<>();
        for (NamedInterface namedInterface : module.getNamedInterfaces()) {
            result.add(namedInterface.getName() + " — " + namedInterface.getPackageName());
        }
        return result;
    }

    private List<String> getOutgoingDependencies(@NotNull ModulithModule module) {
        if (graph == null) return List.of();
        List<String> result = new ArrayList<>();
        for (ModulithDependencyGraph.ModuleDependency dependency : graph.getOutgoingDependencies(module)) {
            String status = dependency.isForbidden()
                    ? "FORBIDDEN"
                    : dependency.isNamedInterface()
                    ? "NAMED INTERFACE"
                    : "ALLOWED";
            if (dependency.isApiViolation()) status += " + API violation";
            if (graph.isCyclicEdge(dependency)) status += " + CYCLE";
            String interfaceName = dependency.namedInterface() == null
                    ? ""
                    : " :: " + dependency.namedInterface();
            result.add(dependency.targetPackage() + interfaceName + " [" + status + "]");
        }
        return result;
    }

    private List<String> getIncomingDependencies(@NotNull ModulithModule module) {
        if (graph == null) return List.of();
        List<String> result = new ArrayList<>();
        for (ModulithDependencyGraph.ModuleDependency dependency : graph.getIncomingDependencies(module)) {
            result.add(dependency.sourcePackage() + " [" + edgeStatus(dependency) + "]");
        }
        return result;
    }

    private String edgeStatus(@NotNull ModulithDependencyGraph.ModuleDependency dependency) {
        String status = dependency.isForbidden()
                ? "FORBIDDEN"
                : dependency.isNamedInterface()
                ? "NAMED INTERFACE"
                : "ALLOWED";
        if (dependency.isApiViolation()) status += " + API violation";
        if (graph != null && graph.isCyclicEdge(dependency)) status += " + CYCLE";
        return status;
    }

    private List<String> getCycleStatus(@NotNull ModulithModule module) {
        if (graph == null) return List.of("No graph loaded");
        List<String> result = new ArrayList<>();
        for (ModulithDependencyGraph.ModuleDependency dependency : graph.getDependencies()) {
            if (dependency.sourcePackage().equals(module.getPackageName())
                    && graph.isCyclicEdge(dependency)) {
                result.add("Cycle detected through " + dependency.targetPackage());
            }
        }
        if (result.isEmpty()) result.add(module.isOpen()
                ? "Open modules are excluded from cycle detection"
                : "No dependency cycle detected");
        return result;
    }

    private void addSection(@NotNull String title, @NotNull List<String> values) {
        content.add(Box.createVerticalStrut(14));
        JLabel titleLabel = new JBLabel(title);
        titleLabel.setFont(titleLabel.getFont().deriveFont(14.0f));
        content.add(titleLabel);

        if (values.isEmpty()) {
            content.add(new JBLabel("None"));
            return;
        }

        JBList<String> list = new JBList<>(values);
        list.setVisibleRowCount(Math.min(values.size(), 8));
        list.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
        content.add(list);
    }

    public void clear() {
        moduleName.setText("No module selected");
        packageName.setText("");
        status.setText("");
        content.removeAll();
        revalidate();
        repaint();
    }
}
