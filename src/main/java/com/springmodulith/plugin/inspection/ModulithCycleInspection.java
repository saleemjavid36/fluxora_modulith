package com.springmodulith.plugin.inspection;

import com.intellij.codeInspection.AbstractBaseJavaLocalInspectionTool;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.openapi.project.Project;
import com.intellij.psi.JavaElementVisitor;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiJavaFile;
import com.springmodulith.plugin.analyzer.ModulithCycleAnalyzer;
import com.springmodulith.plugin.configuration.ModulithSettings;
import com.springmodulith.plugin.model.ModulithModule;
import com.springmodulith.plugin.resolver.ModulithModuleResolver;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class ModulithCycleInspection
        extends AbstractBaseJavaLocalInspectionTool {

    @Override
    public @NotNull PsiElementVisitor buildVisitor(
            @NotNull ProblemsHolder holder,
            boolean isOnTheFly) {

        Project project =
                holder.getProject();

        ModulithSettings settings =
                ModulithSettings.getInstance(project);

        if (!settings.isInspectCycles()) {
            return PsiElementVisitor.EMPTY_VISITOR;
        }

        List<ModulithCycleAnalyzer.Cycle> cycles =
                new ModulithCycleAnalyzer(project)
                        .findCycles();

        if (cycles.isEmpty()) {
            return PsiElementVisitor.EMPTY_VISITOR;
        }

        Map<String, String> messages =
                new HashMap<>();

        for (ModulithCycleAnalyzer.Cycle cycle : cycles) {

            String message =
                    "Modulith module dependency cycle: "
                            + cycle.displayPath();

            for (ModulithModule module :
                    cycle.modules()) {

                messages.put(
                        module.getPackageName(),
                        message
                );
            }
        }

        Set<String> reported =
                new HashSet<>();

        ModulithModuleResolver resolver =
                new ModulithModuleResolver(project);

        return new JavaElementVisitor() {

            @Override
            public void visitClass(
                    @NotNull PsiClass psiClass) {

                PsiJavaFile javaFile =
                        psiClass.getContainingFile()
                                instanceof PsiJavaFile file
                                ? file
                                : null;

                if (javaFile == null) {
                    return;
                }

                ModulithModule module =
                        resolver.resolveModule(
                                javaFile,
                                javaFile.getPackageName()
                        );

                if (module == null) {
                    return;
                }

                String packageName =
                        module.getPackageName();

                if (!reported.add(packageName)) {
                    super.visitClass(psiClass);
                    return;
                }

                String message =
                        messages.get(packageName);

                if (message != null) {

                    holder.registerProblem(
                            psiClass.getNameIdentifier() == null
                                    ? psiClass
                                    : psiClass.getNameIdentifier(),
                            message
                    );
                }

                super.visitClass(psiClass);
            }
        };
    }
}