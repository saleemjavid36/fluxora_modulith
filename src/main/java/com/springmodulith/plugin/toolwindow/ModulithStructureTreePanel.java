package com.springmodulith.plugin.toolwindow;

import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiPackage;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import com.springmodulith.plugin.model.ModulithDependencyGraph;
import com.springmodulith.plugin.model.ModulithModule;
import com.springmodulith.plugin.model.NamedInterface;
import org.jetbrains.annotations.NotNull;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultListCellRenderer;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.JButton;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTree;
import javax.swing.SwingConstants;
import javax.swing.tree.DefaultMutableTreeNode;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Insets;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class ModulithStructureTreePanel extends JPanel {

    private static final Color ACCENT = new JBColor(
            new Color(66, 133, 244),
            new Color(88, 166, 255)
    );

    private static final Color SUCCESS = new JBColor(
            new Color(46, 160, 67),
            new Color(78, 201, 107)
    );

    private static final Color WARNING = new JBColor(
            new Color(220, 160, 40),
            new Color(255, 190, 70)
    );

    private static final Color ERROR = new JBColor(
            new Color(200, 60, 60),
            new Color(255, 100, 100)
    );

    private static final Color MUTED = new JBColor(
            new Color(130, 130, 130),
            new Color(150, 150, 150)
    );

    private static final Color INTERFACE_COLOR = new JBColor(
            new Color(180, 100, 220),
            new Color(205, 130, 245)
    );

    private static final Color PACKAGE_COLOR = new JBColor(
            new Color(100, 160, 210),
            new Color(120, 180, 235)
    );

    private static final Color PANEL_BORDER = new JBColor(
            new Color(65, 65, 65),
            new Color(75, 75, 75)
    );

    private final Project project;

    private final Tree structureTree;

    private final DefaultMutableTreeNode rootNode =
            new DefaultMutableTreeNode("Spring Modulith Modules");

    private final DefaultTreeModel treeModel =
            new DefaultTreeModel(rootNode);

    private final JPanel detailsPanel =
            new JPanel(new BorderLayout());

    private JSplitPane splitPane;

    private ModulithDependencyGraph graph;

    public ModulithStructureTreePanel(
            @NotNull Project project) {

        this.project = project;

        this.structureTree = new Tree(treeModel);

        createUi();
    }

    private void createUi() {

        setLayout(new BorderLayout());

        setBorder(
                JBUI.Borders.empty(8)
        );

        /*
         * =========================================================
         * LEFT PANEL
         * =========================================================
         */

        JPanel leftPanel =
                new JPanel(new BorderLayout());

        leftPanel.setBorder(
                BorderFactory.createLineBorder(
                        PANEL_BORDER
                )
        );

        JBLabel title =
                new JBLabel(
                        "Spring Modulith Modules"
                );

        title.setFont(
                title.getFont().deriveFont(
                        Font.BOLD,
                        16f
                )
        );

        title.setForeground(
                ACCENT
        );

        title.setBorder(
                JBUI.Borders.empty(
                        10,
                        12
                )
        );

        leftPanel.add(
                title,
                BorderLayout.NORTH
        );

        configureTree();

        JBScrollPane treeScrollPane =
                new JBScrollPane(
                        structureTree
                );

        treeScrollPane.setBorder(null);

        leftPanel.add(
                treeScrollPane,
                BorderLayout.CENTER
        );

        /*
         * =========================================================
         * RIGHT PANEL
         * =========================================================
         */

        detailsPanel.setBorder(
                BorderFactory.createLineBorder(
                        PANEL_BORDER
                )
        );

        showEmptyDetails();

        /*
         * =========================================================
         * SPLIT
         * =========================================================
         */

        splitPane =
                new JSplitPane(
                        JSplitPane.HORIZONTAL_SPLIT,
                        leftPanel,
                        detailsPanel
                );

        splitPane.setResizeWeight(0.40);
        splitPane.setDividerSize(3);
        splitPane.setBackground(UIUtil.getPanelBackground());
        splitPane.setBorder(null);

        add(
                splitPane,
                BorderLayout.CENTER
        );

        /*
         * Keep the detail area hidden until the user selects an item.
         * This preserves the existing detail content and selection
         * behavior while giving the structure tree the full width by
         * default.
         */
        hideDetailsPanel();
    }

    private void configureTree() {

        structureTree.setRootVisible(true);

        structureTree.setShowsRootHandles(true);

        structureTree.setRowHeight(25);

        structureTree.setBorder(
                JBUI.Borders.empty(6)
        );

        structureTree.setCellRenderer(
                new StructureTreeCellRenderer()
        );

        structureTree.addTreeSelectionListener(
                event -> {

                    TreePath path =
                            event.getPath();

                    if (path == null) {
                        return;
                    }

                    Object nodeObject =
                            ((DefaultMutableTreeNode)
                                    path.getLastPathComponent())
                                    .getUserObject();

                    showNodeDetails(nodeObject);
                }
        );

        structureTree.addMouseListener(
                new MouseAdapter() {

                    @Override
                    public void mouseClicked(
                            MouseEvent event) {

                        if (event.getClickCount() != 2) {
                            return;
                        }

                        TreePath path =
                                structureTree.getPathForLocation(
                                        event.getX(),
                                        event.getY()
                                );

                        if (path == null) {
                            return;
                        }

                        DefaultMutableTreeNode node =
                                (DefaultMutableTreeNode)
                                        path.getLastPathComponent();

                        openNode(node.getUserObject());
                    }
                }
        );
    }

    /*
     * =============================================================
     * LOAD GRAPH
     * =============================================================
     */

    public void setGraph(
            @NotNull ModulithDependencyGraph graph) {

        this.graph = graph;

        rootNode.removeAllChildren();

        /*
         * Keep the structure view intentionally close to IntelliJ's
         * Spring Modulith module structure: modules are the primary
         * nodes and only their Modulith metadata is shown underneath.
         * Package/class discovery is deliberately left out of this view;
         * those details are available from the selected module/details
         * panels and the graph view.
         */
        List<ModulithModule> modules =
                new ArrayList<>(graph.getModules());

        modules.sort(
                Comparator.comparing(
                        ModulithModule::getName,
                        String.CASE_INSENSITIVE_ORDER
                )
        );

        for (ModulithModule module : modules) {
            DefaultMutableTreeNode moduleNode =
                    new DefaultMutableTreeNode(
                            new ModuleNode(module)
                    );

            rootNode.add(moduleNode);
            buildModuleTree(moduleNode, module);
        }

        treeModel.reload();

        // Keep every module collapsed by default. The root remains expanded
        // so modules are visible without expanding their contents.
        if (structureTree.getRowCount() > 0) {
            structureTree.expandRow(0);
        }

        showEmptyDetails();
        hideDetailsPanel();
    }

    /*
     * =============================================================
     * MODULE TREE
     * =============================================================
     */

    private void buildModuleTree(
            @NotNull DefaultMutableTreeNode moduleNode,
            @NotNull ModulithModule module) {

        /*
         * Module id
         */
        moduleNode.add(
                new DefaultMutableTreeNode(
                        new ModuleIdNode(module.getName(), module)
                )
        );

        /*
         * Allowed dependencies
         */
        DefaultMutableTreeNode allowedDependenciesNode =
                new DefaultMutableTreeNode(
                        new CategoryNode(
                                "Allowed Dependencies",
                                CategoryType.ALLOWED_DEPENDENCIES,
                                module
                        )
                );

        moduleNode.add(allowedDependenciesNode);

        List<String> allowedDependencies =
                new ArrayList<>(module.getAllowedDependencies());

        allowedDependencies.sort(String.CASE_INSENSITIVE_ORDER);

        if (allowedDependencies.isEmpty()) {
            allowedDependenciesNode.add(
                    new DefaultMutableTreeNode(
                            new EmptyNode("None")
                    )
            );
        } else {
            for (String dependency : allowedDependencies) {
                allowedDependenciesNode.add(
                        new DefaultMutableTreeNode(
                                new AllowedDependencyNode(dependency, module)
                        )
                );
            }
        }

        /*
         * Named interfaces
         */
        DefaultMutableTreeNode interfacesNode =
                new DefaultMutableTreeNode(
                        new CategoryNode(
                                "Named Interfaces",
                                CategoryType.NAMED_INTERFACES,
                                module
                        )
                );

        moduleNode.add(interfacesNode);

        List<NamedInterface> namedInterfaces =
                new ArrayList<>(module.getNamedInterfaces());

        namedInterfaces.sort(
                Comparator.comparing(
                        NamedInterface::getName,
                        String.CASE_INSENSITIVE_ORDER
                )
        );

        if (namedInterfaces.isEmpty()) {
            interfacesNode.add(
                    new DefaultMutableTreeNode(
                            new EmptyNode("None")
                    )
            );
        } else {
            for (NamedInterface namedInterface : namedInterfaces) {
                DefaultMutableTreeNode interfaceNode =
                        new DefaultMutableTreeNode(
                                new NamedInterfaceNode(
                                        namedInterface,
                                        module
                                )
                        );

                interfacesNode.add(interfaceNode);

                List<String> typeNames =
                        new ArrayList<>(namedInterface.getTypeNames());

                typeNames.sort(String.CASE_INSENSITIVE_ORDER);

                for (String typeName : typeNames) {
                    interfaceNode.add(
                            new DefaultMutableTreeNode(
                                    new TypeNode(typeName, module)
                            )
                    );
                }
            }
        }
    }

    /*
     * =============================================================
     * FIND MODULE
     * =============================================================
     */

    private ModulithModule findModule(
            @NotNull String packageName) {

        if (graph == null) {
            return null;
        }

        for (ModulithModule module :
                graph.getModules()) {

            if (module.getPackageName()
                    .equals(packageName)) {

                return module;
            }
        }

        return null;
    }

    private ModulithModule findModuleForPackage(
            @NotNull String packageName) {

        if (graph == null) {
            return null;
        }

        ModulithModule result = null;

        for (ModulithModule module :
                graph.getModules()) {

            if (module.containsPackage(packageName)) {

                if (result == null
                        || module.getPackageName().length()
                        > result.getPackageName().length()) {

                    result = module;
                }
            }
        }

        return result;
    }

    /*
     * =============================================================
     * NODE DETAILS
     * =============================================================
     */

    private void showNodeDetails(
            Object nodeObject) {

        if (nodeObject instanceof EmptyNode) {
            return;
        }

        showDetailsPanel();

        if (nodeObject instanceof ModuleNode node) {

            showModuleDetails(
                    node.module()
            );

        } else if (nodeObject instanceof ModuleIdNode node) {

            showModuleDetails(node.module());

        } else if (nodeObject instanceof AllowedDependencyNode node) {

            showModuleDetails(node.module());

        } else if (nodeObject instanceof EmptyNode) {

            // Keep the current details panel unchanged for placeholder rows.

        } else if (nodeObject instanceof PackageNode node) {

            showPackageDetails(
                    node
            );

        } else if (nodeObject instanceof TypeNode node) {

            showTypeDetails(
                    node
            );

        } else if (nodeObject instanceof NamedInterfaceNode node) {

            showNamedInterfaceDetails(
                    node
            );

        } else if (nodeObject instanceof DependencyNode node) {

            showDependencyDetails(
                    node
            );

        } else if (nodeObject instanceof CategoryNode node) {

            showCategoryDetails(
                    node
            );
        }
    }

    /*
     * =============================================================
     * MODULE DETAILS
     * =============================================================
     */

    private void showModuleDetails(
            @NotNull ModulithModule module) {

        JPanel content =
                createDetailsContainer();

        addTitle(
                content,
                module.getName()
        );

        addSubtitle(
                content,
                module.getPackageName()
        );

        addSeparator(content);

        addValue(
                content,
                "Status",
                module.isOpen()
                        ? "OPEN"
                        : "CLOSED",
                module.isOpen()
                        ? SUCCESS
                        : WARNING
        );

        addValue(
                content,
                "Rules",
                module.isAllowedDependenciesConfigured()
                        ? "Explicit"
                        : "Implicit",
                module.isAllowedDependenciesConfigured()
                        ? WARNING
                        : MUTED
        );

        addSeparator(content);

        addSectionTitle(
                content,
                "Package"
        );

        addValue(
                content,
                null,
                module.getPackageName(),
                PACKAGE_COLOR
        );

        addSectionTitle(
                content,
                "Allowed Dependencies"
        );

        if (module.getAllowedDependencies().isEmpty()) {

            addValue(
                    content,
                    null,
                    "None",
                    MUTED
            );

        } else {

            for (String dependency :
                    module.getAllowedDependencies()) {

                addValue(
                        content,
                        null,
                        dependency,
                        SUCCESS
                );
            }
        }

        addSectionTitle(
                content,
                "Named Interfaces"
        );

        if (module.getNamedInterfaces().isEmpty()) {

            addValue(
                    content,
                    null,
                    "None",
                    MUTED
            );

        } else {

            for (NamedInterface namedInterface :
                    module.getNamedInterfaces()) {

                addValue(
                        content,
                        null,
                        namedInterface.getName()
                                + " — "
                                + namedInterface.getPackageName(),
                        INTERFACE_COLOR
                );
            }
        }

        addSectionTitle(
                content,
                "Outgoing Dependencies"
        );

        if (graph != null) {

            List<ModulithDependencyGraph.ModuleDependency>
                    outgoing =
                    graph.getOutgoingDependencies(
                            module
                    );

            if (outgoing.isEmpty()) {

                addValue(
                        content,
                        null,
                        "None",
                        MUTED
                );

            } else {

                for (ModulithDependencyGraph.ModuleDependency dependency :
                        outgoing) {

                    ModulithModule target =
                            findModule(
                                    dependency.targetPackage()
                            );

                    if (target == null) {
                        continue;
                    }

                    addValue(
                            content,
                            null,
                            "→ "
                                    + target.getName()
                                    + " ["
                                    + dependencyStatus(dependency)
                                    + "]",
                            dependencyColor(dependency)
                    );
                }
            }
        }

        addSectionTitle(
                content,
                "Incoming Dependencies"
        );

        if (graph != null) {

            List<ModulithDependencyGraph.ModuleDependency>
                    incoming =
                    graph.getIncomingDependencies(
                            module
                    );

            if (incoming.isEmpty()) {

                addValue(
                        content,
                        null,
                        "None",
                        MUTED
                );

            } else {

                for (ModulithDependencyGraph.ModuleDependency dependency :
                        incoming) {

                    ModulithModule source =
                            findModule(
                                    dependency.sourcePackage()
                            );

                    if (source == null) {
                        continue;
                    }

                    addValue(
                            content,
                            null,
                            "← "
                                    + source.getName()
                                    + " ["
                                    + dependencyStatus(dependency)
                                    + "]",
                            dependencyColor(dependency)
                    );
                }
            }
        }

        setDetailsContent(content);
    }

    /*
     * =============================================================
     * PACKAGE DETAILS
     * =============================================================
     */

    private void showPackageDetails(
            @NotNull PackageNode node) {

        JPanel content =
                createDetailsContainer();

        addTitle(
                content,
                "Package"
        );

        addSubtitle(
                content,
                node.packageName()
        );

        addSeparator(content);

        ModulithModule module =
                findModuleForPackage(
                        node.packageName()
                );

        if (module != null) {

            addValue(
                    content,
                    "Module",
                    module.getName(),
                    ACCENT
            );

            addValue(
                    content,
                    "Module Status",
                    module.isOpen()
                            ? "OPEN"
                            : "CLOSED",
                    module.isOpen()
                            ? SUCCESS
                            : WARNING
            );
        }

        PsiPackage psiPackage =
                JavaPsiFacade.getInstance(project)
                        .findPackage(
                                node.packageName()
                        );

        if (psiPackage != null) {

            GlobalSearchScope scope =
                    GlobalSearchScope.projectScope(project);

            PsiClass[] classes =
                    psiPackage.getClasses(scope);

            addSectionTitle(
                    content,
                    "Classes"
            );

            if (classes.length == 0) {

                addValue(
                        content,
                        null,
                        "None",
                        MUTED
                );

            } else {

                for (PsiClass clazz : classes) {

                    if (clazz.getQualifiedName() != null) {

                        addValue(
                                content,
                                null,
                                clazz.getName(),
                                ACCENT
                        );
                    }
                }
            }
        }

        setDetailsContent(content);
    }

    /*
     * =============================================================
     * TYPE DETAILS
     * =============================================================
     */

    private void showTypeDetails(
            @NotNull TypeNode node) {

        JPanel content =
                createDetailsContainer();

        String qualifiedName =
                node.qualifiedName();

        String simpleName =
                qualifiedName.substring(
                        qualifiedName.lastIndexOf('.') + 1
                );

        addTitle(
                content,
                simpleName
        );

        addSubtitle(
                content,
                qualifiedName
        );

        addSeparator(content);

        ModulithModule module =
                node.module();

        addValue(
                content,
                "Module",
                module.getName(),
                ACCENT
        );

        addValue(
                content,
                "Package",
                module.getPackageName(),
                PACKAGE_COLOR
        );

        PsiClass clazz =
                JavaPsiFacade.getInstance(project)
                        .findClass(
                                qualifiedName,
                                GlobalSearchScope.projectScope(project)
                        );

        if (clazz != null) {

            addValue(
                    content,
                    "Type",
                    getPsiType(clazz),
                    MUTED
            );

            PsiFile file =
                    clazz.getContainingFile();

            if (file != null
                    && file.getVirtualFile() != null) {

                addSectionTitle(
                        content,
                        "Location"
                );

                addValue(
                        content,
                        null,
                        file.getVirtualFile()
                                .getPresentableUrl(),
                        MUTED
                );
            }
        }

        addSectionTitle(
                content,
                "Actions"
        );

        JButton openButton =
                new JButton(
                        "Open " + simpleName
                );

        openButton.addActionListener(
                event ->
                        ModulithModuleNavigation
                                .openQualifiedType(
                                        project,
                                        qualifiedName
                                )
        );

        content.add(openButton);

        setDetailsContent(content);
    }

    /*
     * =============================================================
     * NAMED INTERFACE DETAILS
     * =============================================================
     */

    private void showNamedInterfaceDetails(
            @NotNull NamedInterfaceNode node) {

        NamedInterface namedInterface =
                node.namedInterface();

        JPanel content =
                createDetailsContainer();

        addTitle(
                content,
                namedInterface.getName()
        );

        addSubtitle(
                content,
                node.module().getName()
        );

        addSeparator(content);

        addValue(
                content,
                "Package",
                namedInterface.getPackageName(),
                PACKAGE_COLOR
        );

        addSectionTitle(
                content,
                "Exposed Types"
        );

        if (namedInterface.getTypeNames().isEmpty()) {

            addValue(
                    content,
                    null,
                    "None",
                    MUTED
            );

        } else {

            for (String type :
                    namedInterface.getTypeNames()) {

                addValue(
                        content,
                        null,
                        type,
                        INTERFACE_COLOR
                );
            }
        }

        setDetailsContent(content);
    }

    /*
     * =============================================================
     * DEPENDENCY DETAILS
     * =============================================================
     */

    private void showDependencyDetails(
            @NotNull DependencyNode node) {

        JPanel content =
                createDetailsContainer();

        ModulithModule source =
                node.source();

        ModulithModule target =
                node.target();

        ModulithDependencyGraph.ModuleDependency dependency =
                node.dependency();

        addTitle(
                content,
                source.getName()
                        + " → "
                        + target.getName()
        );

        addSubtitle(
                content,
                "Module Dependency"
        );

        addSeparator(content);

        addValue(
                content,
                "From",
                source.getPackageName(),
                PACKAGE_COLOR
        );

        addValue(
                content,
                "To",
                target.getPackageName(),
                PACKAGE_COLOR
        );

        addValue(
                content,
                "Status",
                dependencyStatus(dependency),
                dependencyColor(dependency)
        );

        addValue(
                content,
                "Cycle",
                graph != null
                        && graph.isCyclicEdge(dependency)
                        ? "Detected"
                        : "None",
                graph != null
                        && graph.isCyclicEdge(dependency)
                        ? ERROR
                        : SUCCESS
        );

        if (dependency.namedInterface() != null) {

            addValue(
                    content,
                    "Named Interface",
                    dependency.namedInterface(),
                    INTERFACE_COLOR
            );
        }

        addSectionTitle(
                content,
                "Actions"
        );

        JButton openButton =
                new JButton(
                        "Open Target Package"
                );

        openButton.addActionListener(
                event ->
                        ModulithModuleNavigation
                                .openPackage(
                                        project,
                                        target
                                )
        );

        content.add(openButton);

        setDetailsContent(content);
    }

    /*
     * =============================================================
     * CATEGORY DETAILS
     * =============================================================
     */

    private void showCategoryDetails(
            @NotNull CategoryNode node) {

        JPanel content =
                createDetailsContainer();

        addTitle(
                content,
                node.type().getTitle()
        );

        addSubtitle(
                content,
                node.module().getName()
        );

        addSeparator(content);

        switch (node.type()) {

            case ALLOWED_DEPENDENCIES -> {

                addValue(
                        content,
                        null,
                        "Dependencies explicitly allowed from this module.",
                        MUTED
                );
            }

            case NAMED_INTERFACES -> {

                addValue(
                        content,
                        null,
                        "Public contracts exposed by this module.",
                        MUTED
                );

            }

            case OUTGOING -> {

                addValue(
                        content,
                        null,
                        "Modules used by this module.",
                        MUTED
                );

            }

            case INCOMING -> {

                addValue(
                        content,
                        null,
                        "Modules that depend on this module.",
                        MUTED
                );
            }
        }

        setDetailsContent(content);
    }

    /*
     * =============================================================
     * DETAIL PANEL VISIBILITY
     * =============================================================
     */

    private void showDetailsPanel() {
        if (splitPane == null) {
            return;
        }

        detailsPanel.setVisible(true);
        splitPane.setDividerLocation(0.40);
        splitPane.revalidate();
        splitPane.repaint();
    }

    private void hideDetailsPanel() {
        if (splitPane == null) {
            return;
        }

        detailsPanel.setVisible(false);
        splitPane.setDividerLocation(1.0);
        splitPane.revalidate();
        splitPane.repaint();
    }

    /*
     * =============================================================
     * EMPTY DETAILS
     * =============================================================
     */

    private void showEmptyDetails() {

        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(JBUI.Borders.empty(28));
        panel.setOpaque(false);

        JPanel emptyCard = new JPanel();
        emptyCard.setOpaque(false);
        emptyCard.setLayout(new BoxLayout(emptyCard, BoxLayout.Y_AXIS));
        emptyCard.setBorder(JBUI.Borders.empty(22));

        JBLabel title = new JBLabel("No element selected");
        title.setAlignmentX(Component.CENTER_ALIGNMENT);
        title.setFont(title.getFont().deriveFont(Font.BOLD, 18f));
        title.setForeground(UIUtil.getLabelForeground());

        JBLabel hint = new JBLabel("Select a module or item from the structure tree");
        hint.setAlignmentX(Component.CENTER_ALIGNMENT);
        hint.setBorder(JBUI.Borders.emptyTop(7));
        hint.setFont(hint.getFont().deriveFont(Font.PLAIN, 12f));
        hint.setForeground(MUTED);

        emptyCard.add(title);
        emptyCard.add(hint);
        panel.add(emptyCard, BorderLayout.NORTH);

        setDetailsContent(panel);
    }

    /*
     * =============================================================
     * DETAILS HELPERS
     * =============================================================
     */

    private JPanel createDetailsContainer() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(JBUI.Borders.empty(18, 18, 24, 18));
        panel.setOpaque(false);
        return panel;
    }

    private void addTitle(
            @NotNull JPanel panel,
            @NotNull String text) {

        JPanel header = new JPanel(new BorderLayout(12, 0));
        header.setOpaque(false);
        header.setBorder(JBUI.Borders.emptyBottom(8));
        header.setAlignmentX(Component.LEFT_ALIGNMENT);

        JPanel accent = new JPanel();
        accent.setBackground(ACCENT);
        accent.setPreferredSize(new Dimension(3, 34));
        accent.setMinimumSize(new Dimension(3, 34));
        accent.setMaximumSize(new Dimension(3, 34));

        JBLabel label = new JBLabel(text);
        label.setFont(label.getFont().deriveFont(Font.BOLD, 20f));
        label.setForeground(UIUtil.getLabelForeground());

        header.add(accent, BorderLayout.WEST);
        header.add(label, BorderLayout.CENTER);
        panel.add(header);
    }

    private void addSubtitle(
            @NotNull JPanel panel,
            @NotNull String text) {

        JBLabel label = new JBLabel(wrapText(text, 390));
        label.setFont(label.getFont().deriveFont(Font.PLAIN, 12f));
        label.setForeground(MUTED);
        label.setBorder(JBUI.Borders.emptyLeft(15));
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(label);
    }

    private void addSeparator(@NotNull JPanel panel) {
        panel.add(Box.createVerticalStrut(14));

        JPanel separator = new JPanel();
        separator.setMaximumSize(new Dimension(Integer.MAX_VALUE, 1));
        separator.setBackground(PANEL_BORDER);
        separator.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(separator);

        panel.add(Box.createVerticalStrut(4));
    }

    private void addSectionTitle(
            @NotNull JPanel panel,
            @NotNull String title) {

        panel.add(Box.createVerticalStrut(12));

        JPanel header = new JPanel(new BorderLayout(8, 0));
        header.setOpaque(false);
        header.setAlignmentX(Component.LEFT_ALIGNMENT);
        header.setBorder(JBUI.Borders.empty(5, 0, 4, 0));

        JPanel marker = new JPanel();
        marker.setBackground(ACCENT);
        marker.setPreferredSize(new Dimension(3, 16));
        marker.setMinimumSize(new Dimension(3, 16));
        marker.setMaximumSize(new Dimension(3, 16));

        JBLabel label = new JBLabel(title);
        label.setFont(label.getFont().deriveFont(Font.BOLD, 13f));
        label.setForeground(UIUtil.getLabelForeground());

        header.add(marker, BorderLayout.WEST);
        header.add(label, BorderLayout.CENTER);
        panel.add(header);
    }

    private void addValue(
            @NotNull JPanel panel,
            String label,
            @NotNull String value,
            @NotNull Color color) {

        JPanel row = new JPanel(new BorderLayout(12, 0));
        row.setOpaque(true);
        row.setBackground(UIUtil.getPanelBackground());
        row.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(PANEL_BORDER),
                JBUI.Borders.empty(7, 9)
        ));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);

        if (label != null) {
            JBLabel labelComponent = new JBLabel(label);
            labelComponent.setForeground(MUTED);
            labelComponent.setFont(labelComponent.getFont().deriveFont(Font.PLAIN, 11.5f));
            labelComponent.setPreferredSize(new Dimension(108, 22));
            row.add(labelComponent, BorderLayout.WEST);
        }

        JBLabel valueComponent = new JBLabel(wrapText(value, 340));
        valueComponent.setForeground(color);
        valueComponent.setFont(valueComponent.getFont().deriveFont(Font.PLAIN, 12f));
        row.add(valueComponent, BorderLayout.CENTER);

        panel.add(row);
        panel.add(Box.createVerticalStrut(5));
    }

    private String wrapText(@NotNull String text, int width) {
        return "<html><body style='width:" + width + "px'>"
                + escapeHtml(text)
                + "</body></html>";
    }

    private String escapeHtml(@NotNull String value) {
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    private void setDetailsContent(@NotNull JPanel content) {
        detailsPanel.removeAll();

        JScrollPane scrollPane = new JBScrollPane(content);
        scrollPane.setBorder(null);
        scrollPane.setOpaque(false);
        scrollPane.getViewport().setOpaque(false);
        scrollPane.getVerticalScrollBar().setUnitIncrement(14);
        scrollPane.getHorizontalScrollBar().setUnitIncrement(14);

        detailsPanel.add(scrollPane, BorderLayout.CENTER);
        detailsPanel.revalidate();
        detailsPanel.repaint();
    }

    /*
     * =============================================================
     * DEPENDENCY HELPERS
     * =============================================================
     */

    private  String dependencyStatus(
            @NotNull ModulithDependencyGraph.ModuleDependency dependency) {

        String status;

        if (dependency.isForbidden()) {
            status = "FORBIDDEN";
        } else if (dependency.isNamedInterface()) {
            status = "NAMED INTERFACE";
        } else {
            status = "ALLOWED";
        }

        if (dependency.isApiViolation()) {
            status += " + API VIOLATION";
        }

        if (graph != null && graph.isCyclicEdge(dependency)) {
            status += " + CYCLE";
        }

        return status;
    }


    private  Color dependencyColor(
            @NotNull ModulithDependencyGraph.ModuleDependency dependency) {

        if (dependency.isForbidden()) {
            return ERROR;
        }

        if (dependency.isNamedInterface()) {
            return WARNING;
        }

        if (dependency.isApiViolation()) {
            return ERROR;
        }

        return SUCCESS;
    }

    /*
     * =============================================================
     * PSI TYPE
     * =============================================================
     */

    private String getPsiType(
            @NotNull PsiClass clazz) {

        if (clazz.isInterface()) {
            return "Interface";
        }

        if (clazz.isEnum()) {
            return "Enum";
        }

        if (clazz.isAnnotationType()) {
            return "Annotation";
        }

        return "Class";
    }

    /*
     * =============================================================
     * OPEN TREE NODE
     * =============================================================
     */

    private void openNode(
            Object nodeObject) {

        if (nodeObject instanceof ModuleNode node) {

            ModulithModuleNavigation.openPackage(
                    project,
                    node.module()
            );

        } else if (nodeObject instanceof PackageNode node) {

            openPackage(
                    node.packageName()
            );

        } else if (nodeObject instanceof TypeNode node) {

            ModulithModuleNavigation.openQualifiedType(
                    project,
                    node.qualifiedName()
            );
        }
    }

    private void openPackage(
            @NotNull String packageName) {

        PsiPackage psiPackage =
                JavaPsiFacade.getInstance(project)
                        .findPackage(packageName);

        if (psiPackage == null) {
            return;
        }

        if (psiPackage.getDirectories().length == 0) {
            return;
        }

        var directory =
                psiPackage.getDirectories()[0];

        PsiFile packageInfo =
                directory.findFile(
                        "package-info.java"
                );

        if (packageInfo != null
                && packageInfo.getVirtualFile() != null) {

            FileEditorManager.getInstance(project)
                    .openFile(
                            packageInfo.getVirtualFile(),
                            true
                    );

            return;
        }

        for (PsiFile file :
                directory.getFiles()) {

            if ("java".equalsIgnoreCase(
                    file.getVirtualFile()
                            .getExtension()
            )) {

                FileEditorManager.getInstance(project)
                        .openFile(
                                file.getVirtualFile(),
                                true
                        );

                return;
            }
        }
    }

    /*
     * =============================================================
     * REFRESH
     * =============================================================
     */

    public void refresh() {

        if (graph == null) {

            rootNode.removeAllChildren();

            treeModel.reload();

            showEmptyDetails();
            hideDetailsPanel();

            return;
        }

        setGraph(graph);
    }

    /*
     * =============================================================
     * TREE RENDERER
     * =============================================================
     */

    private final class StructureTreeCellRenderer
            extends DefaultTreeCellRenderer {

        @Override
        public Component getTreeCellRendererComponent(
                JTree tree,
                Object value,
                boolean selected,
                boolean expanded,
                boolean leaf,
                int row,
                boolean hasFocus) {

            Component component =
                    super.getTreeCellRendererComponent(
                            tree,
                            value,
                            selected,
                            expanded,
                            leaf,
                            row,
                            hasFocus
                    );

            if (!(value instanceof DefaultMutableTreeNode node)) {
                return component;
            }

            Object object =
                    node.getUserObject();

            component.setFont(
                    component.getFont().deriveFont(
                            Font.PLAIN,
                            12.5f
                    )
            );

            if (object instanceof ModuleNode moduleNode) {

                setText(
                        moduleNode.module().getName()
                                + "  "
                                + moduleNode.module().getPackageName()
                );

                component.setFont(
                        component.getFont().deriveFont(
                                Font.BOLD,
                                13f
                        )
                );

                setForeground(
                        selected
                                ? getTextSelectionColor()
                                : UIUtil.getLabelForeground()
                );

            } else if (object instanceof ModuleIdNode) {

                setForeground(
                        selected
                                ? getTextSelectionColor()
                                : MUTED
                );

            } else if (object instanceof AllowedDependencyNode) {

                setForeground(
                        selected
                                ? getTextSelectionColor()
                                : UIUtil.getLabelForeground()
                );

            } else if (object instanceof EmptyNode) {

                setForeground(
                        selected
                                ? getTextSelectionColor()
                                : MUTED
                );

            } else if (object instanceof PackageNode) {

                setForeground(
                        selected
                                ? getTextSelectionColor()
                                : PACKAGE_COLOR
                );

            } else if (object instanceof NamedInterfaceNode) {

                setText(
                        ((NamedInterfaceNode) object).namedInterface().getName()
                                + "  "
                                + ((NamedInterfaceNode) object).namedInterface().getPackageName()
                );

                setForeground(
                        selected
                                ? getTextSelectionColor()
                                : INTERFACE_COLOR
                );

            } else if (object instanceof DependencyNode dependencyNode) {

                setText(
                        dependencyNode.source().getName()
                                + " → "
                                + dependencyNode.target().getName()
                                + " ["
                                + dependencyStatus(
                                dependencyNode.dependency()
                        )
                                + "]"
                );

                setForeground(
                        selected
                                ? getTextSelectionColor()
                                : dependencyColor(
                                dependencyNode.dependency()
                        )
                );

            } else if (object instanceof CategoryNode categoryNode) {

                setText(
                        categoryNode.type().getTitle()
                );

                component.setFont(
                        component.getFont().deriveFont(
                                Font.BOLD,
                                12f
                        )
                );

                setForeground(
                        selected
                                ? getTextSelectionColor()
                                : UIUtil.getLabelForeground()
                );

            } else if (object instanceof TypeNode) {

                setForeground(
                        selected
                                ? getTextSelectionColor()
                                : UIUtil.getLabelForeground()
                );
            }

            return component;
        }
    }

    /*
     * =============================================================
     * TREE NODE MODELS
     * =============================================================
     */

    private record ModuleNode(
            ModulithModule module) {
    }

    private record ModuleIdNode(
            String id,
            ModulithModule module) {

        @Override
        public String toString() {
            return "Id: " + id;
        }
    }

    private record AllowedDependencyNode(
            String dependency,
            ModulithModule module) {

        @Override
        public String toString() {
            return dependency;
        }
    }

    private record EmptyNode(
            String text) {

        @Override
        public String toString() {
            return text;
        }
    }

    private record PackageNode(
            String packageName,
            String displayName) {

        @Override
        public String toString() {

            int lastDot =
                    displayName.lastIndexOf('.');

            if (lastDot >= 0) {
                return displayName.substring(
                        lastDot + 1
                );
            }

            return displayName;
        }
    }

    private record TypeNode(
            String qualifiedName,
            ModulithModule module) {

        @Override
        public String toString() {

            int lastDot =
                    qualifiedName.lastIndexOf('.');

            if (lastDot >= 0) {
                return qualifiedName.substring(
                        lastDot + 1
                );
            }

            return qualifiedName;
        }
    }

    private record NamedInterfaceNode(
            NamedInterface namedInterface,
            ModulithModule module) {

        @Override
        public String toString() {

            return namedInterface.getName();
        }
    }

    private record DependencyNode(
            ModulithModule source,
            ModulithModule target,
            ModulithDependencyGraph.ModuleDependency dependency) {

        @Override
        public String toString() {

            return source.getName()
                    + " → "
                    + target.getName();
        }
    }

    private record CategoryNode(
            String title,
            CategoryType type,
            ModulithModule module) {

        @Override
        public String toString() {

            return title;
        }
    }

    private enum CategoryType {

        ALLOWED_DEPENDENCIES(
                "Allowed Dependencies"
        ),

        NAMED_INTERFACES(
                "Named Interfaces"
        ),

        OUTGOING(
                "Outgoing Dependencies"
        ),

        INCOMING(
                "Incoming Dependencies"
        );

        private final String title;

        CategoryType(
                String title) {

            this.title = title;
        }

        public String getTitle() {

            return title;
        }
    }
}