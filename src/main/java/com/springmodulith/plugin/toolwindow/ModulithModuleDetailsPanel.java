package com.springmodulith.plugin.toolwindow;

import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBList;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import com.springmodulith.plugin.model.ModulithDependencyGraph;
import com.springmodulith.plugin.model.ModulithDependencyReference;
import com.springmodulith.plugin.model.ModulithModule;
import com.springmodulith.plugin.model.NamedInterface;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JLabel;
import javax.swing.JScrollPane;
import javax.swing.JPanel;
import javax.swing.ListCellRenderer;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
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
        setBorder(JBUI.Borders.empty(10));
        setMinimumSize(new Dimension(260, 0));

        JPanel header = new JPanel();
        header.setOpaque(false);
        header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));
        header.setBorder(JBUI.Borders.emptyBottom(8));

        moduleName.setFont(moduleName.getFont().deriveFont(Font.BOLD, 18.0f));
        moduleName.setForeground(UIUtil.getLabelForeground());

        packageName.setBorder(JBUI.Borders.emptyTop(5));
        packageName.setForeground(UIUtil.getContextHelpForeground());
        packageName.setFont(packageName.getFont().deriveFont(12.0f));

        status.setBorder(JBUI.Borders.emptyTop(7));
        status.setFont(status.getFont().deriveFont(Font.BOLD, 11.0f));

        header.add(moduleName);
        header.add(packageName);
        header.add(status);

        add(header, BorderLayout.NORTH);

        content.setOpaque(false);
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));

        JBScrollPane scrollPane = new JBScrollPane(content);
        scrollPane.setBorder(BorderFactory.createEmptyBorder());
        scrollPane.setOpaque(false);
        scrollPane.getViewport().setOpaque(false);
        scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        scrollPane.getVerticalScrollBar().setUnitIncrement(12);
        scrollPane.getHorizontalScrollBar().setUnitIncrement(16);
        add(scrollPane, BorderLayout.CENTER);

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
        addInfoBlock(
                "No recognized application modules were detected in this project.",
                "Add @ApplicationModule or configure module detection to populate the architecture graph."
        );

        revalidate();
        repaint();
    }

    public void showModule(@Nullable ModulithModule module) {
        if (module == null) {
            clear();
            return;
        }

        moduleName.setText(module.getName());
        packageName.setText("Package  •  " + module.getPackageName());

        boolean explicit = module.isAllowedDependenciesConfigured();
        String state = module.isOpen() ? "OPEN" : "CLOSED";
        String rule = explicit ? "EXPLICIT RULES" : "IMPLICIT DEPENDENCIES";

        status.setText("●  " + state + "   •   " + rule);
        status.setForeground(module.isOpen()
                ? new JBColor(new Color(0x2E7D32), new Color(0x81C784))
                : explicit
                ? new JBColor(new Color(0x1565C0), new Color(0x64B5F6))
                : UIUtil.getContextHelpForeground());

        content.removeAll();

        addSection("Allowed dependencies",
                "Dependencies explicitly allowed by this module",
                new ArrayList<>(module.getAllowedDependencies()));

        addSection("Named interfaces",
                "Public APIs exposed by this module",
                getNamedInterfaces(module));

        addSection("Outgoing dependencies",
                "Modules this module depends on",
                getOutgoingDependencies(module));

        addSection("Incoming dependencies",
                "Modules that depend on this module",
                getIncomingDependencies(module));

        addSection("Cycle status",
                "Dependency cycle information",
                getCycleStatus(module));

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
                        + "  →  "
                        + dependency.targetPackage()
        );

        packageName.setText(
                dependency.namedInterface() == null
                        ? "Module dependency"
                        : "Named interface  •  " + dependency.namedInterface()
        );

        status.setText("●  " + edgeStatus(dependency));
        status.setForeground(statusColor(edgeStatus(dependency)));

        content.removeAll();
        addDependencyDetails(dependency);
        addReferenceSection(dependency);

        revalidate();
        repaint();
    }

    private void addDependencyDetails(
            @NotNull ModulithDependencyGraph.ModuleDependency dependency) {

        addSectionHeader(
                "Dependency details",
                "Information for the selected dependency"
        );

        ModulithDependencyReference reference = dependency.references().isEmpty()
                ? null
                : dependency.references().get(0);

        addDetailRow(
                "Source",
                moduleNameForPackage(dependency.sourcePackage())
        );
        addDetailRow(
                "Target",
                moduleNameForPackage(dependency.targetPackage())
        );
        addDetailRow(
                "Referenced type",
                reference == null || reference.referencedType().isEmpty()
                        ? "—"
                        : reference.referencedType()
        );
        addDetailRow(
                "Source file",
                reference == null
                        ? "—"
                        : reference.file().getName()
        );
        addDetailRow(
                "Target package",
                reference == null || reference.targetPackage().isEmpty()
                        ? dependency.targetPackage()
                        : reference.targetPackage()
        );
        addDetailRow(
                "Rule",
                reference == null || reference.rule().isEmpty()
                        ? "—"
                        : reference.rule()
        );
        addDetailRow(
                "Status",
                reference == null || reference.status().isEmpty()
                        ? edgeStatus(dependency)
                        : reference.status()
        );

        if (dependency.references().size() > 1) {
            addDetailRow(
                    "Selected reference",
                    "1 of " + dependency.references().size()
            );
        }
    }

    @NotNull
    private String moduleNameForPackage(@NotNull String packageName) {
        if (graph != null) {
            for (ModulithModule module : graph.getModules()) {
                if (module.getPackageName().equals(packageName)) {
                    return module.getName();
                }
            }
        }
        return packageName;
    }

    private void addDetailRow(
            @NotNull String label,
            @NotNull String value) {

        JPanel row = new JPanel(new BorderLayout(8, 0));
        row.setOpaque(false);
        row.setBorder(JBUI.Borders.empty(3, 2));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);

        JBLabel labelComponent = new JBLabel(label + ":");
        labelComponent.setForeground(UIUtil.getContextHelpForeground());
        labelComponent.setFont(labelComponent.getFont().deriveFont(Font.PLAIN, 11.0f));

        JBLabel valueComponent = new JBLabel(value);
        valueComponent.setForeground(UIUtil.getLabelForeground());
        valueComponent.setFont(valueComponent.getFont().deriveFont(Font.PLAIN, 12.0f));

        row.add(labelComponent, BorderLayout.WEST);
        row.add(valueComponent, BorderLayout.CENTER);

        Dimension preferredSize = row.getPreferredSize();
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, preferredSize.height));
        content.add(row);
    }

    private void addReferenceSection(
            @NotNull ModulithDependencyGraph.ModuleDependency dependency) {
        addSectionHeader(
                "References",
                dependency.referenceCount() + " source reference"
                        + (dependency.referenceCount() == 1 ? "" : "s")
        );

        List<ModulithDependencyReference> references = dependency.references();

        if (references.isEmpty()) {
            addEmptyValue("No source references recorded");
            return;
        }

        JBList<ModulithDependencyReference> list = new JBList<>(references);
        configureList(list, 10);
        list.setToolTipText("Double-click a reference to open it");
        list.setCellRenderer(new ReferenceRenderer());

        list.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent event) {
                if (event.getClickCount() != 2) return;

                int index = list.locationToIndex(event.getPoint());
                if (index < 0) return;

                ModulithDependencyReference reference =
                        list.getModel().getElementAt(index);

                ModulithModuleNavigation.openReference(project, reference);
            }
        });

        content.add(list);
    }

    private List<String> getNamedInterfaces(@NotNull ModulithModule module) {
        List<String> result = new ArrayList<>();

        for (NamedInterface namedInterface : module.getNamedInterfaces()) {
            result.add(
                    namedInterface.getName()
                            + "  —  "
                            + namedInterface.getPackageName()
            );
        }

        return result;
    }

    private List<String> getOutgoingDependencies(@NotNull ModulithModule module) {
        if (graph == null) return List.of();

        List<String> result = new ArrayList<>();

        for (ModulithDependencyGraph.ModuleDependency dependency
                : graph.getOutgoingDependencies(module)) {

            String status = dependency.isMixed()
                    ? "MIXED"
                    : dependency.isForbidden()
                    ? "FORBIDDEN"
                    : dependency.isNamedInterface()
                    ? "NAMED INTERFACE"
                    : "ALLOWED";

            if (dependency.isApiViolation()) {
                status += " + API violation";
            }

            if (graph.isCyclicEdge(dependency)) {
                status += " + CYCLE";
            }

            String interfaceName = dependency.namedInterfaces().isEmpty()
                    ? ""
                    : "  ::  " + String.join(", ", dependency.namedInterfaces());

            result.add(
                    dependency.targetPackage()
                            + interfaceName
                            + "    ["
                            + status
                            + "]"
            );
        }

        return result;
    }

    private List<String> getIncomingDependencies(@NotNull ModulithModule module) {
        if (graph == null) return List.of();

        List<String> result = new ArrayList<>();

        for (ModulithDependencyGraph.ModuleDependency dependency
                : graph.getIncomingDependencies(module)) {

            result.add(
                    dependency.sourcePackage()
                            + "    ["
                            + edgeStatus(dependency)
                            + "]"
            );
        }

        return result;
    }

    private String edgeStatus(
            @NotNull ModulithDependencyGraph.ModuleDependency dependency) {
        String status = dependency.isMixed()
                ? "MIXED"
                : dependency.isForbidden()
                ? "FORBIDDEN"
                : dependency.isNamedInterface()
                ? "NAMED INTERFACE"
                : "ALLOWED";

        if (dependency.isApiViolation()) {
            status += " + API violation";
        }

        if (graph != null && graph.isCyclicEdge(dependency)) {
            status += " + CYCLE";
        }

        return status;
    }

    private List<String> getCycleStatus(@NotNull ModulithModule module) {
        if (graph == null) return List.of("No graph loaded");

        List<String> result = new ArrayList<>();

        for (ModulithDependencyGraph.ModuleDependency dependency
                : graph.getDependencies()) {

            if (dependency.sourcePackage().equals(module.getPackageName())
                    && graph.isCyclicEdge(dependency)) {
                result.add(
                        "Cycle detected through "
                                + dependency.targetPackage()
                );
            }
        }

        if (result.isEmpty()) {
            result.add(module.isOpen()
                    ? "Open modules are excluded from cycle detection"
                    : "No dependency cycle detected");
        }

        return result;
    }

    private void addSection(
            @NotNull String title,
            @NotNull String description,
            @NotNull List<String> values) {

        addSectionHeader(title, description);

        if (values.isEmpty()) {
            addEmptyValue("None");
            return;
        }

        JBList<String> list = new JBList<>(values);
        list.setCellRenderer(new DetailsRenderer());
        configureList(list, 8);

        content.add(list);
    }

    private void addSectionHeader(
            @NotNull String title,
            @NotNull String description) {

        content.add(Box.createVerticalStrut(8));

        JPanel header = new JPanel(new BorderLayout(8, 2));
        header.setOpaque(false);
        header.setBorder(
                BorderFactory.createCompoundBorder(
                        JBUI.Borders.customLine(JBColor.border(), 1, 0, 0, 0),
                        JBUI.Borders.empty(10, 2, 5, 2)
                )
        );

        JBLabel titleLabel = new JBLabel(title);
        titleLabel.setFont(
                titleLabel.getFont().deriveFont(Font.BOLD, 13.0f)
        );
        titleLabel.setForeground(UIUtil.getLabelForeground());

        JBLabel descriptionLabel = new JBLabel(description);
        descriptionLabel.setFont(
                descriptionLabel.getFont().deriveFont(11.0f)
        );
        descriptionLabel.setForeground(UIUtil.getContextHelpForeground());

        JPanel text = new JPanel();
        text.setOpaque(false);
        text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));
        text.add(titleLabel);
        text.add(Box.createVerticalStrut(2));
        text.add(descriptionLabel);

        header.add(text, BorderLayout.CENTER);
        header.setAlignmentX(Component.LEFT_ALIGNMENT);

        // Keep each section header at its natural height. BoxLayout would
        // otherwise stretch the header vertically and push the section data
        // to the bottom when the details panel has extra space.
        Dimension headerSize = header.getPreferredSize();
        header.setMaximumSize(new Dimension(Integer.MAX_VALUE, headerSize.height));

        content.add(header);
    }

    private void addEmptyValue(@NotNull String text) {
        JPanel empty = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
        empty.setOpaque(false);

        JBLabel label = new JBLabel("—  " + text);
        label.setForeground(UIUtil.getContextHelpForeground());
        label.setFont(label.getFont().deriveFont(Font.ITALIC, 12.0f));

        empty.add(label);

        // BoxLayout will otherwise stretch this placeholder vertically because
        // JPanel has an effectively unbounded maximum height. Keep the value
        // directly below its section header and leave spare space at the bottom.
        Dimension preferredSize = empty.getPreferredSize();
        empty.setMaximumSize(new Dimension(Integer.MAX_VALUE, preferredSize.height));
        empty.setAlignmentX(Component.LEFT_ALIGNMENT);

        content.add(empty);
    }

    private void addInfoBlock(
            @NotNull String firstLine,
            @NotNull String secondLine) {

        JPanel panel = new JPanel();
        panel.setOpaque(false);
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(JBUI.Borders.emptyTop(12));

        JBLabel first = new JBLabel(firstLine);
        first.setFont(first.getFont().deriveFont(Font.BOLD, 12.0f));

        JBLabel second = new JBLabel(
                "<html><body style='width:220px'>"
                        + secondLine
                        + "</body></html>"
        );
        second.setForeground(UIUtil.getContextHelpForeground());
        second.setBorder(JBUI.Borders.emptyTop(6));

        panel.add(first);
        panel.add(second);

        Dimension preferredSize = panel.getPreferredSize();
        panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, preferredSize.height));
        panel.setAlignmentX(Component.LEFT_ALIGNMENT);

        content.add(panel);
    }

    private void configureList(
            @NotNull JBList<?> list,
            int visibleRows) {

        list.setVisibleRowCount(Math.min(list.getModel().getSize(), visibleRows));
        list.setBorder(JBUI.Borders.empty(2, 4, 8, 4));
        list.setBackground(UIUtil.getPanelBackground());
        list.setSelectionBackground(UIUtil.getListSelectionBackground());
        list.setSelectionForeground(UIUtil.getListSelectionForeground());
        list.setAlignmentX(Component.LEFT_ALIGNMENT);

        // Keep the list at its actual content size. In particular, do not give
        // it an unlimited maximum width: long dependency values must be able
        // to make the details scroll pane show a horizontal scrollbar.
        Dimension preferredSize = list.getPreferredSize();
        int width = Math.max(220, preferredSize.width);
        int height = preferredSize.height;
        Dimension contentSize = new Dimension(width, height);
        list.setPreferredSize(contentSize);
        list.setMinimumSize(new Dimension(0, height));
        list.setMaximumSize(contentSize);
    }

    private Color statusColor(@NotNull String statusText) {
        if (statusText.contains("FORBIDDEN")
                || statusText.contains("violation")) {
            return new JBColor(
                    new Color(0xC62828),
                    new Color(0xEF9A9A)
            );
        }

        if (statusText.contains("NAMED INTERFACE")) {
            return new JBColor(
                    new Color(0x6A1B9A),
                    new Color(0xCE93D8)
            );
        }

        if (statusText.contains("ALLOWED")) {
            return new JBColor(
                    new Color(0x2E7D32),
                    new Color(0x81C784)
            );
        }

        if (statusText.contains("CYCLE")) {
            return new JBColor(
                    new Color(0xEF6C00),
                    new Color(0xFFB74D)
            );
        }

        return UIUtil.getContextHelpForeground();
    }

    public void clear() {
        moduleName.setText("No module selected");
        moduleName.setForeground(UIUtil.getLabelForeground());
        packageName.setText("");
        status.setText("");
        content.removeAll();
        revalidate();
        repaint();
    }

    private static class DetailsRenderer
            extends DefaultListCellRenderer {

        @Override
        public Component getListCellRendererComponent(
                javax.swing.JList<?> list,
                Object value,
                int index,
                boolean selected,
                boolean focused) {

            JLabel label = (JLabel) super.getListCellRendererComponent(
                    list, value, index, selected, focused);

            label.setBorder(JBUI.Borders.empty(5, 6));
            label.setFont(label.getFont().deriveFont(12.0f));

            if (!selected) {
                label.setForeground(UIUtil.getLabelForeground());
            }

            String text = String.valueOf(value);

            if (text.contains("    [")) {
                int marker = text.indexOf("    [");
                String mainText = escapeHtml(text.substring(0, marker).trim());
                String stateText = escapeHtml(text.substring(marker + 5).trim());

                String stateColor = "#8A8A8A";
                if (text.contains("FORBIDDEN") || text.contains("API violation")) {
                    stateColor = "#EF9A9A";
                } else if (text.contains("NAMED INTERFACE")) {
                    stateColor = "#CE93D8";
                } else if (text.contains("ALLOWED")) {
                    stateColor = "#81C784";
                } else if (text.contains("CYCLE")) {
                    stateColor = "#FFB74D";
                }

                // Keep the dependency target and its status on one line.
                // The outer details scroll pane handles horizontal overflow.
                label.setText(
                        "<html><nobr><b>" + mainText + "</b> "
                                + "<font color='" + stateColor + "'>"
                                + stateText
                                + "</font></nobr></html>"
                );
            } else {
                // No fixed HTML width here. A fixed width forces Swing's HTML
                // renderer to wrap long package names onto multiple lines.
                label.setText(
                        "<html><nobr>"
                                + escapeHtml(text)
                                + "</nobr></html>"
                );
            }

            return label;
        }

        private static String escapeHtml(String value) {
            return value
                    .replace("&", "&amp;")
                    .replace("<", "&lt;")
                    .replace(">", "&gt;")
                    .replace("\"", "&quot;");
        }
    }
    private static class ReferenceRenderer
            extends DefaultListCellRenderer {

        @Override
        public Component getListCellRendererComponent(
                javax.swing.JList<?> list,
                Object value,
                int index,
                boolean selected,
                boolean focused) {

            JLabel label = (JLabel) super.getListCellRendererComponent(
                    list, value, index, selected, focused);

            label.setBorder(JBUI.Borders.empty(5, 6));
            label.setFont(label.getFont().deriveFont(12.0f));

            if (!selected) {
                label.setForeground(UIUtil.getLabelForeground());
            }

            return label;
        }
    }
}




