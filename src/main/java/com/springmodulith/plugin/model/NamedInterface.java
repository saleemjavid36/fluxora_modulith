package com.springmodulith.plugin.model;

import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

public final class NamedInterface {
    private final String name;
    private final String packageName;
    private final Set<String> typeNames;

    public NamedInterface(@NotNull String name, @NotNull String packageName) {
        this(name, packageName, Collections.emptySet());
    }

    public NamedInterface(@NotNull String name, @NotNull String packageName, @NotNull Set<String> typeNames) {
        this.name = name;
        this.packageName = packageName;
        this.typeNames = Collections.unmodifiableSet(new LinkedHashSet<>(typeNames));
    }

    @NotNull public String getName() { return name; }
    @NotNull public String getPackageName() { return packageName; }
    @NotNull public Set<String> getTypeNames() { return typeNames; }

    public boolean containsPackage(@NotNull String candidate) {
        return candidate.equals(packageName) || candidate.startsWith(packageName + ".");
    }

    public boolean containsType(@NotNull String qualifiedName) {
        return typeNames.contains(qualifiedName);
    }
}
