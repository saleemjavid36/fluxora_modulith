package com.springmodulith.plugin.projectView;

import com.intellij.ide.projectView.PresentationData;
import com.intellij.ide.projectView.ProjectViewNode;
import com.intellij.ide.projectView.ProjectViewNodeDecorator;
import com.intellij.ide.projectView.impl.nodes.PsiDirectoryNode;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.IconLoader;
import com.intellij.psi.JavaDirectoryService;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiPackage;
import com.springmodulith.plugin.model.ModulithModule;
import com.springmodulith.plugin.resolver.ModulithModuleResolver;
import org.jetbrains.annotations.NotNull;

import javax.swing.Icon;

public final class ModulithProjectViewNodeDecorator implements ProjectViewNodeDecorator {
    private static final Icon PACKAGE_PUBLIC =
            IconLoader.getIcon("/icons/packagePublic.svg", ModulithProjectViewNodeDecorator.class);
    private static final Icon PACKAGE_INTERNAL =
            IconLoader.getIcon("/icons/packageInternal.svg", ModulithProjectViewNodeDecorator.class);

    @Override
    public void decorate(@NotNull ProjectViewNode<?> node, @NotNull PresentationData data) {
        if (!(node instanceof PsiDirectoryNode directoryNode)) return;

        PsiDirectory directory = directoryNode.getValue();
        if (directory == null || directory.getFiles().length == 0) return;

        PsiPackage pkg = JavaDirectoryService.getInstance().getPackage(directory);
        if (pkg == null || pkg.getQualifiedName().isEmpty()) return;

        Project project = directory.getProject();
        project.getService(ModulithProjectViewRefreshService.class);
        PsiFile packageInfo = directory.findFile("package-info.java");
        PsiFile contextFile = packageInfo != null
                ? packageInfo
                : directory.getFiles()[0];

        ModulithModule module = ReadAction.compute(() ->
                new ModulithModuleResolver(project)
                        .resolveModule(contextFile, pkg.getQualifiedName())
        );
        if (module == null) return;

        Icon icon = ReadAction.compute(() ->
                resolveIcon(directory, pkg.getQualifiedName(), module)
        );
        data.setIcon(icon);
        data.setLocationString(module.isOpen() ? "open" : "closed");
    }

    @NotNull
    private Icon resolveIcon(
            @NotNull PsiDirectory directory,
            @NotNull String packageName,
            @NotNull ModulithModule module) {
        return ModulithPackageVisibilityResolver.isPublic(directory, packageName, module)
                ? PACKAGE_PUBLIC
                : PACKAGE_INTERNAL;
    }

}
