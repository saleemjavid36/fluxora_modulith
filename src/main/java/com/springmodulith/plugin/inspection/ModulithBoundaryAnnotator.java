package com.springmodulith.plugin.inspection;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.IntentionActionDelegate;
import com.intellij.codeInsight.intention.LowPriorityAction;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.Annotator;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo;
import com.intellij.openapi.editor.colors.CodeInsightColors;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.markup.EffectType;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.springmodulith.plugin.analyzer.ModulithDependencyAnalyzer;
import com.springmodulith.plugin.configuration.ModulithSettings;
import com.springmodulith.plugin.model.ModulithDependencyAnalysis;
import com.springmodulith.plugin.model.ModulithModule;
import com.springmodulith.plugin.model.NamedInterface;
import com.springmodulith.plugin.quickfix.AddAllowedDependencyFix;
import com.springmodulith.plugin.quickfix.ExposePackageAsNamedInterfaceFix;
import com.springmodulith.plugin.quickfix.SuppressModulithInspectionFix;
import com.springmodulith.plugin.resolver.ModulithModuleResolver;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.Color;
import java.util.Set;

public final class ModulithBoundaryAnnotator implements Annotator {

    private static final String SUPPRESSION_COMMENT =
            "//noinspection ModulithDependency";

    @Nullable
    private static IntentionAction createApiPopupFix(
            @NotNull ModulithDependencyAnalysis analysis) {

        PsiClass targetClass =
                analysis.targetClass();

        String qualifiedType =
                targetClass.getQualifiedName();

        if (qualifiedType == null
                || qualifiedType.isBlank()) {
            return null;
        }

        String targetPackage =
                packageName(targetClass);

        /*
         * The package should not be offered again when it is already
         * explicitly exposed through a named interface.
         *
         * OPEN modules expose packages implicitly, but that must not hide
         * the optional "Expose package ... as named interface ..." action.
         * Keep the root package behavior unchanged: the module root is
         * already part of the module's public surface and is not converted
         * into a named interface by this quick fix.
         */
        if (targetPackage.equals(analysis.target().getPackageName())
                || analysis.target().findNamedInterfaceForType(
                qualifiedType,
                targetPackage
        ) != null) {
            return null;
        }

        return new ExposePackageAsNamedInterfaceFix(
                targetPackage
        );
    }
    @Override
    public void annotate(
            @NotNull PsiElement element,
            @NotNull AnnotationHolder holder) {

        if (!(element instanceof PsiJavaCodeReferenceElement reference)) {
            return;
        }

        /*
         * Import references are handled by ModulithDependencyInspection.
         * Avoid creating duplicate annotations here.
         */
        if (reference.getParent() instanceof PsiImportStatement) {
            return;
        }

        Project project = element.getProject();

        ModulithSettings settings =
                ModulithSettings.getInstance(project);

        if (!settings.isInspectApiUsage()
                && !settings.isInspectAllowedDependencies()) {
            return;
        }

        ModulithDependencyAnalyzer analyzer =
                new ModulithDependencyAnalyzer(
                        new ModulithModuleResolver(project),
                        project
                );

        ModulithDependencyAnalysis analysis =
                analyzer.analyze(reference);

        if (analysis == null || !analysis.isForbidden()) {
            return;
        }

        boolean apiViolation =
                settings.isInspectApiUsage()
                        && analysis.apiViolation();

        boolean dependencyViolation =
                settings.isInspectAllowedDependencies()
                        && !isAllowedDependency(analysis);

        /*
         * The annotator creates its own highlighting, so it must honor
         * the suppression belonging to the specific violation type.
         */
        if (apiViolation && isSuppressed(
                reference,
                "//noinspection ModulithApiUsage")) {
            apiViolation = false;
        }

        if (dependencyViolation && isSuppressed(
                reference,
                "//noinspection ModulithDependency")) {
            dependencyViolation = false;
        }

        if (!apiViolation && !dependencyViolation) {
            return;
        }

        PsiElement nameElement =
                reference.getReferenceNameElement();

        if (nameElement == null) {
            return;
        }

        String message =
                createShortMessage(
                        analysis,
                        apiViolation,
                        dependencyViolation
                );

        String tooltip =
                createViolationTooltip(
                        analysis,
                        apiViolation,
                        dependencyViolation
                );

        var annotation = holder.newAnnotation(
                        HighlightSeverity.ERROR,
                        message
                )
                .range(nameElement)
                .tooltip(tooltip)
                .enforcedTextAttributes(
                        createRedUnderlineAttributes()
                );

        /*
         * Keep the dependency quick fix as the primary action whenever
         * the source module has an unallowed dependency.
         *
         * The API exposure action is deliberately added after it. This
         * makes it an additional action instead of replacing the
         * dependency fix. In particular, OPEN modules may have no API
         * violation while the dependency is still forbidden, so the API
         * action must be considered independently of apiViolation.
         */
        if (dependencyViolation) {

            IntentionAction dependencyFix =
                    createAllowedDependencyPopupFix(analysis);

            if (dependencyFix != null) {
                annotation.withFix(dependencyFix);
            }

            IntentionAction apiFix =
                    createApiPopupFix(analysis);

            if (apiFix != null) {
                annotation.withFix(
                        new LowPriorityApiPopupFix(apiFix)
                );
            }

        } else if (apiViolation) {

            IntentionAction apiFix =
                    createApiPopupFix(analysis);

            if (apiFix != null) {
                annotation.withFix(apiFix);
            }
        }

        /*
         * The local inspection also remains available for batch analysis,
         * but the editor annotation is the visual source of truth. Add
         * suppression for exactly the violation(s) shown here.
         */
        if (apiViolation && dependencyViolation) {
            annotation.withFix(
                    new SuppressModulithInspectionFix(
                            "ModulithDependency",
                            "ModulithApiUsage"
                    )
            );
        } else if (dependencyViolation) {
            annotation.withFix(
                    new SuppressModulithInspectionFix(
                            "ModulithDependency"
                    )
            );
        } else if (apiViolation
                && !hasAllowedModuleDependency(analysis)) {
            /*
             * When the module dependency is already allowed, API exposure
             * is the only actionable issue. Keep the popup focused on the
             * concrete API-exposure fix instead of offering suppression.
             */
            annotation.withFix(
                    new SuppressModulithInspectionFix(
                            "ModulithApiUsage"
                    )
            );
        }

        annotation.create();
    }

    private static boolean isAllowedDependency(
            @NotNull ModulithDependencyAnalysis analysis) {

        if (!analysis.source().isAllowedDependenciesConfigured()) {
            return true;
        }

        String qualifiedType = analysis.targetClass().getQualifiedName();
        if (qualifiedType == null) {
            return false;
        }

        String targetPackage = packageName(analysis.targetClass());

        for (String dependency : analysis.source().getAllowedDependencies()) {
            ModulithModule.DependencyRule rule =
                    ModulithModule.DependencyRule.parse(dependency);

            if (rule == null
                    || !analysis.target().matchesModuleId(rule.moduleId())) {
                continue;
            }

            // A module-level dependency permits the module dependency itself.
            // API exposure is checked separately by apiViolation(). This is
            // important for CLOSED modules:
            //   allowedDependencies = {"auth"}
            //   access auth.service.AuthenticationService
            // must be reported as an API violation, not as a second dependency
            // violation.
            if (rule.interfaceId() == null) {
                /*
                 * A bare module dependency exposes the target module's
                 * root API. An OPEN module exposes its packages implicitly,
                 * but a CLOSED module still requires a qualified named
                 * interface for non-root packages.
                 *
                 * Therefore, after a CLOSED module exposes a package such
                 * as "service", an existing "auth" dependency must still
                 * produce the dependency fix "auth :: service".
                 */
                if (analysis.target().isOpen()
                        || targetPackage.equals(analysis.target().getPackageName())) {
                    return true;
                }

                // A bare dependency is sufficient to reach a CLOSED
                // module, but the internal package is still an API
                // violation. Once that package is exposed, apiViolation()
                // becomes false and this method must return false so the
                // precise named-interface dependency (for example
                // "reporting :: service") can be suggested.
                return analysis.apiViolation();
            }

            // A wildcard dependency permits explicitly declared named
            // interfaces, while the API check still handles unexposed types.
            if ("*".equals(rule.interfaceId())) {
                if (analysis.target().findNamedInterfaceForType(
                        qualifiedType,
                        targetPackage
                ) != null) {
                    return true;
                }
                continue;
            }

            NamedInterface namedInterface =
                    analysis.target().findNamedInterface(rule.interfaceId());

            if (namedInterface != null
                    && namedInterface.contains(qualifiedType, targetPackage)) {
                return true;
            }
        }

        return false;
    }

    private static boolean hasAllowedModuleDependency(
            @NotNull ModulithDependencyAnalysis analysis) {

        if (!analysis.source().isAllowedDependenciesConfigured()) {
            return true;
        }

        for (String dependency : analysis.source().getAllowedDependencies()) {
            ModulithModule.DependencyRule rule =
                    ModulithModule.DependencyRule.parse(dependency);

            if (rule != null
                    && rule.interfaceId() == null
                    && analysis.target().matchesModuleId(rule.moduleId())) {
                return true;
            }
        }

        return false;
    }

    @NotNull
    private static String createShortMessage(
            @NotNull ModulithDependencyAnalysis analysis,
            boolean apiViolation,
            boolean dependencyViolation) {

        if (apiViolation && dependencyViolation) {
            return "Modulith API and dependency violation: "
                    + analysis.source().getName()
                    + " → "
                    + analysis.target().getName();
        }

        if (apiViolation) {
            return "Modulith API violation: "
                    + analysis.source().getName()
                    + " accesses internal type from "
                    + analysis.target().getName();
        }

        return "Modulith dependency is not allowed: "
                + analysis.source().getName()
                + " → "
                + analysis.target().getName();
    }

    @NotNull
    private static String createViolationTooltip(
            @NotNull ModulithDependencyAnalysis analysis,
            boolean apiViolation,
            boolean dependencyViolation) {

        ModulithModule source = analysis.source();
        ModulithModule target = analysis.target();

        String qualifiedType =
                analysis.targetClass().getQualifiedName();

        String targetPackage =
                packageName(analysis.targetClass());

        StringBuilder html =
                new StringBuilder();

        html.append("<html>");

        /*
         * Header
         */
        if (apiViolation && dependencyViolation) {
            html.append("<b>Modulith API and dependency violation</b>");
        } else if (apiViolation) {
            html.append("<b>Modulith API violation</b>");
        } else {
            html.append("<b>Modulith dependency violation</b>");
        }

        html.append("<br><br>");

        /*
         * Dependency path
         */
        html.append("<b>Dependency</b><br>");
        html.append("<code>")
                .append(escape(source.getName()))
                .append(" → ")
                .append(escape(target.getName()))
                .append("</code>");

        html.append("<br><br>");

        /*
         * Source module
         */
        html.append("<b>Source module</b><br>");
        html.append(escape(source.getName()));

        html.append("<br><br>");

        /*
         * Target module
         */
        html.append("<b>Target module</b><br>");
        html.append(escape(target.getName()));

        /*
         * Accessed type
         */
        if (qualifiedType != null
                && !qualifiedType.isBlank()) {

            html.append("<br><br>");
            html.append("<b>Accessed type</b><br>");
            html.append("<code>")
                    .append(escape(qualifiedType))
                    .append("</code>");
        }

        /*
         * Target package
         */
        if (!targetPackage.isBlank()) {

            html.append("<br><br>");
            html.append("<b>Target package</b><br>");
            html.append("<code>")
                    .append(escape(targetPackage))
                    .append("</code>");
        }

        /*
         * API violation explanation
         */
        if (apiViolation) {

            html.append("<br><br>");
            html.append("<b>Why this is a violation</b><br>");

            html.append(
                    "The accessed type is internal to the "
                            + "target module and is not exposed "
                            + "through its Modulith API."
            );

            NamedInterface exposedInterface =
                    qualifiedType == null
                            ? null
                            : target.findNamedInterfaceForType(
                            qualifiedType,
                            targetPackage
                    );

            if (exposedInterface != null) {

                html.append("<br><br>");
                html.append("<b>Target named interface</b><br>");
                html.append("<code>")
                        .append(
                                escape(
                                        exposedInterface.getName()
                                )
                        )
                        .append("</code>");
            }
        }

        /*
         * Dependency rule explanation
         */
        if (dependencyViolation) {

            html.append("<br><br>");
            html.append("<b>Why this is a violation</b><br>");

            html.append(
                    "The source module does not allow this "
                            + "dependency through its configured "
                            + "<code>allowedDependencies</code>."
            );

            Set<String> configured =
                    source.getAllowedDependencies();

            if (!configured.isEmpty()) {

                html.append("<br><br>");
                html.append("<b>Configured dependencies</b><br>");

                for (String dependency : configured) {

                    html.append("• ")
                            .append(escape(dependency))
                            .append("<br>");
                }

            } else {

                html.append("<br><br>");
                html.append("<b>Configured dependencies</b><br>");
                html.append("None");
            }
        }

        /*
         * Helpful rule suggestion
         */
        NamedInterface namedInterface =
                qualifiedType == null
                        ? null
                        : target.findNamedInterfaceForType(
                        qualifiedType,
                        targetPackage
                );

        if (namedInterface != null
                && dependencyViolation) {

            html.append("<br><br>");
            html.append("<b>Possible dependency rule</b><br>");

            html.append("<code>")
                    .append(escape(target.getName()))
                    .append(" :: ")
                    .append(
                            escape(
                                    namedInterface.getName()
                            )
                    )
                    .append("</code>");
        }

        html.append("</html>");

        return html.toString();
    }

    /**
     * Creates the quick fix displayed directly inside the
     * IntelliJ annotation/violation popup.
     *
     * This quick fix is only offered when:
     *
     * 1. The target type belongs to a recognized named interface.
     * 2. That named-interface dependency is not already configured.
     *
     * If the target type is not exposed through a named interface,
     * the API quick fix is responsible for exposing it.
     */
    @Nullable
    private static IntentionAction createAllowedDependencyPopupFix(
            @NotNull ModulithDependencyAnalysis analysis) {

        ModulithModule source =
                analysis.source();

        ModulithModule target =
                analysis.target();

        PsiClass targetClass =
                analysis.targetClass();

        String qualifiedType =
                targetClass.getQualifiedName();

        if (qualifiedType == null
                || qualifiedType.isBlank()) {
            return null;
        }

        String targetPackage =
                packageName(targetClass);

        /*
         * ---------------------------------------------------------
         * CASE 1:
         * Target type belongs to a named interface.
         *
         * Example:
         *
         *     student :: repository
         *     student :: controller
         *     student :: dto
         * ---------------------------------------------------------
         */
        NamedInterface namedInterface =
                target.findNamedInterfaceForType(
                        qualifiedType,
                        targetPackage
                );

        if (namedInterface != null) {

            String dependency =
                    dependencyModuleId(source, target, targetClass.getProject())
                            + " :: "
                            + namedInterface.getName();

            /*
             * Do not show the quick fix if the dependency
             * is already configured.
             */
            if (hasAllowedDependency(
                    source,
                    dependency
            )) {
                return null;
            }

            String fixName =
                    "Add '"
                            + dependency
                            + "' as an allowed dependency of the '"
                            + source.getName()
                            + "' module";

            return new AddAllowedDependencyFix(
                    dependency,
                    source.getPackageName(),
                    fixName
            );
        }

        /*
         * ---------------------------------------------------------
         * CASE 2:
         * Target type belongs directly to the module root package.
         *
         * Example:
         *
         *     org.example.final_test.student.StudentService
         *
         * Module:
         *
         *     student
         *
         * Dependency:
         *
         *     student
         *
         * This is the normal Spring Modulith root API dependency.
         * ---------------------------------------------------------
         */
        boolean isRootApiType =
                targetPackage.equals(
                        target.getPackageName()
                );

        if (isRootApiType) {

            String dependency =
                    dependencyModuleId(source, target, targetClass.getProject());

            /*
             * Do not offer:
             *
             *     Add 'student'
             *
             * if it already exists.
             */
            if (hasAllowedDependency(
                    source,
                    dependency
            )) {
                return null;
            }

            String fixName =
                    "Add '"
                            + dependency
                            + "' as an allowed dependency of the '"
                            + source.getName()
                            + "' module";

            return new AddAllowedDependencyFix(
                    dependency,
                    source.getPackageName(),
                    fixName
            );
        }

        /*
         * ---------------------------------------------------------
         * CASE 3:
         * Target type is inside the target module but is not
         * associated with a named interface.
         *
         * When the target module is not represented by a named
         * interface for this type, still offer the module-level
         * dependency rule. This is especially useful for OPEN
         * modules, where the type is accessible but the source
         * module still has to explicitly allow the dependency.
         *
         * Example:
         *
         *     auth -> account.api.AccountApi
         *
         * Suggest:
         *
         *     Add 'account' as an allowed dependency of the 'auth' module
         * ---------------------------------------------------------
         */
        String dependency = dependencyModuleId(source, target, targetClass.getProject());

        if (hasAllowedDependency(
                source,
                dependency
        )) {
            return null;
        }

        String fixName =
                "Add '"
                        + dependency
                        + "' as an allowed dependency of the '"
                        + source.getName()
                        + "' module";

        return new AddAllowedDependencyFix(
                dependency,
                source.getPackageName(),
                fixName
        );
    }

    /**
     * Keeps the optional API-exposure action available without allowing it
     * to replace the dependency action as the primary quick fix.
     * IntelliJ uses {@link LowPriorityAction} to place this action lower in
     * the available fixes, which keeps it under the secondary actions when
     * the dependency fix is present.
     */
    private static final class LowPriorityApiPopupFix
            implements IntentionAction, IntentionActionDelegate, LowPriorityAction {

        private final IntentionAction delegate;

        private LowPriorityApiPopupFix(
                @NotNull IntentionAction delegate) {
            this.delegate = delegate;
        }

        @Override
        public @NotNull IntentionAction getDelegate() {
            return delegate;
        }

        @Override
        public @NotNull String getText() {
            return delegate.getText();
        }

        @Override
        public @NotNull String getFamilyName() {
            return delegate.getFamilyName();
        }

        @Override
        public boolean isAvailable(
                @NotNull Project project,
                com.intellij.openapi.editor.Editor editor,
                @NotNull PsiFile file) {
            return delegate.isAvailable(project, editor, file);
        }

        @Override
        public void invoke(
                @NotNull Project project,
                com.intellij.openapi.editor.Editor editor,
                @NotNull PsiFile file) {
            delegate.invoke(project, editor, file);
        }

        @Override
        public boolean startInWriteAction() {
            return delegate.startInWriteAction();
        }

        @Override
        public @NotNull IntentionPreviewInfo generatePreview(
                @NotNull Project project,
                @NotNull com.intellij.openapi.editor.Editor editor,
                @NotNull PsiFile file) {
            return delegate.generatePreview(project, editor, file);
        }
    }

    /**
     * Returns the dependency module identifier used in allowedDependencies.
     *
     * Top-level modules keep their existing short module name. Nested module
     * dependencies are represented by their logical module path, not by the
     * Java package's fully qualified name.
     */
    @NotNull
    private static String dependencyModuleId(
            @NotNull ModulithModule source,
            @NotNull ModulithModule target,
            @NotNull Project project) {

        String sourcePackage = source.getPackageName();
        String targetPackage = target.getPackageName();

        if (!targetPackage.startsWith(sourcePackage + ".")) {
            return target.getName();
        }

        String nestedPath =
                targetPackage.substring(sourcePackage.length() + 1);

        /*
         * Build the logical module path from recognized Modulith parent
         * packages. This deliberately avoids using the Java application's
         * fully qualified package prefix.
         *
         * Examples:
         *   account -> account.nested
         *   account.nested -> account.nested.deepNested1
         *   account.nested.deepNested1 ->
         *       account.nested.deepNested1.deepNested2
         */
        java.util.LinkedList<String> modulePath =
                new java.util.LinkedList<>();
        modulePath.addFirst(source.getName());

        String parentPackage = sourcePackage;
        String sourceSuffix = "." + source.getName();

        if (parentPackage.endsWith(sourceSuffix)) {
            parentPackage =
                    parentPackage.substring(
                            0,
                            parentPackage.length() - sourceSuffix.length()
                    );
        }

        while (isApplicationModulePackage(project, parentPackage)) {
            int lastDot = parentPackage.lastIndexOf('.');
            String parentModuleName =
                    lastDot >= 0
                            ? parentPackage.substring(lastDot + 1)
                            : parentPackage;

            modulePath.addFirst(parentModuleName);

            int parentLastDot = parentPackage.lastIndexOf('.');
            if (parentLastDot < 0) {
                break;
            }

            String parentOfParent =
                    parentPackage.substring(0, parentLastDot);

            if (parentOfParent.equals(parentPackage)) {
                break;
            }

            parentPackage = parentOfParent;
        }

        return String.join(".", modulePath)
                + "."
                + nestedPath;
    }

    private static boolean isApplicationModulePackage(
            @NotNull Project project,
            @NotNull String packageName) {

        com.intellij.psi.PsiPackage psiPackage =
                JavaPsiFacade.getInstance(project)
                        .findPackage(packageName);

        if (psiPackage == null) {
            return false;
        }

        for (com.intellij.psi.PsiDirectory directory :
                psiPackage.getDirectories()) {

            PsiFile packageInfo =
                    directory.findFile("package-info.java");

            if (!(packageInfo instanceof PsiJavaFile javaFile)) {
                continue;
            }

            for (PsiClass psiClass : javaFile.getClasses()) {
                if (psiClass.getModifierList() != null
                        && psiClass.getModifierList().findAnnotation(
                        "org.springframework.modulith.ApplicationModule"
                ) != null) {
                    return true;
                }
            }
        }

        return false;
    }

    private static boolean hasAllowedDependency(
            @NotNull ModulithModule source,
            @NotNull String dependency) {

        ModulithModule.DependencyRule expected =
                ModulithModule.DependencyRule.parse(
                        dependency
                );

        if (expected == null) {
            return false;
        }

        for (String configuredDependency :
                source.getAllowedDependencies()) {

            ModulithModule.DependencyRule configured =
                    ModulithModule.DependencyRule.parse(
                            configuredDependency
                    );

            if (configured == null) {
                continue;
            }

            /*
             * Module must match.
             */
            if (!expected.moduleId()
                    .equals(configured.moduleId())) {
                continue;
            }

            /*
             * "student" and "student :: repository"
             * are different rules.
             */
            if (expected.interfaceId() == null) {

                if (configured.interfaceId() == null) {
                    return true;
                }

                continue;
            }

            /*
             * Exact named-interface dependency.
             */
            if (expected.interfaceId()
                    .equals(configured.interfaceId())) {

                return true;
            }
        }

        return false;
    }

    private static boolean isSuppressed(
            @NotNull PsiElement element,
            @NotNull String suppressionMarker) {

        PsiElement target =
                findSuppressionTarget(element);

        if (target == null) {
            return false;
        }

        PsiElement previous =
                target.getPrevSibling();

        while (previous != null) {

            if (previous instanceof PsiWhiteSpace) {
                previous = previous.getPrevSibling();
                continue;
            }

            String text =
                    previous.getText();

            return text != null
                    && text.contains(suppressionMarker);
        }

        return false;
    }

    @Nullable
    private static PsiElement findSuppressionTarget(
            @NotNull PsiElement element) {

        PsiImportStatement importStatement =
                PsiTreeUtil.getParentOfType(
                        element,
                        PsiImportStatement.class
                );

        if (importStatement != null) {
            return importStatement;
        }

        PsiStatement statement =
                PsiTreeUtil.getParentOfType(
                        element,
                        PsiStatement.class
                );

        if (statement != null) {
            return statement;
        }

        PsiField field =
                PsiTreeUtil.getParentOfType(
                        element,
                        PsiField.class
                );

        if (field != null) {
            return field;
        }

        PsiMethod method =
                PsiTreeUtil.getParentOfType(
                        element,
                        PsiMethod.class
                );

        if (method != null) {
            return method;
        }

        PsiClass psiClass =
                PsiTreeUtil.getParentOfType(
                        element,
                        PsiClass.class
                );

        if (psiClass != null) {
            return psiClass;
        }

        return element;
    }

    @NotNull
    private static String packageName(
            @NotNull PsiClass psiClass) {

        if (psiClass.getContainingFile()
                instanceof PsiJavaFile javaFile) {

            return javaFile.getPackageName();
        }

        return "";
    }

    @NotNull
    private static String escape(
            @NotNull String value) {

        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    @NotNull
    private static TextAttributes createRedUnderlineAttributes() {

        EditorColorsScheme scheme =
                EditorColorsManager
                        .getInstance()
                        .getGlobalScheme();

        TextAttributes errorAttributes =
                scheme.getAttributes(
                        CodeInsightColors.ERRORS_ATTRIBUTES
                );

        Color effectColor =
                errorAttributes == null
                        ? com.intellij.ui.JBColor.RED
                        : errorAttributes.getEffectColor();

        if (effectColor == null) {
            effectColor = com.intellij.ui.JBColor.RED;
        }

        TextAttributes attributes =
                new TextAttributes();

        attributes.setEffectType(
                EffectType.WAVE_UNDERSCORE
        );

        attributes.setEffectColor(effectColor);

        return attributes;
    }
}