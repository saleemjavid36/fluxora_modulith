package com.springmodulith.plugin.projectView;

import com.intellij.ide.projectView.ProjectView;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.newvfs.BulkFileListener;
import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Re-decorates the Project View when a package-info.java changes.
 *
 * Icons of sibling/child packages depend on package-info.java of the module root,
 * but the Project View only re-decorates the node that was modified. Without this
 * refresh, switching CLOSED to OPEN leaves the child packages with stale icons.
 */
public final class ModulithProjectViewRefreshListener implements BulkFileListener {
    private final Project project;
    private final AtomicBoolean scheduled = new AtomicBoolean();

    public ModulithProjectViewRefreshListener(@NotNull Project project) {
        this.project = project;
    }

    @Override
    public void after(@NotNull List<? extends VFileEvent> events) {
        boolean relevant = false;
        for (VFileEvent event : events) {
            String path = event.getPath();
            if (path.endsWith("/package-info.java") || path.endsWith("/package-info.kt")) {
                relevant = true;
                break;
            }
        }
        if (!relevant || project.isDisposed() || !scheduled.compareAndSet(false, true)) {
            return;
        }
        ApplicationManager.getApplication().invokeLater(() -> {
            scheduled.set(false);
            if (!project.isDisposed()) {
                ProjectView.getInstance(project).refresh();
            }
        }, project.getDisposed());
    }
}
