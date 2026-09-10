package com.springmodulith.plugin.projectView;

import com.intellij.icons.AllIcons;
import com.intellij.ide.projectView.PresentationData;
import com.intellij.ide.projectView.ProjectViewNode;
import com.intellij.ide.projectView.ProjectViewNodeDecorator;
import com.intellij.ide.projectView.impl.nodes.PsiDirectoryNode;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiPackage;
import com.intellij.psi.JavaDirectoryService;
import com.springmodulith.plugin.model.ModulithModule;
import com.springmodulith.plugin.resolver.ModulithModuleResolver;
import org.jetbrains.annotations.NotNull;

public final class ModulithProjectViewNodeDecorator implements ProjectViewNodeDecorator {
    @Override public void decorate(@NotNull ProjectViewNode<?> node, @NotNull PresentationData data) {
        if (!(node instanceof PsiDirectoryNode directoryNode)) return;
        PsiDirectory directory = directoryNode.getValue();
        if (directory == null) return;
        PsiPackage pkg = JavaDirectoryService.getInstance().getPackage(directory);
        if (pkg == null) return;
        if (directory.getFiles().length == 0) return;
        Project project = directory.getProject();
        com.intellij.psi.PsiFile contextFile = directory.findFile("package-info.java");
        if (contextFile == null) contextFile = directory.getFiles()[0];
        ModulithModule module = new ModulithModuleResolver(project).resolveModule(contextFile, pkg.getQualifiedName());
        if (module == null) return;
        data.setIcon(AllIcons.Nodes.Package);
        data.setLocationString(module.isOpen() ? "open" : "closed");
    }
}
