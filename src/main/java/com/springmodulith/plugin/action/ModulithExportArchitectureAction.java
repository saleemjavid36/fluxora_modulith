package com.springmodulith.plugin.action;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.fileChooser.FileChooserFactory;
import com.intellij.openapi.fileChooser.FileSaverDescriptor;
import com.intellij.openapi.fileChooser.FileSaverDialog;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.VirtualFileWrapper;
import com.springmodulith.plugin.configuration.ModulithProjectModelService;
import com.springmodulith.plugin.model.ModulithDependencyGraph;
import com.springmodulith.plugin.model.ModulithModule;
import com.springmodulith.plugin.toolwindow.ModulithVerificationPanel;
import org.jetbrains.annotations.NotNull;

import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import java.awt.Component;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public final class ModulithExportArchitectureAction extends AnAction {

    private enum ExportFormat {
        JSON("JSON", "json"),
        MERMAID("Mermaid", "mmd"),
        PLANTUML("PlantUML", "puml"),
        GRAPHVIZ("Graphviz DOT", "dot"),
        HTML("HTML", "html");

        private final String label;
        private final String extension;

        ExportFormat(@NotNull String label, @NotNull String extension) {
            this.label = label;
            this.extension = extension;
        }
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent event) {
        Project project = event.getProject();
        if (project == null) {
            return;
        }
        export(project);
    }

    /**
     * Keeps the existing Tools-menu behavior: export architecture as JSON.
     */
    public static void export(@NotNull Project project) {
        export(project, ExportFormat.JSON);
    }

    /**
     * Shows the optional multi-format architecture export menu without changing
     * the existing verification JSON export action.
     */
    public static void showExportMenu(
            @NotNull Project project,
            @NotNull Component owner,
            @NotNull ModulithVerificationPanel verificationPanel) {
        if (!verificationPanel.hasVerificationResult()) {
            Messages.showInfoMessage(
                    project,
                    "Run Verify Architecture before exporting the architecture.",
                    "Architecture Not Verified"
            );
            return;
        }

        JPopupMenu popup = new JPopupMenu();

        for (ExportFormat format : ExportFormat.values()) {
            JMenuItem item = new JMenuItem(format.label);
            item.addActionListener(event -> export(project, format));
            popup.add(item);
        }

        popup.show(owner, 0, owner.getHeight());
    }

    private static void export(
            @NotNull Project project,
            @NotNull ExportFormat format) {

        FileSaverDescriptor descriptor = new FileSaverDescriptor(
                "Export Spring Modulith Architecture",
                "Choose " + format.label + " destination",
                format.extension
        );

        FileSaverDialog dialog =
                FileChooserFactory.getInstance()
                        .createSaveFileDialog(descriptor, project);

        VirtualFile initialDirectory = findDownloadsDirectory(project);

        VirtualFileWrapper wrapper = dialog.save(
                initialDirectory,
                "architecture." + format.extension
        );

        if (wrapper == null || wrapper.getFile() == null) {
            return;
        }

        new Task.Backgroundable(
                project,
                "Preparing Spring Modulith architecture export",
                true) {

            private ModulithDependencyGraph graph;

            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                indicator.setIndeterminate(true);
                graph = DumbService.getInstance(project)
                        .runReadActionInSmartMode(() ->
                                project
                                        .getService(ModulithProjectModelService.class)
                                        .getGraph()
                        );
            }

            @Override
            public void onSuccess() {
                if (project.isDisposed() || graph == null) {
                    return;
                }

                String content = switch (format) {
                    case JSON -> toJson(graph);
                    case MERMAID -> toMermaid(graph);
                    case PLANTUML -> toPlantUml(graph);
                    case GRAPHVIZ -> toGraphviz(graph);
                    case HTML -> toHtml(graph);
                };

                try {
                    Files.writeString(
                            wrapper.getFile().toPath(),
                            content,
                            StandardCharsets.UTF_8
                    );

                    Messages.showInfoMessage(
                            project,
                            format.label + " architecture exported to:\n"
                                    + wrapper.getFile().getAbsolutePath(),
                            "Export Complete"
                    );
                } catch (Exception exception) {
                    Messages.showErrorDialog(
                            project,
                            "Unable to export the Spring Modulith architecture "
                                    + format.label + ".\n\n"
                                    + exception.getMessage(),
                            "Export Architecture Failed"
                    );
                }
            }

            @Override
            public void onThrowable(@NotNull Throwable error) {
                Messages.showErrorDialog(
                        project,
                        "Unable to prepare the Spring Modulith architecture export.\n\n"
                                + error.getMessage(),
                        "Export Architecture Failed"
                );
            }
        }.queue();
    }

    /**
     * Opens the export chooser in the user's Downloads directory when it
     * exists. If Downloads is unavailable (for example on a customized
     * environment), fall back to the project directory without changing the
     * existing export behavior.
     */
    @NotNull
    private static VirtualFile findDownloadsDirectory(@NotNull Project project) {
        Path downloadsPath = Paths.get(
                System.getProperty("user.home"),
                "Downloads"
        );

        VirtualFile downloads = VirtualFileManager.getInstance()
                .findFileByNioPath(downloadsPath);

        if (downloads != null && downloads.isDirectory()) {
            return downloads;
        }

        return project.getBaseDir();
    }

    private static String toJson(@NotNull ModulithDependencyGraph graph) {
        String modules = graph.getModules().stream()
                .map(m -> "{\"name\":\"" + escape(m.getName())
                        + "\",\"package\":\"" + escape(m.getPackageName())
                        + "\",\"open\":" + m.isOpen()
                        + ",\"namedInterfaces\":["
                        + m.getNamedInterfaces().stream()
                        .map(i -> "{\"name\":\"" + escape(i.getName())
                                + "\",\"package\":\"" + escape(i.getPackageName())
                                + "\",\"types\":["
                                + i.getTypeNames().stream()
                                .map(t -> "\"" + escape(t) + "\"")
                                .collect(Collectors.joining(","))
                                + "]}")
                        .collect(Collectors.joining(","))
                        + "]}")
                .collect(Collectors.joining(","));

        String dependencies = graph.getDependencies().stream()
                .map(d -> "{\"source\":\"" + escape(d.sourcePackage())
                        + "\",\"target\":\"" + escape(d.targetPackage())
                        + "\",\"kind\":\"" + escape(edgeLabel(d))
                        + "\",\"namedInterfaces\":["
                        + d.namedInterfaces().stream()
                        .map(i -> "\"" + escape(i) + "\"")
                        .collect(Collectors.joining(","))
                        + "],\"allowedReferences\":" + d.allowedReferenceCount()
                        + ",\"forbiddenReferences\":" + d.forbiddenReferenceCount()
                        + ",\"namedInterfaceReferences\":" + d.namedInterfaceReferenceCount()
                        + ",\"references\":" + d.referenceCount()
                        + "}")
                .collect(Collectors.joining(","));

        return "{\"modules\":[" + modules
                + "],\"dependencies\":[" + dependencies + "]}";
    }

    private static String toMermaid(@NotNull ModulithDependencyGraph graph) {
        StringBuilder result = new StringBuilder("flowchart LR\n");

        for (ModulithModule module : graph.getModules()) {
            result.append("    ")
                    .append(mermaidId(module.getPackageName()))
                    .append("[\"")
                    .append(escapeMermaid(module.getName()))
                    .append("\\n")
                    .append(escapeMermaid(module.getPackageName()))
                    .append("\"]\n");
        }

        for (ModulithDependencyGraph.ModuleDependency dependency : graph.getDependencies()) {
            result.append("    ")
                    .append(mermaidId(dependency.sourcePackage()))
                    .append(" -->|\"")
                    .append(escapeMermaid(edgeLabel(dependency)))
                    .append("\"| ")
                    .append(mermaidId(dependency.targetPackage()))
                    .append("\n");
        }

        return result.toString();
    }

    private static String toPlantUml(@NotNull ModulithDependencyGraph graph) {
        StringBuilder result = new StringBuilder("@startuml\n");
        result.append("left to right direction\n");

        for (ModulithModule module : graph.getModules()) {
            result.append("rectangle \"")
                    .append(escapePlantUml(module.getName()))
                    .append("\\n")
                    .append(escapePlantUml(module.getPackageName()))
                    .append("\" as ")
                    .append(plantUmlId(module.getPackageName()))
                    .append("\n");
        }

        for (ModulithDependencyGraph.ModuleDependency dependency : graph.getDependencies()) {
            result.append(plantUmlId(dependency.sourcePackage()))
                    .append(" --> ")
                    .append(plantUmlId(dependency.targetPackage()))
                    .append(" : ")
                    .append(escapePlantUml(edgeLabel(dependency)))
                    .append("\n");
        }

        result.append("@enduml\n");
        return result.toString();
    }

    private static String toGraphviz(@NotNull ModulithDependencyGraph graph) {
        StringBuilder result = new StringBuilder("digraph Modulith {\n");
        result.append("    rankdir=LR;\n");
        result.append("    node [shape=box];\n");

        for (ModulithModule module : graph.getModules()) {
            result.append("    \"")
                    .append(escapeGraphviz(module.getPackageName()))
                    .append("\" [label=\"")
                    .append(escapeGraphviz(module.getName()))
                    .append("\\n")
                    .append(escapeGraphviz(module.getPackageName()))
                    .append("\"];\n");
        }

        for (ModulithDependencyGraph.ModuleDependency dependency : graph.getDependencies()) {
            result.append("    \"")
                    .append(escapeGraphviz(dependency.sourcePackage()))
                    .append("\" -> \"")
                    .append(escapeGraphviz(dependency.targetPackage()))
                    .append("\" [label=\"")
                    .append(escapeGraphviz(edgeLabel(dependency)))
                    .append("\"];\n");
        }

        result.append("}\n");
        return result.toString();
    }


    private static String toHtml(@NotNull ModulithDependencyGraph graph) {
        StringBuilder html = new StringBuilder();
        html.append("<!doctype html><html lang=\"en\"><head>")
                .append("<meta charset=\"UTF-8\">")
                .append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">")
                .append("<title>Spring Modulith Architecture</title>")
                .append("<style>")
                .append("body{font-family:Inter,Segoe UI,Arial,sans-serif;margin:0;padding:32px;background:#f5f7fa;color:#1f2937}")
                .append(".container{max-width:1200px;margin:0 auto}.header{margin-bottom:24px}.header h1{margin:0 0 8px}.muted{color:#6b7280}")
                .append(".summary{display:flex;gap:12px;flex-wrap:wrap;margin:20px 0}.badge{padding:8px 12px;border-radius:8px;background:#fff;border:1px solid #d1d5db}")
                .append(".section{background:#fff;border:1px solid #d1d5db;border-radius:12px;margin-top:20px;overflow:hidden}")
                .append(".section h2{font-size:18px;margin:0;padding:16px 20px;border-bottom:1px solid #e5e7eb}")
                .append("table{width:100%;border-collapse:collapse}th,td{text-align:left;padding:11px 14px;border-bottom:1px solid #eef0f2;vertical-align:top}th{background:#f9fafb;font-weight:600}")
                .append("tr:last-child td{border-bottom:0}.module-name{font-weight:600}.code{font-family:ui-monospace,SFMono-Regular,Consolas,monospace;font-size:12px}")
                .append("</style></head><body><div class=\"container\">");

        html.append("<div class=\"header\"><h1>Spring Modulith Architecture</h1>")
                .append("<div class=\"muted\">Exported architecture model</div></div>");

        html.append("<div class=\"summary\">")
                .append("<div class=\"badge\"><strong>Modules:</strong> ")
                .append(graph.getModules().size()).append("</div>")
                .append("<div class=\"badge\"><strong>Dependencies:</strong> ")
                .append(graph.getDependencies().size()).append("</div>")
                .append("</div>");

        html.append("<section class=\"section\"><h2>Modules</h2><table><thead><tr>")
                .append("<th>Name</th><th>Package</th><th>Status</th><th>Named interfaces</th></tr></thead><tbody>");

        for (ModulithModule module : graph.getModules()) {
            html.append("<tr><td class=\"module-name\">")
                    .append(escapeHtml(module.getName()))
                    .append("</td><td class=\"code\">")
                    .append(escapeHtml(module.getPackageName()))
                    .append("</td><td>")
                    .append(module.isOpen() ? "OPEN" : "CLOSED")
                    .append(module.isAllowedDependenciesConfigured() ? " · explicit rules" : " · implicit dependencies")
                    .append("</td><td>");

            if (module.getNamedInterfaces().isEmpty()) {
                html.append("—");
            } else {
                html.append("<ul>");
                for (var namedInterface : module.getNamedInterfaces()) {
                    html.append("<li>")
                            .append(escapeHtml(namedInterface.getName()))
                            .append("</li>");
                }
                html.append("</ul>");
            }
            html.append("</td></tr>");
        }
        html.append("</tbody></table></section>");

        html.append("<section class=\"section\"><h2>Dependencies</h2><table><thead><tr>")
                .append("<th>Source</th><th>Target</th><th>Kind</th><th>References</th></tr></thead><tbody>");

        for (ModulithDependencyGraph.ModuleDependency dependency : graph.getDependencies()) {
            html.append("<tr><td class=\"code\">")
                    .append(escapeHtml(dependency.sourcePackage()))
                    .append("</td><td class=\"code\">")
                    .append(escapeHtml(dependency.targetPackage()))
                    .append("</td><td>")
                    .append(escapeHtml(edgeLabel(dependency)))
                    .append("</td><td>")
                    .append(dependency.referenceCount())
                    .append("</td></tr>");
        }
        html.append("</tbody></table></section>");
        html.append("</div></body></html>");
        return html.toString();
    }

    private static String escapeHtml(@NotNull String value) {
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    private static String edgeLabel(
            @NotNull ModulithDependencyGraph.ModuleDependency dependency) {
        List<String> parts = new ArrayList<>();
        if (dependency.hasForbiddenReferences()) {
            parts.add("forbidden");
        }
        if (dependency.hasNamedInterfaceReferences()) {
            parts.add("named interface");
        }
        if (dependency.hasAllowedReferences()) {
            parts.add("allowed");
        }
        if (parts.isEmpty()) {
            parts.add(dependency.kind().name().toLowerCase(java.util.Locale.ROOT));
        }
        return String.join(" / ", parts) + " x" + dependency.referenceCount();
    }

    private static String mermaidId(@NotNull String value) {
        return "module_" + Integer.toHexString(value.hashCode());
    }

    private static String plantUmlId(@NotNull String value) {
        return "module_" + Integer.toHexString(value.hashCode());
    }

    private static String escapeMermaid(@NotNull String value) {
        return value.replace("\\", "\\\\")
                .replace("\"", "&quot;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }

    private static String escapePlantUml(@NotNull String value) {
        return value.replace("\\", "\\\\")
                .replace("\"", "\\\"");
    }

    private static String escapeGraphviz(@NotNull String value) {
        return value.replace("\\", "\\\\")
                .replace("\"", "\\\"");
    }

    private static String escape(@NotNull String value) {
        return value.replace("\\", "\\\\")
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
