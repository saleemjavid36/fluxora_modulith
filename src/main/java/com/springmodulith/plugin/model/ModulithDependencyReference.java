package com.springmodulith.plugin.model;

import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

public final class ModulithDependencyReference {
    private final VirtualFile file;
    private final int offset;
    private final int lineNumber;
    private final String presentation;

    public ModulithDependencyReference(
            @NotNull VirtualFile file,
            int offset,
            int lineNumber,
            @NotNull String presentation) {
        this.file = file;
        this.offset = Math.max(0, offset);
        this.lineNumber = Math.max(1, lineNumber);
        this.presentation = presentation;
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

    @Override
    public String toString() {
        return presentation;
    }
}
