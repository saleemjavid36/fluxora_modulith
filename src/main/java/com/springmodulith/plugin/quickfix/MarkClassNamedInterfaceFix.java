package com.springmodulith.plugin.quickfix;

import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo;
import com.intellij.codeInspection.IntentionAndQuickFixAction;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaCodeReferenceElement;
import com.intellij.psi.PsiModifierList;
import com.intellij.psi.PsiClass;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class MarkClassNamedInterfaceFix
        extends IntentionAndQuickFixAction {

    private static final String NAMED_INTERFACE =
            "org.springframework.modulith.NamedInterface";

    private final String name;

    public MarkClassNamedInterfaceFix(
            @NotNull String name) {

        this.name = name;
    }

    @Override
    public @NotNull String getFamilyName() {
        return "Spring Modulith";
    }

    @Override
    public @NotNull String getName() {
        return "Mark class as named interface '" + name + "'";
    }

    /**
     * We perform the write action explicitly inside applyFix().
     *
     * Returning false prevents IntelliJ from wrapping the
     * intention in another automatic write action.
     */
    @Override
    public boolean startInWriteAction() {
        return false;
    }

    @Override
    public void applyFix(
            @NotNull Project project,
            @NotNull PsiFile file,
            @Nullable Editor editor) {

        if (editor == null) {
            return;
        }

        int offset =
                editor.getCaretModel().getOffset();

        if (offset < 0
                || offset > file.getTextLength()) {
            return;
        }

        PsiElement element =
                file.findElementAt(offset);

        if (element == null) {
            return;
        }

        /*
         * The caret is normally positioned on the
         * cross-module type reference.
         *
         * Example:
         *
         *     StudentDto studentDto;
         *     ^^^^^^^^^^
         */
        PsiJavaCodeReferenceElement reference =
                PsiTreeUtil.getParentOfType(
                        element,
                        PsiJavaCodeReferenceElement.class
                );

        if (reference == null) {
            return;
        }

        PsiElement resolved =
                reference.resolve();

        if (!(resolved instanceof PsiClass)) {
            return;
        }

        PsiClass psiClass =
                (PsiClass) resolved;

        PsiModifierList modifierList =
                psiClass.getModifierList();

        if (modifierList == null) {
            return;
        }

        /*
         * Don't add @NamedInterface twice.
         */
        if (modifierList.findAnnotation(
                NAMED_INTERFACE
        ) != null) {
            return;
        }

        WriteCommandAction.runWriteCommandAction(
                project,
                () -> {

                    /*
                     * Re-check inside the write action.
                     *
                     * PSI may have changed between the
                     * initial inspection and execution.
                     */
                    PsiModifierList currentModifierList =
                            psiClass.getModifierList();

                    if (currentModifierList == null) {
                        return;
                    }

                    if (currentModifierList.findAnnotation(
                            NAMED_INTERFACE
                    ) != null) {
                        return;
                    }

                    PsiElementFactory factory =
                            JavaPsiFacade.getElementFactory(
                                    project
                            );

                    PsiAnnotation annotation =
                            factory.createAnnotationFromText(
                                    "@org.springframework.modulith.NamedInterface",
                                    psiClass
                            );

                    PsiElement firstChild =
                            currentModifierList.getFirstChild();

                    if (firstChild != null) {

                        currentModifierList.addBefore(
                                annotation,
                                firstChild
                        );

                    } else {

                        currentModifierList.add(
                                annotation
                        );
                    }
                }
        );
    }

    /**
     * IntelliJ generates intention previews in a read action.
     *
     * The actual fix performs a write command, so allowing the
     * default LocalQuickFix preview implementation to call
     * applyFix() would cause:
     *
     *     read action -> write action
     *
     * and IntelliJ reports:
     *
     *     Must not start write action from within read action
     *
     * There is no need for a custom preview here, so explicitly
     * disable preview generation.
     */
    @Override
    public @NotNull IntentionPreviewInfo generatePreview(
            @NotNull Project project,
            @NotNull com.intellij.codeInspection.ProblemDescriptor previewDescriptor) {

        return IntentionPreviewInfo.EMPTY;
    }
}