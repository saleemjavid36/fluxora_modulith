package com.springmodulith.plugin.toolwindow;

import com.intellij.ui.components.JBList;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.ui.JBUI;
import com.springmodulith.plugin.analyzer.ModulithVerificationAnalyzer;
import com.springmodulith.plugin.model.ModulithDependencyAnalysis;
import com.springmodulith.plugin.model.ModulithVerificationResult;
import org.jetbrains.annotations.NotNull;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;

public final class ModulithVerificationPanel extends JPanel {
    private final com.intellij.openapi.project.Project project;
    private final JLabel summary = new JLabel("Not verified");
    private final JPanel content = new JPanel();

    public ModulithVerificationPanel(@NotNull com.intellij.openapi.project.Project project) {
        this.project = project;
        setLayout(new BorderLayout(8, 8));
        setBorder(JBUI.Borders.empty(8));
        JButton verify = new JButton("Verify Architecture");
        verify.addActionListener(e -> verify());
        JPanel header = new JPanel(new BorderLayout(8, 8));
        header.add(summary, BorderLayout.CENTER);
        header.add(verify, BorderLayout.EAST);
        add(header, BorderLayout.NORTH);
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        add(new JBScrollPane(content), BorderLayout.CENTER);
    }

    public void verify() {
        ModulithVerificationResult result = new ModulithVerificationAnalyzer(project).verify();
        summary.setText(result.violationCount() == 0
                ? "Architecture verification passed"
                : result.violationCount() + " architecture violation(s) detected");
        content.removeAll();
        addSection("Dependency violations", result.dependencyViolations());
        addSection("Dependency cycles", result.cycles());
        revalidate();
        repaint();
    }

    private void addSection(String title, List<?> values) {
        JLabel label = new JLabel(title + " (" + values.size() + ")");
        label.setBorder(BorderFactory.createEmptyBorder(10, 4, 4, 4));
        content.add(label);
        if (values.isEmpty()) {
            content.add(new JLabel("None"));
            return;
        }
        List<String> rows = new ArrayList<>();
        for (Object value : values) {
            if (value instanceof ModulithDependencyAnalysis analysis) {
                rows.add(analysis.sourcePackage() + " → " + analysis.targetPackage() +
                        " :: " + analysis.targetClass().getQualifiedName() +
                        (analysis.namedInterfaceName() == null ? "" : " [" + analysis.namedInterfaceName() + "]"));
            } else rows.add(String.valueOf(value));
        }
        JBList<String> list = new JBList<>(rows);
        list.setVisibleRowCount(Math.min(10, rows.size()));
        content.add(list);
    }
}
