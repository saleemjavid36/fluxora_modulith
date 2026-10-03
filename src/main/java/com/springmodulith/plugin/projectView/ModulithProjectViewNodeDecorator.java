package com.springmodulith.plugin.projectView;

import com.intellij.ide.projectView.PresentationData;
import com.intellij.ide.projectView.ProjectViewNode;
import com.intellij.ide.projectView.ProjectViewNodeDecorator;
import com.intellij.ide.projectView.impl.nodes.PsiDirectoryNode;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.IconLoader;
import com.intellij.util.ui.UIUtil;
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
            IconLoader.getIcon("/icons/fluxoraPublicPackage.svg", ModulithProjectViewNodeDecorator.class);
    private static final Icon PACKAGE_PUBLIC_DARK =
            IconLoader.getIcon("/icons/fluxoraPublicPackage_dark.svg", ModulithProjectViewNodeDecorator.class);
    private static final Icon PACKAGE_INTERNAL =
            IconLoader.getIcon("/icons/fluxoraInternalIcon.svg", ModulithProjectViewNodeDecorator.class);
    private static final Icon PACKAGE_INTERNAL_DARK =
            IconLoader.getIcon("/icons/fluxoraInternalPackage_dark.svg", ModulithProjectViewNodeDecorator.class);
    private static final Icon PACKAGE_NESTED =
            IconLoader.getIcon("/icons/fluxoraNestedPackage.svg", ModulithProjectViewNodeDecorator.class);
    private static final Icon PACKAGE_NESTED_DARK =
            IconLoader.getIcon("/icons/fluxoraNestedPackage_dark.svg", ModulithProjectViewNodeDecorator.class);

    @Override
    public void decorate(@NotNull ProjectViewNode<?> node, @NotNull PresentationData data) {
        if (!(node instanceof PsiDirectoryNode directoryNode)) return;

        PsiDirectory directory = directoryNode.getValue();
        if (directory == null) return;

        PsiPackage pkg = JavaDirectoryService.getInstance().getPackage(directory);
        if (pkg == null || pkg.getQualifiedName().isEmpty()) return;

        Project project = directory.getProject();
        project.getService(ModulithProjectViewRefreshService.class);
        PsiFile contextFile = findContextFile(directory);
        if (contextFile == null) return;

        ModulithModule module = ReadAction.compute(() ->
                new ModulithModuleResolver(project)
                        .resolveModule(contextFile, pkg.getQualifiedName())
        );
        if (module == null) return;

        Icon icon = ReadAction.compute(() ->
                resolveIcon(directory, pkg.getQualifiedName(), module)
        );
        data.setIcon(icon);
        data.setLocationString(null);
    }

    private PsiFile findContextFile(@NotNull PsiDirectory directory) {
        PsiDirectory current = directory;

        while (current != null) {
            PsiFile packageInfo = current.findFile("package-info.java");
            if (packageInfo != null) return packageInfo;

            for (PsiFile file : current.getFiles()) {
                if (file.getName().endsWith(".java")) return file;
            }

            current = current.getParentDirectory();
        }

        return null;
    }

    @NotNull
    private Icon resolveIcon(
            @NotNull PsiDirectory directory,
            @NotNull String packageName,
            @NotNull ModulithModule module) {
        boolean darkTheme = UIUtil.isUnderDarcula();

        // A module root nested inside another module is represented by the
        // dedicated nested-package icon. This preserves the existing module
        // detection behavior while making the previously unused nested icon
        // visible in the Project View.
        if (packageName.equals(module.getPackageName()) && isNestedModule(module, directory)) {
            return darkTheme ? PACKAGE_NESTED_DARK : PACKAGE_NESTED;
        }

        boolean exposed = ModulithPackageVisibilityResolver.isPublic(
                directory, packageName, module);

        return exposed
                ? (darkTheme ? PACKAGE_PUBLIC_DARK : PACKAGE_PUBLIC)
                : (darkTheme ? PACKAGE_INTERNAL_DARK : PACKAGE_INTERNAL);
    }

    private boolean isNestedModule(
            @NotNull ModulithModule module,
            @NotNull PsiDirectory directory) {
        String modulePackage = module.getPackageName();

        for (ModulithModule candidate :
                new ModulithModuleResolver(directory.getProject()).resolveModules()) {
            if (candidate != module
                    && modulePackage.startsWith(candidate.getPackageName() + ".")) {
                return true;
            }
        }

        return false;
    }

}
