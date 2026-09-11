package com.springmodulith.plugin.model;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiJavaCodeReferenceElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Single immutable result used by inspections, the dependency graph and navigation.
 */
public record ModulithDependencyAnalysis(
        @NotNull ModulithModule source,
        @NotNull ModulithModule target,
        @NotNull PsiClass targetClass,
        @NotNull PsiJavaCodeReferenceElement reference,
        @NotNull ModulithDependencyReference sourceReference,
        @NotNull Status status,
        boolean apiViolation,
        @Nullable NamedInterface namedInterface) {

    public enum Status {
        ALLOWED,
        FORBIDDEN,
        NAMED_INTERFACE
    }

    public boolean isForbidden() {
        return status == Status.FORBIDDEN || apiViolation;
    }

    public boolean isAllowed() {
        return !isForbidden();
    }

    @NotNull
    public String sourcePackage() {
        return source.getPackageName();
    }

    @NotNull
    public String targetPackage() {
        return target.getPackageName();
    }

    @Nullable
    public String targetType() {
        return targetClass.getQualifiedName();
    }

    @Nullable
    public String namedInterfaceName() {
        return namedInterface == null ? null : namedInterface.getName();
    }
}
