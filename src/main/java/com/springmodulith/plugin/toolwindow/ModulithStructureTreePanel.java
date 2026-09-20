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

        JSplitPane splitPane =
                new JSplitPane(
                        JSplitPane.HORIZONTAL_SPLIT,
                        leftPanel,
                        detailsPanel
                );

        splitPane.setResizeWeight(0.40);

        splitPane.setDividerLocation(0.40);

        splitPane.setBorder(null);

        add(
                splitPane,
                BorderLayout.CENTER
        );
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

        List<ModulithModule> modules =
                new ArrayList<>(
                        graph.getModules()
                );

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

            buildModuleTree(
                    moduleNode,
                    module
            );
        }

        treeModel.reload();

        structureTree.expandRow(0);

        showEmptyDetails();
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
         * Package hierarchy
         */
        DefaultMutableTreeNode packageRoot =
                new DefaultMutableTreeNode(
                        new PackageNode(
                                module.getPackageName(),
                                module.getPackageName()
                        )
                );

        moduleNode.add(packageRoot);

        buildPackageTree(
                packageRoot,
                module.getPackageName(),
                module
        );

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

        for (NamedInterface namedInterface :
                module.getNamedInterfaces()) {

            DefaultMutableTreeNode interfaceNode =
                    new DefaultMutableTreeNode(
                            new NamedInterfaceNode(
                                    namedInterface,
                                    module
                            )
                    );

            interfacesNode.add(interfaceNode);

            for (String typeName :
                    namedInterface.getTypeNames()) {

                interfaceNode.add(
                        new DefaultMutableTreeNode(
                                new TypeNode(
                                        typeName,
                                        module
                                )
                        )
                );
            }
        }

        /*
         * Outgoing dependencies
         */
        DefaultMutableTreeNode outgoingNode =
                new DefaultMutableTreeNode(
                        new CategoryNode(
                                "Outgoing Dependencies",
                                CategoryType.OUTGOING,
                                module
                        )
                );

        moduleNode.add(outgoingNode);

        for (ModulithDependencyGraph.ModuleDependency dependency :
                graph.getOutgoingDependencies(module)) {

            ModulithModule target =
                    findModule(
                            dependency.targetPackage()
                    );

            if (target == null) {
                continue;
            }

            outgoingNode.add(
                    new DefaultMutableTreeNode(
                            new DependencyNode(
                                    module,
                                    target,
                                    dependency
                            )
                    )
            );
        }

        /*
         * Incoming dependencies
         */
        DefaultMutableTreeNode incomingNode =
                new DefaultMutableTreeNode(
                        new CategoryNode(
                                "Incoming Dependencies",
                                CategoryType.INCOMING,
                                module
                        )
                );

        moduleNode.add(incomingNode);

        for (ModulithDependencyGraph.ModuleDependency dependency :
                graph.getIncomingDependencies(module)) {

            ModulithModule source =
                    findModule(
                            dependency.sourcePackage()
                    );

            if (source == null) {
                continue;
            }

            incomingNode.add(
                    new DefaultMutableTreeNode(
                            new DependencyNode(
                                    source,
                                    module,
                                    dependency
                            )
                    )
            );
        }
    }

    /*
     * =============================================================
     * PACKAGE TREE
     * =============================================================
     */

    private void buildPackageTree(
            @NotNull DefaultMutableTreeNode parentNode,
            @NotNull String packageName,
            @NotNull ModulithModule module) {

        PsiPackage psiPackage =
                JavaPsiFacade.getInstance(project)
                        .findPackage(packageName);

        if (psiPackage == null) {
            return;
        }

        GlobalSearchScope scope =
                GlobalSearchScope.projectScope(project);

        /*
         * Classes directly inside this package
         */
        PsiClass[] classes =
                psiPackage.getClasses(scope);

        List<PsiClass> sortedClasses =
                new ArrayList<>(
                        List.of(classes)
                );

        sortedClasses.sort(
                Comparator.comparing(
                        clazz -> {

                            String name =
                                    clazz.getQualifiedName();

                            return name == null
                                    ? clazz.getName()
                                    : name;

                        },
                        Comparator.nullsLast(
                                String.CASE_INSENSITIVE_ORDER
                        )
                )
        );

        for (PsiClass clazz :
                sortedClasses) {

            if (clazz.getQualifiedName() == null) {
                continue;
            }

            parentNode.add(
                    new DefaultMutableTreeNode(
                            new TypeNode(
                                    clazz.getQualifiedName(),
                                    module
                            )
                    )
            );
        }

        /*
         * Child packages
         */
        PsiPackage[] subPackages =
                psiPackage.getSubPackages(scope);

        List<PsiPackage> sortedPackages =
                new ArrayList<>(
                        List.of(subPackages)
                );

        sortedPackages.sort(
                Comparator.comparing(
                        PsiPackage::getQualifiedName,
                        String.CASE_INSENSITIVE_ORDER
                )
        );

        for (PsiPackage child :
                sortedPackages) {

            String childName =
                    child.getQualifiedName();

            if (childName == null) {
                continue;
            }

            /*
             * Do not walk into another Modulith module.
             */
            ModulithModule childModule =
                    findModuleForPackage(
                            childName
                    );

            if (childModule != null
                    && childModule != module) {

                continue;
            }

            DefaultMutableTreeNode childNode =
                    new DefaultMutableTreeNode(
                            new PackageNode(
                                    childName,
                                    childName
                            )
                    );

            parentNode.add(childNode);

            buildPackageTree(
                    childNode,
                    childName,
                    module
            );
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

        if (nodeObject instanceof ModuleNode node) {

            showModuleDetails(
                    node.module()
            );

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
     * EMPTY DETAILS
     * =============================================================
     */

    private void showEmptyDetails() {

        JPanel panel =
                new JPanel(
                        new BorderLayout()
                );

        panel.setBorder(
                JBUI.Borders.empty(24)
        );

        JBLabel title =
                new JBLabel(
                        "No element selected"
                );

        title.setHorizontalAlignment(
                SwingConstants.CENTER
        );

        title.setFont(
                title.getFont().deriveFont(
                        Font.BOLD,
                        18f
                )
        );

        title.setForeground(
                MUTED
        );

        panel.add(
                title,
                BorderLayout.CENTER
        );

        setDetailsContent(panel);
    }

    /*
     * =============================================================
     * DETAILS HELPERS
     * =============================================================
     */

    private JPanel createDetailsContainer() {

        JPanel panel =
                new JPanel();

        panel.setLayout(
                new BoxLayout(
                        panel,
                        BoxLayout.Y_AXIS
                )
        );

        panel.setBorder(
                JBUI.Borders.empty(18)
        );

        return panel;
    }

    private void addTitle(
            @NotNull JPanel panel,
            @NotNull String text) {

        JBLabel label =
                new JBLabel(text);

        label.setFont(
                label.getFont().deriveFont(
                        Font.BOLD,
                        20f
                )
        );

        label.setForeground(
                ACCENT
        );

        label.setBorder(
                JBUI.Borders.emptyBottom(4)
        );

        panel.add(label);
    }

    private void addSubtitle(
            @NotNull JPanel panel,
            @NotNull String text) {

        JBLabel label =
                new JBLabel(text);

        label.setFont(
                label.getFont().deriveFont(
                        Font.PLAIN,
                        12f
                )
        );

        label.setForeground(
                MUTED
        );

        panel.add(label);
    }

    private void addSeparator(
            @NotNull JPanel panel) {

        panel.add(
                Box.createVerticalStrut(12)
        );

        JPanel separator = new JPanel();

        separator.setMaximumSize(
                new Dimension(
                        Integer.MAX_VALUE,
                        1
                )
        );

        separator.setBackground(
                PANEL_BORDER
        );

        panel.add(separator);

        panel.add(
                Box.createVerticalStrut(6)
        );
    }

    private void addSectionTitle(
            @NotNull JPanel panel,
            @NotNull String title) {

        panel.add(
                Box.createVerticalStrut(14)
        );

        JBLabel label =
                new JBLabel(title);

        label.setFont(
                label.getFont().deriveFont(
                        Font.BOLD,
                        13f
                )
        );

        label.setForeground(
                UIUtil.getLabelForeground()
        );

        panel.add(label);
    }

    private void addValue(
            @NotNull JPanel panel,
            String label,
            @NotNull String value,
            @NotNull Color color) {

        JPanel row =
                new JPanel(
                        new BorderLayout()
                );

        row.setOpaque(false);

        row.setBorder(
                JBUI.Borders.empty(
                        3,
                        0
                )
        );

        if (label != null) {

            JBLabel labelComponent =
                    new JBLabel(
                            label
                                    + ": "
                    );

            labelComponent.setForeground(
                    MUTED
            );

            row.add(
                    labelComponent,
                    BorderLayout.WEST
            );
        }

        JBLabel valueComponent =
                new JBLabel(value);

        valueComponent.setForeground(color);

        row.add(
                valueComponent,
                BorderLayout.CENTER
        );

        panel.add(row);
    }

    private void setDetailsContent(
            @NotNull JPanel content) {

        detailsPanel.removeAll();

        JScrollPane scrollPane =
                new JBScrollPane(content);

        scrollPane.setBorder(null);

        detailsPanel.add(
                scrollPane,
                BorderLayout.CENTER
        );

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
                                + "    "
                                + (
                                moduleNode.module().isOpen()
                                        ? "OPEN"
                                        : "CLOSED"
                        )
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
                                : ACCENT
                );

            } else if (object instanceof PackageNode) {

                setForeground(
                        selected
                                ? getTextSelectionColor()
                                : PACKAGE_COLOR
                );

            } else if (object instanceof NamedInterfaceNode) {

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