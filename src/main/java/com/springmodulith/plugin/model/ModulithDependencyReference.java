package com.springmodulith.plugin.model;

import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

public final class ModulithDependencyReference {
    private final VirtualFile file;
    private final int offset;
    private final int lineNumber;
    private final String presentation;
    private final String referencedType;
    private final String targetPackage;
    private final String rule;
    private final String status;

    public ModulithDependencyReference(
            @NotNull VirtualFile file,
            int offset,
            int lineNumber,
            @NotNull String presentation) {
        this(file, offset, lineNumber, presentation, "", "", "", "");
    }

    public ModulithDependencyReference(
            @NotNull VirtualFile file,
            int offset,
            int lineNumber,
            @NotNull String presentation,
            @NotNull String referencedType,
            @NotNull String targetPackage,
            @NotNull String rule,
            @NotNull String status) {
        this.file = file;
        this.offset = Math.max(0, offset);
        this.lineNumber = Math.max(1, lineNumber);
        this.presentation = presentation;
        this.referencedType = referencedType;
        this.targetPackage = targetPackage;
        this.rule = rule;
        this.status = status;
    }

    @NotNull
    public VirtualFile file() {
        return file;
    }

    public int offset() {
        return offset;
    }

    public int lineNumber() {
        return lineNumber;
    }

    @NotNull
    public String presentation() {
        return presentation;
    }

    @NotNull
    public String referencedType() {
        return referencedType;
    }

    @NotNull
    public String targetPackage() {
        return targetPackage;
    }

    @NotNull
    public String rule() {
        return rule;
    }

    @NotNull
    public String status() {
        return status;
    }

    @Override
    public String toString() {
        return presentation;
    }
}
