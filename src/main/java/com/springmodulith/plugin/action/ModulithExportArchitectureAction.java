package com.springmodulith.plugin.action;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.fileChooser.FileChooserFactory;
import com.intellij.openapi.fileChooser.FileSaverDescriptor;
import com.intellij.openapi.fileChooser.FileSaverDialog;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.springmodulith.plugin.configuration.ModulithProjectModelService;
import com.springmodulith.plugin.model.ModulithDependencyGraph;
import org.jetbrains.annotations.NotNull;
import com.intellij.openapi.vfs.VirtualFileWrapper;

import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;

public final class ModulithExportArchitectureAction extends AnAction {
    @Override public void actionPerformed(@NotNull AnActionEvent event) {
        Project project = event.getProject();
        if (project == null) return;
        export(project);
    }

    public static void export(@NotNull Project project) {
        FileSaverDescriptor descriptor = new FileSaverDescriptor(
                "Export Spring Modulith Architecture",
                "Choose JSON destination",
                "json"
        );

        FileSaverDialog dialog =
                FileChooserFactory.getInstance()
                        .createSaveFileDialog(descriptor, project);

        VirtualFileWrapper wrapper =
                dialog.save(project.getBaseDir(), "architecture.json");

        if (wrapper == null) return;

        VirtualFile parent = wrapper.getVirtualFile();
        if (parent == null) return;

        ModulithDependencyGraph graph =
                project.getService(ModulithProjectModelService.class).getGraph();

        String json = toJson(graph);

        try {
            parent.setBinaryContent(json.getBytes(StandardCharsets.UTF_8));
        } catch (Exception ignored) {
            // File chooser already created the destination.
        }
    }

    private static String toJson(ModulithDependencyGraph graph) {
        String modules = graph.getModules().stream()
                .map(m -> "{\"name\":\"" + escape(m.getName()) + "\",\"package\":\"" + escape(m.getPackageName()) + "\",\"open\":" + m.isOpen() + ",\"namedInterfaces\":[" +
                        m.getNamedInterfaces().stream().map(i -> "{\"name\":\"" + escape(i.getName()) + "\",\"package\":\"" + escape(i.getPackageName()) + "\",\"types\":[" + i.getTypeNames().stream().map(t -> "\"" + escape(t) + "\"").collect(Collectors.joining(",")) + "]}").collect(Collectors.joining(",")) + "]}")
                .collect(Collectors.joining(","));
        String deps = graph.getDependencies().stream()
                .map(d -> "{\"source\":\"" + escape(d.sourcePackage()) + "\",\"target\":\"" + escape(d.targetPackage()) + "\",\"kind\":\"" + d.kind() + "\",\"namedInterface\":" + (d.namedInterface() == null ? "null" : "\"" + escape(d.namedInterface()) + "\"") + ",\"references\":" + d.referenceCount() + "}")
                .collect(Collectors.joining(","));
        return "{\"modules\":[" + modules + "],\"dependencies\":[" + deps + "]}";
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    @Override public void update(@NotNull AnActionEvent event) { event.getPresentation().setEnabledAndVisible(event.getProject() != null); }
}
