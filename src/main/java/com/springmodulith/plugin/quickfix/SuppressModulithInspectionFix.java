package com.springmodulith.plugin.quickfix;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiImportStatement;
import com.intellij.psi.PsiJavaCodeReferenceElement;
import com.intellij.psi.PsiModifierListOwner;
import com.intellij.psi.PsiParserFacade;
import com.intellij.psi.PsiStatement;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Suppresses one or more Spring Modulith inspections at the smallest
 * practical Java source scope.
 *
 * The editor annotation is backed by a separate annotator, so this fix
 * writes the normal IntelliJ //noinspection marker and the annotator
 * explicitly honors the same marker.
 */
public final class SuppressModulithInspectionFix
        implements LocalQuickFix, IntentionAction {

    private final String[] inspectionIds;

    public SuppressModulithInspectionFix(
            @NotNull String... inspectionIds) {

        this.inspectionIds = inspectionIds.clone();
    }

    @Override
    public @NotNull String getName() {
        return "Suppress inspection";
    }

    @Override
    public @NotNull String getFamilyName() {
        return "Spring Modulith";
    }

    /*
     * AnnotationBuilder.withFix(...) accepts an IntentionAction,
     * while ProblemsHolder.registerProblem(...) accepts a LocalQuickFix.
     *
     * Implementing both lets the same suppression action work from
     * both the editor annotation popup and the inspection popup.
     */
    @Override
    public @NotNull String getText() {
        return getName();
    }

    @Override
    public boolean isAvailable(
            @NotNull Project project,
            @NotNull com.intellij.openapi.editor.Editor editor,
            @NotNull PsiFile file) {

        PsiElement element =
                file.findElementAt(
                        editor.getCaretModel().getOffset()
                );

        return element != null
                && element.isValid();
    }

    @Override
    public void invoke(
            @NotNull Project project,
            @NotNull com.intellij.openapi.editor.Editor editor,
            @NotNull PsiFile file) {

        PsiElement element =
                file.findElementAt(
                        editor.getCaretModel().getOffset()
                );

        if (element == null || !element.isValid()) {
            return;
        }

        WriteCommandAction
                .writeCommandAction(project)
                .withName("Suppress Spring Modulith inspection")
                .run(() -> addSuppression(element));
    }

    @Override
    public void applyFix(
            @NotNull Project project,
            @NotNull ProblemDescriptor descriptor) {

        PsiElement problemElement =
                descriptor.getPsiElement();

        if (problemElement == null
                || !problemElement.isValid()) {
            return;
        }

        WriteCommandAction
                .writeCommandAction(project)
                .withName("Suppress Spring Modulith inspection")
                .run(() -> addSuppression(problemElement));
    }

    private void addSuppression(
            @NotNull PsiElement problemElement) {

        PsiElement target =
                findSuppressionTarget(problemElement);

        if (target == null
                || target.getParent() == null
                || target.getContainingFile() == null) {
            return;
        }

        String suppressionComment =
                createSuppressionComment();

        addSuppressionBefore(
                target,
                suppressionComment
        );

        /*
         * A Java type dependency is often reported twice:
         *
         *     import StudentRepository;
         *     private StudentRepository repository;
         *
         * Suppressing only the field leaves the import highlighted.
         * Suppress the matching import as well when the quick fix was
         * invoked on a type reference.
         */
        if (!(target instanceof PsiImportStatement)) {

            PsiImportStatement importStatement =
                    findMatchingImport(
                            problemElement,
                            target.getContainingFile()
                    );

            if (importStatement != null) {
                addSuppressionBefore(
                        importStatement,
                        suppressionComment
                );
            }
        }
    }

    private void addSuppressionBefore(
            @NotNull PsiElement target,
            @NotNull String suppressionComment) {

        if (hasSuppressionComment(
                target,
                suppressionComment
        )) {
            return;
        }

        PsiFile containingFile =
                target.getContainingFile();

        if (containingFile == null
                || target.getParent() == null) {
            return;
        }

        PsiElementFactory factory =
                PsiElementFactory.getInstance(
                        containingFile.getProject()
                );

        PsiElement comment =
                factory.createCommentFromText(
                        suppressionComment,
                        target
                );

        PsiElement newline =
                PsiParserFacade.SERVICE
                        .getInstance(
                                containingFile.getProject()
                        )
                        .createWhiteSpaceFromText("\n");

        PsiElement parent =
                target.getParent();

        parent.addBefore(comment, target);
        parent.addBefore(newline, target);
    }

    @NotNull
    private String createSuppressionComment() {

        String ids =
                Arrays.stream(inspectionIds)
                        .filter(Objects::nonNull)
                        .map(String::trim)
                        .filter(id -> !id.isEmpty())
                        .distinct()
                        .collect(Collectors.joining(","));

        return "//noinspection " + ids;
    }

    @Nullable
    private static PsiImportStatement findMatchingImport(
            @NotNull PsiElement problemElement,
            @NotNull PsiFile file) {

        PsiJavaCodeReferenceElement reference =
                PsiTreeUtil.getParentOfType(
                        problemElement,
                        PsiJavaCodeReferenceElement.class
                );

        if (reference == null) {
            return null;
        }

        PsiElement resolved =
                reference.resolve();

        if (!(resolved instanceof PsiClass targetClass)) {
            return null;
        }

        String targetQualifiedName =
                targetClass.getQualifiedName();

        if (targetQualifiedName == null) {
            return null;
        }

        for (PsiImportStatement importStatement :
                PsiTreeUtil.getChildrenOfTypeAsList(
                        file,
                        PsiImportStatement.class
                )) {

            PsiJavaCodeReferenceElement importReference =
                    importStatement.getImportReference();

            if (importReference == null) {
                continue;
            }

            PsiElement importResolved =
                    importReference.resolve();

            if (importResolved == targetClass) {
                return importStatement;
            }

            if (targetQualifiedName.equals(
                    importReference.getQualifiedName()
            )) {
                return importStatement;
            }
        }

        return null;
    }

    /**
     * Chooses the smallest source element for which IntelliJ's
     * //noinspection comment can suppress the inspection.
     */
    @NotNull
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

        PsiModifierListOwner owner =
                PsiTreeUtil.getParentOfType(
                        element,
                        PsiModifierListOwner.class
                );

        if (owner != null) {
            return owner;
        }

        return element;
    }

    private static boolean hasSuppressionComment(
            @NotNull PsiElement target,
            @NotNull String suppressionComment) {

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
                    && text.contains(suppressionComment);
        }

        return false;
    }

    @Override
    public boolean startInWriteAction() {
        return false;
    }
}
