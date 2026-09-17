package com.springmodulith.plugin.toolwindow;

import com.intellij.ui.components.JBList;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.ui.JBUI;
import com.springmodulith.plugin.model.ModulithModule;
import com.springmodulith.plugin.model.NamedInterface;
import org.jetbrains.annotations.NotNull;

import javax.swing.DefaultListModel;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSplitPane;
import java.awt.BorderLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

public final class ModulithNamedInterfaceTypesPanel extends JPanel {
    private final com.intellij.openapi.project.Project project;
    private final JBList<String> interfaces = new JBList<>();
    private final JBList<String> types = new JBList<>();
    private java.util.List<NamedInterface> current = java.util.List.of();
    private ModulithModule module;

    public ModulithNamedInterfaceTypesPanel(@NotNull com.intellij.openapi.project.Project project) {
        this.project = project;
        setLayout(new BorderLayout(8, 8));
        setBorder(JBUI.Borders.empty(8));
        JPanel left = new JPanel(new BorderLayout(4, 4));
        left.add(new JLabel("Named interfaces"), BorderLayout.NORTH);
        left.add(new JBScrollPane(interfaces), BorderLayout.CENTER);
        JPanel right = new JPanel(new BorderLayout(4, 4));
        right.add(new JLabel("Exposed types"), BorderLayout.NORTH);
        right.add(new JBScrollPane(types), BorderLayout.CENTER);
        add(new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, left, right), BorderLayout.CENTER);
        interfaces.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) showTypes(interfaces.getSelectedIndex());
        });
        types.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() != 2) return;
                String type = types.getSelectedValue();
                if (type != null) ModulithModuleNavigation.openQualifiedType(project, type);
            }
        });
    }

    public void setModule(@NotNull ModulithModule module) {
        this.module = module;
        this.current = module.getNamedInterfaces();
        DefaultListModel<String> model = new DefaultListModel<>();
        for (NamedInterface namedInterface : current) model.addElement(namedInterface.getName() + " — " + namedInterface.getPackageName());
        interfaces.setModel(model);
        if (!current.isEmpty()) interfaces.setSelectedIndex(0); else types.setModel(new DefaultListModel<>());
    }

    private void showTypes(int index) {
        DefaultListModel<String> model = new DefaultListModel<>();
        if (index >= 0 && index < current.size()) {
            for (String type : current.get(index).getTypeNames()) model.addElement(type);
        }
        types.setModel(model);
    }
}
