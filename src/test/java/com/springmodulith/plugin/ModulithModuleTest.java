package com.springmodulith.plugin;

import com.springmodulith.plugin.model.ModulithModule;
import com.springmodulith.plugin.model.NamedInterface;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class ModulithModuleTest {
    @Test void sameModuleIsAlwaysAllowed() {
        ModulithModule account = module("account", "com.example.account", false, false, Set.of());
        assertTrue(account.allowsDependency(account));
    }

    @Test void explicitModuleDependencyIsAllowed() {
        ModulithModule account = module("account", "com.example.account", false, true, Set.of("user"));
        ModulithModule user = module("user", "com.example.user", false, false, Set.of());
        assertTrue(account.allowsDependency(user));
    }

    @Test void qualifiedNamedInterfaceDependencyIsAllowed() {
        ModulithModule account = module("account", "com.example.account", false, true, Set.of("user :: api"));
        ModulithModule user = new ModulithModule("user", "com.example.user", false, false, Set.of(),
                List.of(new NamedInterface("api", "com.example.user.api", Set.of("com.example.user.api.UserApi"))));
        assertTrue(account.allowsDependency(user));
    }

    @Test void explicitlyEmptyDependenciesRejectExternalModule() {
        ModulithModule account = module("account", "com.example.account", false, true, Set.of());
        ModulithModule user = module("user", "com.example.user", false, false, Set.of());
        assertFalse(account.allowsDependency(user));
    }

    private ModulithModule module(String name, String pkg, boolean open, boolean configured, Set<String> deps) {
        return new ModulithModule(name, pkg, open, configured, deps, List.of());
    }
}
