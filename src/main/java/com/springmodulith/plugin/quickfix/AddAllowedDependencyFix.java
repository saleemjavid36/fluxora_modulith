package com.springmodulith.plugin.quickfix;

import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.ide.highlighter.JavaFileType;
import com.springmodulith.plugin.resolver.ModulithModuleResolver;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import com.intellij.codeInspection.IntentionAndQuickFixAction;
import com.intellij.openapi.editor.Editor;
import com.springmodulith.plugin.model.ModulithModule;

public final class AddAllowedDependencyFix
        extends IntentionAndQuickFixAction {

    private final String dependency;
    private final String sourceModulePackage;
    private final String customName;

    public AddAllowedDependencyFix(
            @NotNull String dependency,
            @NotNull String sourceModulePackage) {

        this(
                dependency,
                sourceModulePackage,
                null
        );
    }

    public AddAllowedDependencyFix(
            @NotNull String dependency,
            @NotNull String sourceModulePackage,
            @Nullable String customName) {

        this.dependency = dependency;
        this.sourceModulePackage = sourceModulePackage;
        this.customName = customName;
    }

    @Override
    public @NotNull String getName() {

        if (customName != null
                && !customName.isBlank()) {

            return customName;
        }

        return "Allow dependency '"
                + dependency
                + "'";
    }

    @Override
    public @NotNull String getFamilyName() {
        return "Spring Modulith";
    }

    @Override
    public boolean startInWriteAction() {
        return false;
    }

    @Override
    public void applyFix(
            @NotNull Project project,
            @NotNull PsiFile file,
            @Nullable Editor editor) {

        applyDependencyChange(project);
    }

    private boolean dependencyAlreadyConfigured(
            @NotNull PsiAnnotation applicationModule) {

        PsiAnnotationMemberValue value =
                applicationModule.findDeclaredAttributeValue(
                        "allowedDependencies"
                );

        if (!(value instanceof PsiArrayInitializerMemberValue)) {
            return false;
        }

        PsiArrayInitializerMemberValue arrayValue =
                (PsiArrayInitializerMemberValue) value;

        ModulithModule.DependencyRule requested =
                ModulithModule.DependencyRule.parse(
                        dependency
                );

        for (PsiLiteralExpression literal :
                PsiTreeUtil.findChildrenOfType(
                        arrayValue,
                        PsiLiteralExpression.class
                )) {

            Object literalValue =
                    literal.getValue();

            if (!(literalValue instanceof String)) {
                continue;
            }

            String existingDependency =
                    ((String) literalValue).trim();

            /*
             * Exact match.
             */
            if (dependency.trim().equals(existingDependency)) {
                return true;
            }

            /*
             * Logical Modulith dependency match.
             */
            if (requested == null) {
                continue;
            }

            ModulithModule.DependencyRule existing =
                    ModulithModule.DependencyRule.parse(
                            existingDependency
                    );

            if (existing == null) {
                continue;
            }

            if (!requested.moduleId()
                    .equals(existing.moduleId())) {
                continue;
            }

            /*
             * Root dependency:
             *
             * student
             *
             * must match only:
             *
             * student
             */
            if (requested.interfaceId() == null) {

                if (existing.interfaceId() == null) {
                    return true;
                }

                continue;
            }

            /*
             * Named dependency:
             *
             * student :: controller
             *
             * must match only the same named dependency.
             */
            if (requested.interfaceId()
                    .equals(existing.interfaceId())) {

                return true;
            }
        }

        return false;
    }

    private void applyDependencyChange(
            @NotNull Project project) {

        PsiDirectory moduleDirectory =
                findPackageDirectory(
                        project,
                        sourceModulePackage
                );

        if (moduleDirectory == null) {
            return;
        }

        WriteCommandAction
                .writeCommandAction(project)
                .withName("Allow Modulith dependency")
                .run(() -> {

                    PsiJavaFile packageInfo =
                            findPackageInfo(moduleDirectory);

                    if (packageInfo == null) {

                        PsiElementFactory factory =
                                PsiElementFactory
                                        .getInstance(project);

                        createPackageInfo(
                                project,
                                moduleDirectory,
                                factory
                        );

                        packageInfo =
                                findPackageInfo(moduleDirectory);

                        if (packageInfo == null) {
                            return;
                        }
                    }

                    PsiAnnotation applicationModule =
                            findApplicationModule(packageInfo);

                    if (applicationModule != null
                            && applicationModule.isValid()) {

                        /*
                         * FINAL DUPLICATE CHECK
                         *
                         * Never modify package-info.java when the
                         * requested dependency already exists.
                         */
                        if (dependencyAlreadyConfigured(applicationModule)) {
                            return;
                        }

                        addDependencyToAnnotation(
                                project,
                                applicationModule
                        );

                        return;
                    }

                    PsiPackageStatement packageStatement =
                            packageInfo.getPackageStatement();

                    if (packageStatement == null) {
                        return;
                    }

                    addApplicationModuleAnnotation(
                            project,
                            packageStatement
                    );
                });
    }

    private void addDependencyToAnnotation(
            @NotNull Project project,
            @NotNull PsiAnnotation annotation) {

        PsiElementFactory factory =
                PsiElementFactory.getInstance(project);

        PsiAnnotationMemberValue value =
                annotation.findDeclaredAttributeValue(
                        "allowedDependencies"
                );

        String annotationText =
                annotation.getText();

        /*
         * allowedDependencies does not exist yet.
         */
        if (value == null) {

            /*
             * The attribute may exist in the source text but IntelliJ
             * may not be able to build a PSI value when the array is
             * malformed (for example, the closing '} ' was removed).
             * Repair that source form instead of creating a second
             * allowedDependencies attribute.
             */
            if (annotationText.contains("allowedDependencies")) {
                if (repairMalformedAllowedDependencies(project, annotation)) {
                    return;
                }
            }

            int closeParen =
                    annotationText.lastIndexOf(')');

            if (closeParen < 0) {
                return;
            }

            String newAnnotationText =
                    annotationText.substring(
                            0,
                            closeParen
                    )
                            + "allowedDependencies = {\""
                            + dependency
                            + "\"}"
                            + annotationText.substring(
                            closeParen
                    );

            PsiAnnotation replacement =
                    factory.createAnnotationFromText(
                            newAnnotationText,
                            annotation
                    );

            annotation.replace(replacement);
            return;
        }

        /*
         * If IntelliJ could not build a normal PSI array because
         * the user has accidentally removed the closing '}',
         * repair the malformed allowedDependencies declaration
         * before doing the normal dependency insertion.
         */
        if (!(value instanceof PsiArrayInitializerMemberValue)) {

            repairMalformedAllowedDependencies(
                    project,
                    annotation
            );

            return;
        }

        PsiArrayInitializerMemberValue arrayValue =
                (PsiArrayInitializerMemberValue) value;

        /*
         * Safety check:
         *
         * Never add the same dependency twice.
         */
        if (containsDependencyLiteral(arrayValue)) {
            return;
        }

        String valueText =
                arrayValue.getText();

        int closingBrace =
                valueText.lastIndexOf('}');

        if (closingBrace < 0) {
            /*
             * PSI can still expose an array value even though the
             * source is missing its closing brace. Repair the actual
             * source text and add the requested dependency in the same
             * operation.
             */
            repairMalformedAllowedDependencies(
                    project,
                    annotation
            );
            return;
        }

        /*
         * Determine whether the existing array is formatted
         * across multiple lines.
         */
        boolean multiline =
                valueText.indexOf('\n') >= 0
                        || valueText.indexOf('\r') >= 0;

        /*
         * Empty array:
         *
         * allowedDependencies = {}
         */
        if (arrayValue.getInitializers().length == 0) {

            /*
             * A PSI array with only comments has no initializers.
             * Do not rebuild that array, because doing so would erase
             * the user's commented dependency examples.
             */
            if (multiline && containsCommentOnlyContent(valueText)) {
                String newValueText =
                        insertDependencyIntoCommentOnlyArray(
                                valueText,
                                getElementIndent(valueText, closingBrace),
                                dependency
                        );

                if (newValueText == null) {
                    return;
                }

                replaceAnnotationArrayValue(
                        factory,
                        annotation,
                        annotationText,
                        valueText,
                        newValueText
                );

                return;
            }

            String newValueText;

            if (multiline) {

                String closingIndent =
                        getClosingBraceIndent(
                                valueText,
                                closingBrace
                        );

                String elementIndent =
                        getElementIndent(
                                valueText,
                                closingBrace
                        );

                newValueText =
                        "{\n"
                                + elementIndent
                                + "\""
                                + dependency
                                + "\"\n"
                                + closingIndent
                                + "}";

            } else {

                newValueText =
                        "{\""
                                + dependency
                                + "\"}";
            }

            replaceAnnotationArrayValue(
                    factory,
                    annotation,
                    annotationText,
                    valueText,
                    newValueText
            );

            return;
        }

        /*
         * Existing dependencies are present.
         */
        if (multiline) {

            /*
             * Example existing value:
             *
             * {
             *     "student :: dto",
             *     "student"
             * }
             *
             * We want:
             *
             * {
             *     "student :: dto",
             *     "student",
             *     "student :: repository"
             * }
             */

            String closingIndent =
                    getClosingBraceIndent(
                            valueText,
                            closingBrace
                    );

            String elementIndent =
                    getElementIndent(
                            valueText,
                            closingBrace
                    );

            /*
             * Remove whitespace immediately before the
             * closing brace.
             *
             * This prevents:
             *
             * "student"
             *     ,
             *
             * from being generated.
             */
            String newValueText =
                    insertMultilineDependencyPreservingComments(
                            valueText,
                            arrayValue,
                            closingBrace,
                            elementIndent,
                            dependency
                    );

            if (newValueText == null) {
                return;
            }

            replaceAnnotationArrayValue(
                    factory,
                    annotation,
                    annotationText,
                    valueText,
                    newValueText
            );

            return;
        }

        /*
         * Single-line array:
         *
         * allowedDependencies = {"student"}
         *
         * becomes:
         *
         * allowedDependencies = {
         *     "student",
         *     "student :: repository"
         * }
         *
         * Actually, to avoid unnecessarily changing the
         * user's existing style, keep it single-line:
         *
         * allowedDependencies = {"student", "student :: repository"}
         */
        String contentBeforeClosing =
                valueText.substring(
                        0,
                        closingBrace
                );

        int lastNonWhitespace =
                findLastNonWhitespace(
                        contentBeforeClosing
                );

        if (lastNonWhitespace < 0) {
            return;
        }

        String content =
                contentBeforeClosing.substring(
                        0,
                        lastNonWhitespace + 1
                );

        String newValueText =
                content
                        + ", \""
                        + dependency
                        + "\"}";

        replaceAnnotationArrayValue(
                factory,
                annotation,
                annotationText,
                valueText,
                newValueText
        );
    }
    private static boolean containsCommentOnlyContent(
            @NotNull String valueText) {

        int openingBrace = valueText.indexOf('{');
        int closingBrace = valueText.lastIndexOf('}');

        if (openingBrace < 0 || closingBrace <= openingBrace) {
            return false;
        }

        String content =
                valueText.substring(
                        openingBrace + 1,
                        closingBrace
                );

        return content.contains("//")
                || content.contains("/*")
                || content.contains("*");
    }

    private static @Nullable String insertDependencyIntoCommentOnlyArray(
            @NotNull String valueText,
            @NotNull String elementIndent,
            @NotNull String dependency) {

        int openingBrace = valueText.indexOf('{');
        if (openingBrace < 0) {
            return null;
        }

        return valueText.substring(0, openingBrace + 1)
                + "\n"
                + elementIndent
                + "\""
                + dependency
                + "\"\n"
                + valueText.substring(openingBrace + 1);
    }

    private static @Nullable String insertMultilineDependencyPreservingComments(
            @NotNull String valueText,
            @NotNull PsiArrayInitializerMemberValue arrayValue,
            int closingBrace,
            @NotNull String elementIndent,
            @NotNull String dependency) {

        PsiAnnotationMemberValue[] initializers = arrayValue.getInitializers();
        if (initializers.length == 0) {
            return null;
        }

        int arrayStart = arrayValue.getTextRange().getStartOffset();
        int lastInitializerEnd =
                initializers[initializers.length - 1].getTextRange().getEndOffset()
                        - arrayStart;

        if (lastInitializerEnd < 0 || lastInitializerEnd > closingBrace) {
            return null;
        }

        int comma = -1;
        for (int i = lastInitializerEnd; i < closingBrace; i++) {
            char c = valueText.charAt(i);
            if (Character.isWhitespace(c)) {
                continue;
            }
            if (c == ',') {
                comma = i;
            }
            break;
        }

        if (comma >= 0) {
            return valueText.substring(0, comma + 1)
                    + "\n"
                    + elementIndent
                    + "\""
                    + dependency
                    + "\""
                    + valueText.substring(comma + 1);
        }

        return valueText.substring(0, lastInitializerEnd)
                + ",\n"
                + elementIndent
                + "\""
                + dependency
                + "\""
                + valueText.substring(lastInitializerEnd);
    }

    private static void replaceAnnotationArrayValue(
            @NotNull PsiElementFactory factory,
            @NotNull PsiAnnotation annotation,
            @NotNull String annotationText,
            @NotNull String oldValueText,
            @NotNull String newValueText) {

        int valueStart =
                annotationText.indexOf(oldValueText);

        if (valueStart < 0) {
            return;
        }

        String newAnnotationText =
                annotationText.substring(
                        0,
                        valueStart
                )
                        + newValueText
                        + annotationText.substring(
                        valueStart + oldValueText.length()
                );

        PsiAnnotation replacement =
                factory.createAnnotationFromText(
                        newAnnotationText,
                        annotation
                );

        annotation.replace(replacement);
    }
    private static int findLastNonWhitespace(
            @NotNull String text) {

        for (int i = text.length() - 1; i >= 0; i--) {

            if (!Character.isWhitespace(
                    text.charAt(i))) {

                return i;
            }
        }

        return -1;
    }
    private static String getClosingBraceIndent(
            @NotNull String valueText,
            int closingBrace) {

        int lineStart =
                valueText.lastIndexOf(
                        '\n',
                        closingBrace - 1
                );

        if (lineStart < 0) {
            return "";
        }

        int start =
                lineStart + 1;

        int end =
                closingBrace;

        String indentation =
                valueText.substring(
                        start,
                        end
                );

        /*
         * The text between the last newline and '}'
         * should normally contain only indentation.
         */
        if (indentation.trim().isEmpty()) {
            return indentation;
        }

        return "";
    }
    private static String getElementIndent(
            @NotNull String valueText,
            int closingBrace) {

        /*
         * Find the line containing the last existing
         * dependency.
         */
        int lastNewLine =
                valueText.lastIndexOf(
                        '\n',
                        closingBrace - 1
                );

        if (lastNewLine < 0) {
            return "    ";
        }

        /*
         * Find the newline before that line.
         */
        int previousNewLine =
                valueText.lastIndexOf(
                        '\n',
                        lastNewLine - 1
                );

        int lineStart =
                previousNewLine < 0
                        ? 0
                        : previousNewLine + 1;

        /*
         * Extract indentation from the existing
         * dependency line.
         */
        String line =
                valueText.substring(
                        lineStart,
                        lastNewLine
                );

        int firstNonWhitespace = 0;

        while (firstNonWhitespace < line.length()
                && Character.isWhitespace(
                line.charAt(firstNonWhitespace))) {

            firstNonWhitespace++;
        }

        if (firstNonWhitespace > 0) {
            return line.substring(
                    0,
                    firstNonWhitespace
            );
        }

        /*
         * Fallback.
         */
        return "    ";
    }
    private static @Nullable PsiJavaFile findPackageInfo(
            @NotNull PsiDirectory directory) {

        PsiFile file =
                directory.findFile(
                        "package-info.java"
                );

        if (file instanceof PsiJavaFile) {
            return (PsiJavaFile) file;
        }

        return null;
    }
    private static String extractLeadingWhitespace(
            @NotNull String line) {

        int index = 0;

        while (index < line.length()
                && Character.isWhitespace(line.charAt(index))) {

            index++;
        }

        return line.substring(0, index);
    }

    private static String findDependencyIndentation(
            @NotNull String content) {

        String[] lines = content.split("\\R", -1);

        /*
         * Find the indentation of the last non-empty line
         * containing an allowed dependency.
         */
        for (int i = lines.length - 1; i >= 0; i--) {

            String line = lines[i];

            if (line.trim().isEmpty()) {
                continue;
            }

            String trimmed = line.trim();

            /*
             * Dependency entries normally look like:
             *
             * "student :: repository"
             * "student"
             */
            if (trimmed.startsWith("\"")
                    || trimmed.endsWith("\",")
                    || trimmed.endsWith("\"")) {

                String indentation =
                        extractLeadingWhitespace(line);

                if (!indentation.isEmpty()) {
                    return indentation;
                }
            }
        }

        /*
         * Fallback used when no dependency line can be found.
         */
        return "    ";
    }

    private void createPackageInfo(
            @NotNull Project project,
            @NotNull PsiDirectory directory,
            @NotNull PsiElementFactory factory) {

        String text =
                "@org.springframework.modulith.ApplicationModule(" +
                        "allowedDependencies = {\"" +
                        dependency +
                        "\"})\n" +
                        "package " +
                        sourceModulePackage +
                        ";\n";

        PsiJavaFile packageInfo =
                (PsiJavaFile) PsiFileFactory
                        .getInstance(project)
                        .createFileFromText(
                                "package-info.java",
                                JavaFileType.INSTANCE,
                                text
                        );

        directory.add(packageInfo);
    }

    private static @Nullable PsiAnnotation findApplicationModule(
            @NotNull PsiJavaFile javaFile) {

        PsiPackageStatement packageStatement =
                javaFile.getPackageStatement();

        if (packageStatement == null) {
            return null;
        }

        PsiModifierList annotationList =
                packageStatement.getAnnotationList();

        if (annotationList == null) {
            return null;
        }

        for (PsiAnnotation annotation :
                annotationList.getAnnotations()) {

            if (isApplicationModule(annotation)) {
                return annotation;
            }
        }

        return null;
    }

    private static boolean isApplicationModule(
            @NotNull PsiAnnotation annotation) {

        String qualifiedName =
                annotation.getQualifiedName();

        return "ApplicationModule".equals(qualifiedName)
                || "org.springframework.modulith.ApplicationModule"
                .equals(qualifiedName);
    }

    private void addApplicationModuleAnnotation(
            @NotNull Project project,
            @NotNull PsiPackageStatement packageStatement) {

        PsiElementFactory factory =
                PsiElementFactory.getInstance(project);

        PsiAnnotation annotation =
                factory.createAnnotationFromText(
                        "@org.springframework.modulith.ApplicationModule(" +
                                "allowedDependencies = {\"" +
                                dependency +
                                "\"})",
                        packageStatement
                );

        PsiModifierList annotationList =
                packageStatement.getAnnotationList();

        if (annotationList != null) {

            annotationList.addBefore(
                    annotation,
                    annotationList.getFirstChild()
            );

        } else {

            packageStatement.addBefore(
                    annotation,
                    packageStatement.getFirstChild()
            );
        }
    }
    private boolean repairMalformedAllowedDependencies(
            @NotNull Project project,
            @NotNull PsiAnnotation annotation) {

        String annotationText = annotation.getText();

        int allowedDependenciesIndex =
                annotationText.indexOf("allowedDependencies");

        if (allowedDependenciesIndex < 0) {
            return false;
        }

        int openingBrace =
                annotationText.indexOf(
                        '{',
                        allowedDependenciesIndex
                );

        if (openingBrace < 0) {
            return false;
        }

        /*
         * Find a real closing brace while ignoring braces
         * inside string literals.
         */
        int closingBrace =
                findClosingBrace(
                        annotationText,
                        openingBrace
                );

        /*
         * A valid closing brace already exists.
         * Let the normal existing logic handle it.
         */
        if (closingBrace >= 0) {
            return false;
        }

        /*
         * The allowedDependencies array is malformed:
         *
         * allowedDependencies = {
         *     "student :: repository"
         * )
         *
         * We need to repair it and add the requested dependency.
         */

        int closingParen =
                annotationText.lastIndexOf(')');

        if (closingParen < 0
                || closingParen <= openingBrace) {

            return false;
        }

        String content =
                annotationText.substring(
                        openingBrace + 1,
                        closingParen
                );

        /*
         * Check whether the dependency already exists
         * even though the annotation is syntactically broken.
         */
        if (containsDependencyText(content)) {

            String repairedText =
                    annotationText.substring(
                            0,
                            closingParen
                    )
                            + "\n}"
                            + annotationText.substring(
                            closingParen
                    );

            PsiElementFactory factory =
                    PsiElementFactory.getInstance(project);

            PsiAnnotation replacement =
                    factory.createAnnotationFromText(
                            repairedText,
                            annotation
                    );

            annotation.replace(replacement);

            return true;
        }

        /*
         * Determine whether there is already an actual
         * dependency inside the array.
         */
        boolean hasExistingDependency =
                !content.trim().isEmpty();

        /*
         * Preserve the user's existing formatting as much
         * as possible.
         */
        boolean multiline =
                content.contains("\n")
                        || content.contains("\r");

        String newContent;

        if (multiline) {

            int lastNewLine =
                    Math.max(
                            content.lastIndexOf('\n'),
                            content.lastIndexOf('\r')
                    );

            String beforeLastLine =
                    content.substring(
                            0,
                            lastNewLine + 1
                    );

            String lastLine =
                    content.substring(
                            lastNewLine + 1
                    );

            String indentation =
                    extractLeadingWhitespace(lastLine);

            /*
             * If the text immediately before the missing
             * brace is only whitespace, use the indentation
             * of the previous dependency line.
             */
            if (lastLine.trim().isEmpty()) {

                indentation =
                        findDependencyIndentation(
                                content
                        );
            }

            String trimmedContent =
                    content.stripTrailing();

            /*
             * The malformed source may already contain the comma
             * belonging to its last dependency. Do not add another
             * comma in that case.
             */
            boolean hasTrailingComma =
                    trimmedContent.endsWith(",");

            String separator =
                    hasExistingDependency && !hasTrailingComma
                            ? ","
                            : "";

            String dependencyIndent =
                    findDependencyIndentation(
                            content
                    );

            if (dependencyIndent.isEmpty()) {
                dependencyIndent = indentation;
            }

            newContent =
                    trimmedContent
                            + separator
                            + "\n"
                            + dependencyIndent
                            + "\""
                            + dependency
                            + "\"\n";

        } else {

            String trimmedContent =
                    content.trim();

            /*
             * If the last existing dependency already has its comma,
             * reuse it instead of producing ",, ".
             */
            boolean hasTrailingComma =
                    trimmedContent.endsWith(",");

            String separator =
                    hasExistingDependency && !hasTrailingComma
                            ? ", "
                            : hasExistingDependency
                            ? " "
                            : "";

            newContent =
                    trimmedContent
                            + separator
                            + "\""
                            + dependency
                            + "\"";
        }

        String repairedText =
                annotationText.substring(
                        0,
                        openingBrace + 1
                )
                        + newContent
                        + "}"
                        + annotationText.substring(
                        closingParen
                );

        PsiElementFactory factory =
                PsiElementFactory.getInstance(project);

        PsiAnnotation replacement =
                factory.createAnnotationFromText(
                        repairedText,
                        annotation
                );

        annotation.replace(replacement);

        return true;
    }
    private static int findClosingBrace(
            @NotNull String text,
            int openingBrace) {

        boolean insideString = false;
        boolean escaped = false;

        for (int i = openingBrace + 1;
             i < text.length();
             i++) {

            char current =
                    text.charAt(i);

            if (insideString) {

                if (escaped) {
                    escaped = false;
                    continue;
                }

                if (current == '\\') {
                    escaped = true;
                    continue;
                }

                if (current == '"') {
                    insideString = false;
                }

                continue;
            }

            if (current == '"') {
                insideString = true;
                continue;
            }

            if (current == '}') {
                return i;
            }
        }

        return -1;
    }

    private boolean containsDependencyText(
            @NotNull String content) {

        String normalizedRequested =
                dependency
                        .trim()
                        .replaceAll("\\s*::\\s*", " :: ");

        String[] parts =
                content.split(",");

        for (String part : parts) {

            String existing =
                    part
                            .trim()
                            .replace("\"", "")
                            .replaceAll(
                                    "\\s*::\\s*",
                                    " :: "
                            );

            if (normalizedRequested.equals(existing)) {
                return true;
            }
        }

        return false;
    }
    private static PsiAnnotationMemberValue createTemporaryAnnotationValue(
            @NotNull String content) {

        PsiElementFactory factory =
                PsiElementFactory.getInstance(
                        com.intellij.openapi.project.ProjectManager
                                .getInstance()
                                .getDefaultProject()
                );

        PsiAnnotation annotation =
                factory.createAnnotationFromText(
                        "@ApplicationModule(allowedDependencies = {"
                                + content
                                + "})",
                        null
                );

        return annotation.findDeclaredAttributeValue(
                "allowedDependencies"
        );
    }

    private boolean containsDependencyLiteral(
            @NotNull PsiAnnotationMemberValue value) {

        ModulithModule.DependencyRule requested =
                ModulithModule.DependencyRule.parse(
                        dependency
                );

        for (PsiLiteralExpression literal :
                PsiTreeUtil.findChildrenOfType(
                        value,
                        PsiLiteralExpression.class
                )) {

            Object literalValue =
                    literal.getValue();

            if (!(literalValue instanceof String)) {
                continue;
            }

            String existingDependency =
                    ((String) literalValue).trim();

            /*
             * First perform an exact string comparison.
             *
             * This handles the normal case:
             *
             * "student :: controller"
             * "student :: controller"
             */
            if (dependency.trim().equals(existingDependency)) {
                return true;
            }

            /*
             * Then compare the parsed Modulith rule.
             *
             * This protects against formatting differences such as:
             *
             * "student::controller"
             * "student :: controller"
             *
             * if DependencyRule.parse() considers them
             * the same logical dependency.
             */
            if (requested == null) {
                continue;
            }

            ModulithModule.DependencyRule existing =
                    ModulithModule.DependencyRule.parse(
                            existingDependency
                    );

            if (existing == null) {
                continue;
            }

            if (!requested.moduleId()
                    .equals(existing.moduleId())) {
                continue;
            }

            if (requested.interfaceId() == null) {

                if (existing.interfaceId() == null) {
                    return true;
                }

                continue;
            }

            if (requested.interfaceId()
                    .equals(existing.interfaceId())) {

                return true;
            }
        }

        return false;
    }

    private static PsiDirectory findPackageDirectory(
            @NotNull Project project,
            @NotNull String packageName) {

        return new ModulithModuleResolver(project)
                .findDirectoryForPackage(packageName);
    }

    @Override
    public @NotNull IntentionPreviewInfo generatePreview(
            @NotNull Project project,
            @NotNull ProblemDescriptor previewDescriptor) {

        return IntentionPreviewInfo.EMPTY;
    }
}