package com.springmodulith.plugin.configuration;

import com.intellij.openapi.components.Service;
import com.intellij.openapi.project.Project;
import com.intellij.psi.util.PsiModificationTracker;
import com.springmodulith.plugin.analyzer.ModulithDependencyGraphAnalyzer;
import com.springmodulith.plugin.model.ModulithDependencyGraph;
import org.jetbrains.annotations.NotNull;

@Service(Service.Level.PROJECT)
public final class ModulithProjectModelService {
    private final Project project;
    private ModulithDependencyGraph cachedGraph;
    private long modificationCount = -1L;

    public ModulithProjectModelService(@NotNull Project project) {
        this.project = project;
    }

    @NotNull
    public synchronized ModulithDependencyGraph getGraph() {
        long current = PsiModificationTracker.getInstance(project).getModificationCount();
        if (cachedGraph == null || modificationCount != current) {
            cachedGraph = new ModulithDependencyGraphAnalyzer(project).analyze();
            modificationCount = current;
        }
        return cachedGraph;
    }

    public synchronized void invalidate() {
        cachedGraph = null;
        modificationCount = -1L;
    }
}
