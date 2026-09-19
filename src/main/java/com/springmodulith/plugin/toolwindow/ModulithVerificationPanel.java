package com.springmodulith.plugin.toolwindow;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
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
import java.util.ArrayList;
import java.util.List;

public final class ModulithVerificationPanel extends JPanel {

    private final Project project;
    private final JLabel summary = new JLabel("Not verified");
    private final JPanel content = new JPanel();
    private final JButton verifyButton = new JButton("Verify Architecture");

    public ModulithVerificationPanel(@NotNull Project project) {
        this.project = project;

        setLayout(new BorderLayout(8, 8));
        setBorder(JBUI.Borders.empty(8));

        verifyButton.addActionListener(e -> verify());

        JPanel header = new JPanel(new BorderLayout(8, 8));
        header.add(summary, BorderLayout.CENTER);
        header.add(verifyButton, BorderLayout.EAST);

        add(header, BorderLayout.NORTH);

        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        add(new JBScrollPane(content), BorderLayout.CENTER);
    }

    public void verify() {
        verifyButton.setEnabled(false);
        summary.setText("Verifying architecture...");
        content.removeAll();
        revalidate();
        repaint();

        ProgressManager.getInstance().run(
                new Task.Backgroundable(
                        project,
                        "Verifying Spring Modulith Architecture",
                        true
                ) {
                    @Override
                    public void run(@NotNull ProgressIndicator indicator) {
                        indicator.setIndeterminate(true);

                        if (project.isDisposed()) {
                            return;
                        }

                        ModulithVerificationResult result =
                                ReadAction.compute(
                                        () -> new ModulithVerificationAnalyzer(project).verify()
                                );

                        if (project.isDisposed()) {
                            return;
                        }

                        ApplicationManager.getApplication().invokeLater(
                                () -> updateVerificationUI(result)
                        );
                    }
                }
        );
    }

    private void updateVerificationUI(
            @NotNull ModulithVerificationResult result
    ) {
        if (project.isDisposed()) {
            return;
        }

        verifyButton.setEnabled(true);

        summary.setText(
                result.violationCount() == 0
                        ? "Architecture verification passed"
                        : result.violationCount()
                        + " architecture violation(s) detected"
        );

        content.removeAll();

        addSection(
                "Dependency violations",
                result.dependencyViolations()
        );

        addSection(
                "Dependency cycles",
                result.cycles()
        );

        revalidate();
        repaint();
    }

    private void addSection(
            @NotNull String title,
            @NotNull List<?> values
    ) {
        JLabel label = new JLabel(
                title + " (" + values.size() + ")"
        );

        label.setBorder(
                BorderFactory.createEmptyBorder(
                        10,
                        4,
                        4,
                        4
                )
        );

        content.add(label);

        if (values.isEmpty()) {
            content.add(new JLabel("None"));
            return;
        }

        List<String> rows = new ArrayList<>();

        for (Object value : values) {
            if (value instanceof ModulithDependencyAnalysis analysis) {
                String namedInterface =
                        analysis.namedInterfaceName() == null
                                ? ""
                                : " [" + analysis.namedInterfaceName() + "]";

                rows.add(
                        analysis.sourcePackage()
                                + " → "
                                + analysis.targetPackage()
                                + " :: "
                                + analysis.targetClass().getQualifiedName()
                                + namedInterface
                );
            } else {
                rows.add(String.valueOf(value));
            }
        }

        JBList<String> list = new JBList<>(rows);

        list.setVisibleRowCount(
                Math.min(10, rows.size())
        );

        content.add(list);
    }
}