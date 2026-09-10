package com.springmodulith.plugin.codeInsight;

import com.intellij.codeInsight.daemon.ImplicitUsageProvider;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiModifierListOwner;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

public final class ModulithImplicitUsageProvider implements ImplicitUsageProvider {
    private static final Set<String> ENTRY_POINTS = Set.of(
            "org.springframework.stereotype.Component",
            "org.springframework.stereotype.Service",
            "org.springframework.stereotype.Repository",
            "org.springframework.stereotype.Controller",
            "org.springframework.web.bind.annotation.RestController",
            "org.springframework.scheduling.annotation.Scheduled",
            "org.springframework.context.event.EventListener",
            "org.springframework.modulith.ApplicationModuleListener"
    );

    @Override public boolean isImplicitUsage(@NotNull PsiElement element) {
        return element instanceof PsiClass psiClass && hasEntryPointAnnotation(psiClass);
    }
    @Override public boolean isImplicitRead(@NotNull PsiElement element) { return false; }
    @Override public boolean isImplicitWrite(@NotNull PsiElement element) { return false; }

    private boolean hasEntryPointAnnotation(PsiModifierListOwner owner) {
        for (String fqn : ENTRY_POINTS) if (owner.hasAnnotation(fqn)) return true;
        return false;
    }
}
