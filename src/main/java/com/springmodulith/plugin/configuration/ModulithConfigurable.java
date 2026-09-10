package com.springmodulith.plugin.configuration;

import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.Nullable;

import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;

public final class ModulithConfigurable implements Configurable {
    private final Project project;
    private JPanel panel;
    private JTextField rootPackage;
    private JComboBox<String> detectionStrategy;
    private JCheckBox apiUsage;
    private JCheckBox allowedDependencies;
    private JCheckBox eventListeners;

    public ModulithConfigurable(Project project) { this.project = project; }

    @Override @Nls public String getDisplayName() { return "Spring Modulith"; }

    @Override @Nullable public JComponent createComponent() {
        panel = new JPanel(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(6, 6, 6, 6);
        c.anchor = GridBagConstraints.WEST;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.weightx = 0;
        panel.add(new JLabel("Root package:"), c);
        rootPackage = new JTextField(32);
        c.gridx = 1; c.weightx = 1;
        panel.add(rootPackage, c);
        c.gridx = 0; c.gridy = 1; c.weightx = 0;
        panel.add(new JLabel("Module detection:"), c);
        detectionStrategy = new JComboBox<>(new String[]{ModulithSettings.DIRECT_SUB_PACKAGES, ModulithSettings.EXPLICITLY_ANNOTATED});
        c.gridx = 1; c.weightx = 1;
        panel.add(detectionStrategy, c);
        apiUsage = new JCheckBox("Check cross-module API access");
        allowedDependencies = new JCheckBox("Validate allowedDependencies");
        eventListeners = new JCheckBox("Suggest @ApplicationModuleListener");
        c.gridx = 0; c.gridy = 2; c.gridwidth = 2; c.weightx = 1;
        panel.add(apiUsage, c);
        c.gridy = 3; panel.add(allowedDependencies, c);
        c.gridy = 4; panel.add(eventListeners, c);
        reset();
        return panel;
    }

    @Override public boolean isModified() {
        ModulithSettings s = ModulithSettings.getInstance(project);
        return !rootPackage.getText().trim().equals(s.getRootPackage())
                || !detectionStrategy.getSelectedItem().equals(s.getDetectionStrategy())
                || apiUsage.isSelected() != s.isInspectApiUsage()
                || allowedDependencies.isSelected() != s.isInspectAllowedDependencies()
                || eventListeners.isSelected() != s.isInspectEventListeners();
    }

    @Override public void apply() {
        ModulithSettings s = ModulithSettings.getInstance(project);
        s.setRootPackage(rootPackage.getText());
        s.setDetectionStrategy((String) detectionStrategy.getSelectedItem());
        s.stateForUi(apiUsage.isSelected(), allowedDependencies.isSelected(), eventListeners.isSelected());
    }

    @Override public void reset() {
        ModulithSettings s = ModulithSettings.getInstance(project);
        rootPackage.setText(s.getRootPackage());
        detectionStrategy.setSelectedItem(s.getDetectionStrategy());
        apiUsage.setSelected(s.isInspectApiUsage());
        allowedDependencies.setSelected(s.isInspectAllowedDependencies());
        eventListeners.setSelected(s.isInspectEventListeners());
    }

    @Override public void disposeUIResources() { panel = null; rootPackage = null; detectionStrategy = null; apiUsage = null; allowedDependencies = null; eventListeners = null; }
}
