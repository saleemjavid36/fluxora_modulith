package com.springmodulith.plugin.toolwindow;

import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBList;
import com.intellij.util.ui.JBUI;
import com.springmodulith.plugin.model.ModulithModule;
import com.springmodulith.plugin.model.NamedInterface;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.util.ArrayList;
import java.util.List;

public final class ModulithModuleDetailsPanel extends JPanel {

    private final JBLabel moduleName = new JBLabel();
    private final JBLabel packageName = new JBLabel();
    private final JBLabel status = new JBLabel();

    private final JPanel content = new JPanel();

    public ModulithModuleDetailsPanel() {
        setLayout(new BorderLayout());
        setBorder(JBUI.Borders.empty(12));

        JPanel header = new JPanel();
        header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));

        moduleName.setFont(
                moduleName.getFont().deriveFont(18.0f)
        );

        packageName.setBorder(
                JBUI.Borders.emptyTop(4)
        );

        status.setBorder(
                JBUI.Borders.emptyTop(4)
        );

        header.add(moduleName);
        header.add(packageName);
        header.add(status);

        add(header, BorderLayout.NORTH);

        content.setLayout(
                new BoxLayout(content, BoxLayout.Y_AXIS)
        );

        add(content, BorderLayout.CENTER);

        clear();
    }

    public void showModule(
            @Nullable ModulithModule module) {

        if (module == null) {
            clear();
            return;
        }

        moduleName.setText(module.getName());

        packageName.setText(
                "Package: " + module.getPackageName()
        );

        status.setText(
                "Status: " +
                        (module.isOpen() ? "OPEN" : "CLOSED")
        );

        content.removeAll();

        addSection(
                "Allowed dependencies",
                getAllowedDependencies(module)
        );

        addSection(
                "Named interfaces",
                getNamedInterfaces(module)
        );

        revalidate();
        repaint();
    }

    private List<String> getAllowedDependencies(
            @NotNull ModulithModule module) {

        return new ArrayList<>(
                module.getAllowedDependencies()
        );
    }

    private List<String> getNamedInterfaces(
            @NotNull ModulithModule module) {

        List<String> result = new ArrayList<>();

        for (NamedInterface namedInterface :
                module.getNamedInterfaces()) {

            result.add(
                    namedInterface.getName()
            );
        }

        return result;
    }

    private void addSection(
            @NotNull String title,
            @NotNull List<String> values) {

        content.add(
                Box.createVerticalStrut(16)
        );

        JLabel titleLabel =
                new JBLabel(title);

        titleLabel.setFont(
                titleLabel.getFont().deriveFont(
                        14.0f
                )
        );

        content.add(titleLabel);

        if (values.isEmpty()) {
            content.add(
                    new JBLabel("None")
            );
        } else {
            JBList<String> list =
                    new JBList<>(values);

            list.setVisibleRowCount(
                    Math.min(values.size(), 8)
            );

            list.setBorder(
                    BorderFactory.createEmptyBorder(
                            4,
                            8,
                            4,
                            8
                    )
            );

            content.add(list);
        }
    }

    public void clear() {

        moduleName.setText(
                "No module selected"
        );

        packageName.setText("");

        status.setText("");

        content.removeAll();

        revalidate();
        repaint();
    }
}