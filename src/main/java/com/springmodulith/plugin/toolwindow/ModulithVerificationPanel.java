package com.springmodulith.plugin.toolwindow;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBList;
import com.intellij.ui.components.JBPanel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import com.springmodulith.plugin.analyzer.ModulithVerificationAnalyzer;
import com.springmodulith.plugin.model.ModulithDependencyAnalysis;
import com.springmodulith.plugin.model.ModulithVerificationResult;
import org.jetbrains.annotations.NotNull;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.ListCellRenderer;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Insets;
import java.util.ArrayList;
import java.util.List;

public final class ModulithVerificationPanel extends JPanel {

    private static final Color ERROR_COLOR =
            new JBColor(new Color(0xF05D5E), new Color(0xFF7B72));

    private static final Color WARNING_COLOR =
            new JBColor(new Color(0xC98A00), new Color(0xE5B44B));

    private static final Color SUCCESS_COLOR =
            new JBColor(new Color(0x2E9B61), new Color(0x63C174));

    private final Project project;
    private final JLabel summary = new JBLabel("Not verified");
    private final JLabel statusBadge = new JBLabel();
    private final JPanel content = new JBPanel<>(new BorderLayout());
    private final JButton verifyButton = new JButton("Verify Architecture");

    private boolean hasVerificationResult;

    public ModulithVerificationPanel(@NotNull Project project) {
        this.project = project;

        setLayout(new BorderLayout(0, 10));
        setBorder(JBUI.Borders.empty(8));

        verifyButton.setToolTipText(
                "Run a complete Spring Modulith architecture verification"
        );
        verifyButton.addActionListener(e -> verify());

        JPanel header = new JPanel(new BorderLayout(12, 0));
        header.setOpaque(false);
        header.setBorder(
                BorderFactory.createCompoundBorder(
                        JBUI.Borders.emptyBottom(2),
                        JBUI.Borders.empty(2, 4)
                )
        );

        JPanel summaryPanel = new JPanel();
        summaryPanel.setOpaque(false);
        summaryPanel.setLayout(new BoxLayout(summaryPanel, BoxLayout.Y_AXIS));

        summary.setFont(
                summary.getFont().deriveFont(Font.BOLD, 15.0f)
        );

        statusBadge.setFont(
                statusBadge.getFont().deriveFont(Font.PLAIN, 12.0f)
        );
        statusBadge.setBorder(JBUI.Borders.emptyTop(3));

        summaryPanel.add(summary);
        summaryPanel.add(statusBadge);

        header.add(summaryPanel, BorderLayout.CENTER);
        header.add(verifyButton, BorderLayout.EAST);

        add(header, BorderLayout.NORTH);

        content.setOpaque(false);
        content.setBorder(
                BorderFactory.createCompoundBorder(
                        JBUI.Borders.customLine(
                                UIUtil.getBoundsColor(),
                                1
                        ),
                        JBUI.Borders.empty(12)
                )
        );

        JBScrollPane scrollPane = new JBScrollPane(content);
        scrollPane.setBorder(BorderFactory.createEmptyBorder());
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);
        add(scrollPane, BorderLayout.CENTER);

        showNotVerified();
    }

    public boolean hasVerificationResult() {
        return hasVerificationResult;
    }

    public void verify() {
        verifyButton.setEnabled(false);
        summary.setForeground(UIUtil.getLabelForeground());
        summary.setText("Verifying architecture...");
        statusBadge.setText("Analyzing module boundaries and dependencies");
        statusBadge.setForeground(UIUtil.getContextHelpForeground());

        content.removeAll();
        content.add(createProgressState(), BorderLayout.NORTH);
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

                    @Override
                    public void onThrowable(@NotNull Throwable error) {
                        ApplicationManager.getApplication().invokeLater(
                                () -> showVerificationError(error)
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

        hasVerificationResult = true;
        verifyButton.setEnabled(true);

        boolean passed = result.violationCount() == 0;

        summary.setText(
                passed
                        ? "Architecture verification passed"
                        : result.violationCount()
                        + " architecture violation(s) detected"
        );

        statusBadge.setText(
                passed
                        ? "No dependency violations or cycles detected"
                        : result.dependencyViolations().size()
                        + " dependency violation(s)  •  "
                        + result.cycles().size()
                        + " cycle(s)"
        );

        summary.setForeground(
                passed ? SUCCESS_COLOR : ERROR_COLOR
        );
        statusBadge.setForeground(
                passed
                        ? SUCCESS_COLOR
                        : UIUtil.getContextHelpForeground()
        );

        content.removeAll();

        JPanel sections = new JPanel();
        sections.setOpaque(false);
        sections.setLayout(new BoxLayout(sections, BoxLayout.Y_AXIS));

        sections.add(
                createSectionCard(
                        "Dependency violations",
                        result.dependencyViolations().size(),
                        result.dependencyViolations(),
                        !result.dependencyViolations().isEmpty()
                )
        );

        sections.add(Box.createVerticalStrut(10));

        sections.add(
                createSectionCard(
                        "Dependency cycles",
                        result.cycles().size(),
                        result.cycles(),
                        !result.cycles().isEmpty()
                )
        );

        content.add(sections, BorderLayout.NORTH);

        revalidate();
        repaint();
    }

    private JPanel createSectionCard(
            @NotNull String title,
            int count,
            @NotNull List<?> values,
            boolean hasProblems
    ) {
        JPanel card = new JPanel(new BorderLayout(0, 8));
        card.setOpaque(true);
        card.setBackground(
                JBColor.namedColor(
                        "EditorPane.background",
                        UIUtil.getPanelBackground()
                )
        );
        card.setBorder(
                BorderFactory.createCompoundBorder(
                        JBUI.Borders.customLine(
                                hasProblems
                                        ? JBColor.namedColor(
                                        "ValidationError.borderColor",
                                        ERROR_COLOR
                                )
                                        : UIUtil.getBoundsColor(),
                                1
                        ),
                        JBUI.Borders.empty(10)
                )
        );

        JPanel sectionHeader = new JPanel(new BorderLayout(8, 0));
        sectionHeader.setOpaque(false);

        JLabel titleLabel = new JBLabel(title);
        titleLabel.setFont(
                titleLabel.getFont().deriveFont(Font.BOLD, 13.0f)
        );

        JLabel countLabel = new JBLabel(String.valueOf(count));
        countLabel.setHorizontalAlignment(JLabel.CENTER);
        countLabel.setFont(
                countLabel.getFont().deriveFont(Font.BOLD, 12.0f)
        );
        countLabel.setForeground(
                hasProblems ? ERROR_COLOR : SUCCESS_COLOR
        );
        countLabel.setBorder(
                BorderFactory.createCompoundBorder(
                        BorderFactory.createLineBorder(
                                hasProblems ? ERROR_COLOR : SUCCESS_COLOR
                        ),
                        JBUI.Borders.empty(2, 7)
                )
        );

        sectionHeader.add(titleLabel, BorderLayout.WEST);
        sectionHeader.add(countLabel, BorderLayout.EAST);

        card.add(sectionHeader, BorderLayout.NORTH);

        if (values.isEmpty()) {
            JLabel empty = new JBLabel("No issues detected");
            empty.setForeground(SUCCESS_COLOR);
            empty.setBorder(JBUI.Borders.emptyTop(4));
            card.add(empty, BorderLayout.CENTER);
            return card;
        }

        List<String> rows = toDisplayRows(values);
        JBList<String> list = new JBList<>(rows);
        list.setVisibleRowCount(Math.min(10, rows.size()));
        list.setBackground(card.getBackground());
        list.setBorder(BorderFactory.createEmptyBorder());
        list.setCellRenderer(new VerificationRowRenderer());
        list.setFixedCellHeight(30);

        JBScrollPane listScrollPane = new JBScrollPane(list);
        listScrollPane.setBorder(BorderFactory.createEmptyBorder());
        listScrollPane.getVerticalScrollBar().setUnitIncrement(14);
        listScrollPane.setPreferredSize(
                new Dimension(0, Math.min(320, rows.size() * 30 + 4))
        );

        card.add(listScrollPane, BorderLayout.CENTER);

        return card;
    }

    @NotNull
    private List<String> toDisplayRows(@NotNull List<?> values) {
        List<String> rows = new ArrayList<>();

        for (Object value : values) {
            if (value instanceof ModulithDependencyAnalysis analysis) {
                String namedInterface =
                        analysis.namedInterfaceName() == null
                                ? ""
                                : "  ::  " + analysis.namedInterfaceName();

                rows.add(
                        analysis.sourcePackage()
                                + "  →  "
                                + analysis.targetPackage()
                                + "  ::  "
                                + analysis.targetClass().getQualifiedName()
                                + namedInterface
                );
            } else {
                rows.add(String.valueOf(value));
            }
        }

        return rows;
    }

    private JPanel createProgressState() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        panel.setOpaque(false);

        JLabel dot = new JLabel("●");
        dot.setForeground(WARNING_COLOR);

        JLabel label = new JLabel("Checking current project architecture...");
        label.setForeground(UIUtil.getContextHelpForeground());

        panel.add(dot);
        panel.add(label);

        return panel;
    }

    private void showNotVerified() {
        hasVerificationResult = false;
        summary.setText("Architecture not verified");
        summary.setForeground(UIUtil.getLabelForeground());
        statusBadge.setText("Run verification to analyze the current project");
        statusBadge.setForeground(UIUtil.getContextHelpForeground());

        content.removeAll();

        JPanel empty = new JPanel();
        empty.setOpaque(false);
        empty.setLayout(new BoxLayout(empty, BoxLayout.Y_AXIS));
        empty.setBorder(JBUI.Borders.empty(24));

        JLabel icon = new JLabel("✓");
        icon.setAlignmentX(Component.CENTER_ALIGNMENT);
        icon.setFont(icon.getFont().deriveFont(Font.BOLD, 28.0f));
        icon.setForeground(UIUtil.getContextHelpForeground());

        JLabel title = new JBLabel("Ready to verify");
        title.setAlignmentX(Component.CENTER_ALIGNMENT);
        title.setFont(title.getFont().deriveFont(Font.BOLD, 15.0f));

        JLabel message = new JBLabel(
                "Check module dependencies, API access, and dependency cycles."
        );
        message.setAlignmentX(Component.CENTER_ALIGNMENT);
        message.setForeground(UIUtil.getContextHelpForeground());

        empty.add(icon);
        empty.add(Box.createVerticalStrut(8));
        empty.add(title);
        empty.add(Box.createVerticalStrut(4));
        empty.add(message);

        content.add(empty, BorderLayout.NORTH);
        revalidate();
        repaint();
    }

    private void showVerificationError(@NotNull Throwable error) {
        verifyButton.setEnabled(true);

        summary.setText("Verification failed");
        summary.setForeground(ERROR_COLOR);
        statusBadge.setText("The architecture could not be verified");
        statusBadge.setForeground(ERROR_COLOR);

        content.removeAll();

        JLabel message = new JBLabel(
                error.getMessage() == null
                        ? "An unexpected error occurred while verifying the project."
                        : error.getMessage()
        );
        message.setForeground(ERROR_COLOR);
        message.setBorder(JBUI.Borders.empty(8));

        content.add(message, BorderLayout.NORTH);
        revalidate();
        repaint();
    }

    private static final class VerificationRowRenderer
            extends DefaultListCellRenderer {

        @Override
        public Component getListCellRendererComponent(
                JList<?> list,
                Object value,
                int index,
                boolean selected,
                boolean focused
        ) {
            JLabel label = (JLabel) super.getListCellRendererComponent(
                    list,
                    value,
                    index,
                    selected,
                    focused
            );

            label.setBorder(
                    BorderFactory.createCompoundBorder(
                            BorderFactory.createMatteBorder(
                                    0,
                                    0,
                                    index == list.getModel().getSize() - 1 ? 0 : 1,
                                    0,
                                    UIUtil.getBoundsColor()
                            ),
                            JBUI.Borders.empty(5, 6)
                    )
            );
            label.setFont(
                    label.getFont().deriveFont(Font.PLAIN, 12.0f)
            );

            return label;
        }
    }
}
