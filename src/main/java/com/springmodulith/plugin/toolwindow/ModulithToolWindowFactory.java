package com.springmodulith.plugin.toolwindow;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBList;
import com.intellij.ui.components.JBPanel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.content.ContentFactory;
import com.springmodulith.plugin.model.ModulithModule;
import com.springmodulith.plugin.resolver.ModulithModuleResolver;
import org.jetbrains.annotations.NotNull;

import javax.swing.DefaultListModel;
import javax.swing.JList;
import javax.swing.JPanel;
import java.awt.BorderLayout;

public final class ModulithToolWindowFactory implements ToolWindowFactory {
    @Override public void createToolWindowContent(@NotNull Project project, @NotNull ToolWindow toolWindow) {
        JPanel root = new JBPanel<>(new BorderLayout(8, 8));
        root.add(new JBLabel("Spring Modulith modules"), BorderLayout.NORTH);
        DefaultListModel<String> model = new DefaultListModel<>();
        for (ModulithModule module : new ModulithModuleResolver(project).resolveModules()) {
            StringBuilder line = new StringBuilder(module.getName()).append("  ").append(module.isOpen() ? "[open]" : "[closed]");
            if (!module.getAllowedDependencies().isEmpty()) line.append("  allows: ").append(String.join(", ", module.getAllowedDependencies()));
            model.addElement(line.toString());
        }
        JList<String> list = new JBList<>(model);
        root.add(new JBScrollPane(list), BorderLayout.CENTER);
        toolWindow.getContentManager().addContent(ContentFactory.getInstance().createContent(root, "Modules", false));
    }
}
