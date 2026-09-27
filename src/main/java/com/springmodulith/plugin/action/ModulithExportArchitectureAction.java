package com.springmodulith.plugin.action;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.fileChooser.FileChooserFactory;
import com.intellij.openapi.fileChooser.FileSaverDescriptor;
import com.intellij.openapi.fileChooser.FileSaverDialog;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileWrapper;
import com.springmodulith.plugin.configuration.ModulithProjectModelService;
import com.springmodulith.plugin.model.ModulithDependencyGraph;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;

public final class ModulithExportArchitectureAction extends AnAction {
    @Override
    public void actionPerformed(@NotNull AnActionEvent event) {
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

        VirtualFile targetFile = wrapper.getVirtualFile();
        if (targetFile == null || !targetFile.isValid()) return;

        ModulithDependencyGraph graph =
                project.getService(ModulithProjectModelService.class).getGraph();

        String json = toJson(graph);

        try {
            byte[] content = json.getBytes(StandardCharsets.UTF_8);

            // VirtualFile content changes require a write action.
            // The previous implementation silently swallowed this failure,
            // which made the Export JSON action appear to do nothing.
            WriteAction.runAndWait(() -> targetFile.setBinaryContent(content));
        } catch (IOException exception) {
            com.intellij.openapi.ui.Messages.showErrorDialog(
                    project,
                    "Unable to export the Spring Modulith architecture JSON.\n\n"
                            + exception.getMessage(),
                    "Export JSON Failed"
            );
        }
    }

    private static String toJson(ModulithDependencyGraph graph) {
        String modules = graph.getModules().stream()
                .map(m -> "{\"name\":\"" + escape(m.getName()) + "\",\"package\":\"" + escape(m.getPackageName()) + "\",\"open\":" + m.isOpen() + ",\"namedInterfaces\":[" +
                        m.getNamedInterfaces().stream().map(i -> "{\"name\":\"" + escape(i.getName()) + "\",\"package\":\"" + escape(i.getPackageName()) + "\",\"types\":[" + i.getTypeNames().stream().map(t -> "\"" + escape(t) + "\"").collect(Collectors.joining(",")) + "]}").collect(Collectors.joining(",")) + "]}")
                .collect(Collectors.joining(","));

        String deps = graph.getDependencies().stream()
                .map(d -> "{\"source\":\"" + escape(d.sourcePackage())
                        + "\",\"target\":\"" + escape(d.targetPackage())
                        + "\",\"kind\":\"" + escape(String.valueOf(d.kind()))
                        + "\",\"namedInterfaces\":["
                        + d.namedInterfaces().stream()
                        .map(i -> "\"" + escape(i) + "\"")
                        .collect(Collectors.joining(","))
                        + "],\"allowedReferences\":" + d.allowedReferenceCount()
                        + ",\"forbiddenReferences\":" + d.forbiddenReferenceCount()
                        + ",\"namedInterfaceReferences\":" + d.namedInterfaceReferenceCount()
                        + ",\"references\":" + d.referenceCount() + "}")
                .collect(Collectors.joining(","));

        return "{\"modules\":[" + modules + "],\"dependencies\":[" + deps + "]}";
    }

    private static String escape(String value) {
        if (value == null) return "";

        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\b", "\\b")
                .replace("\f", "\\f")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    @Override
    public void update(@NotNull AnActionEvent event) {
        event.getPresentation().setEnabledAndVisible(event.getProject() != null);
    }
}
