package com.springmodulith.plugin.toolwindow;

import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.ui.JBUI;
import com.springmodulith.plugin.model.ModulithDependencyGraph;
import com.springmodulith.plugin.model.ModulithModule;
import com.springmodulith.plugin.model.NamedInterface;
import org.jetbrains.annotations.NotNull;

import javax.swing.JLabel;
import javax.swing.JTree;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreePath;
import java.awt.BorderLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

public final class ModulithStructureTreePanel extends JPanel {
    private final com.intellij.openapi.project.Project project;
    private final JTree tree = new JTree(new DefaultMutableTreeNode("No graph loaded"));
    private ModulithDependencyGraph graph;
    private final ModulithNamedInterfaceTypesPanel typesPanel;

    public ModulithStructureTreePanel(@NotNull com.intellij.openapi.project.Project project) {
        this.project = project;
        this.typesPanel = new ModulithNamedInterfaceTypesPanel(project);
        setLayout(new BorderLayout(8, 8));
        setBorder(JBUI.Borders.empty(8));
        add(new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, new JBScrollPane(tree), typesPanel), BorderLayout.CENTER);
        tree.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() != 2) return;
                TreePath path = tree.getPathForLocation(e.getX(), e.getY());
                if (path == null) return;
                Object value = ((DefaultMutableTreeNode) path.getLastPathComponent()).getUserObject();
                if (value instanceof ModulithModule module) ModulithModuleNavigation.openPackage(project, module);
                if (value instanceof String type && type.contains(".")) ModulithModuleNavigation.openQualifiedType(project, type);
            }
        });
        tree.addTreeSelectionListener(e -> {
            Object value = ((DefaultMutableTreeNode) e.getPath().getLastPathComponent()).getUserObject();
            if (value instanceof ModulithModule module) typesPanel.setModule(module);
        });
    }

    public void setGraph(ModulithDependencyGraph graph) {
        this.graph = graph;
        DefaultMutableTreeNode root = new DefaultMutableTreeNode("Spring Modulith Modules");
        for (ModulithModule module : graph.getModules()) {
            DefaultMutableTreeNode moduleNode = new DefaultMutableTreeNode(module);
            DefaultMutableTreeNode packageNode = new DefaultMutableTreeNode("Package: " + module.getPackageName());
            moduleNode.add(packageNode);
            DefaultMutableTreeNode interfacesNode = new DefaultMutableTreeNode("Named Interfaces");
            for (NamedInterface namedInterface : module.getNamedInterfaces()) {
                DefaultMutableTreeNode interfaceNode = new DefaultMutableTreeNode(namedInterface.getName() + " — " + namedInterface.getPackageName());
                for (String type : namedInterface.getTypeNames()) interfaceNode.add(new DefaultMutableTreeNode(type));
                interfacesNode.add(interfaceNode);
            }
            moduleNode.add(interfacesNode);
            DefaultMutableTreeNode outgoing = new DefaultMutableTreeNode("Outgoing Dependencies");
            for (ModulithDependencyGraph.ModuleDependency dependency : graph.getOutgoingDependencies(module)) {
                outgoing.add(new DefaultMutableTreeNode(dependency.targetPackage() + " [" + dependency.kind() + "]"));
            }
            moduleNode.add(outgoing);
            root.add(moduleNode);
        }
        tree.setModel(new javax.swing.tree.DefaultTreeModel(root));
        for (int i = 0; i < tree.getRowCount(); i++) tree.expandRow(i);
        if (!graph.getModules().isEmpty()) typesPanel.setModule(graph.getModules().get(0));
    }
}
