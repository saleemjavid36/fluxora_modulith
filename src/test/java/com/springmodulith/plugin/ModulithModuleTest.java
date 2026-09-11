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


    @Test void namedInterfaceAllowsOnlyDeclaredInterfaceType() {
        ModulithModule account = module("account", "com.example.account", false, true, Set.of("user :: api"));
        ModulithModule user = new ModulithModule("user", "com.example.user", false, false, Set.of(),
                List.of(new NamedInterface("api", "com.example.user.api", Set.of("com.example.user.api.UserApi"))));

        assertTrue(account.allowsType("com.example.user.api.UserApi", "com.example.user.api", user));
        assertFalse(account.allowsType("com.example.user.UserService", "com.example.user", user));
    }

    @Test void namedInterfacePackageIncludesNestedPackages() {
        NamedInterface api = new NamedInterface("api", "com.example.user.api", Set.of());

        assertTrue(api.containsPackage("com.example.user.api"));
        assertTrue(api.containsPackage("com.example.user.api.v1"));
        assertFalse(api.containsPackage("com.example.user.internal"));
    }

    @Test
    void unconfiguredDependenciesAreDetectedByInspectionPolicy() {
        ModulithModule account = module("account", "com.example.account", false, false, Set.of());
        ModulithModule user = module("user", "com.example.user", false, false, Set.of());

        assertTrue(account.getAllowedDependencies().isEmpty());
        assertFalse(account.isAllowedDependenciesConfigured());
        assertTrue(account.allowsDependency(user));
    }
    @Test
    void allowedNamedInterfaceAllowsType() {
        ModulithModule account =
                module(
                        "account",
                        "com.example.account",
                        false,
                        true,
                        Set.of("user :: api")
                );

        ModulithModule user =
                new ModulithModule(
                        "user",
                        "com.example.user",
                        false,
                        false,
                        Set.of(),
                        List.of(
                                new NamedInterface(
                                        "api",
                                        "com.example.user.api",
                                        Set.of(
                                                "com.example.user.api.UserApi"
                                        )
                                )
                        )
                );

        assertNotNull(
                account.findAllowedNamedInterface(
                        "com.example.user.api.UserApi",
                        "com.example.user.api",
                        user
                )
        );

        assertTrue(
                account.allowsType(
                        "com.example.user.api.UserApi",
                        "com.example.user.api",
                        user
                )
        );
    }

    @Test
    void namedInterfaceDoesNotAllowInternalType() {
        ModulithModule account =
                module(
                        "account",
                        "com.example.account",
                        false,
                        true,
                        Set.of("user :: api")
                );

        ModulithModule user =
                new ModulithModule(
                        "user",
                        "com.example.user",
                        false,
                        false,
                        Set.of(),
                        List.of(
                                new NamedInterface(
                                        "api",
                                        "com.example.user.api",
                                        Set.of(
                                                "com.example.user.api.UserApi"
                                        )
                                )
                        )
                );

        assertNull(
                account.findAllowedNamedInterface(
                        "com.example.user.internal.UserRepository",
                        "com.example.user.internal",
                        user
                )
        );

        assertFalse(
                account.allowsType(
                        "com.example.user.internal.UserRepository",
                        "com.example.user.internal",
                        user
                )
        );
    }

    @Test
    void namedInterfaceRuleMatchesModulePackageName() {
        ModulithModule account =
                module(
                        "account",
                        "com.example.account",
                        false,
                        true,
                        Set.of("com.example.user :: api")
                );

        ModulithModule user =
                new ModulithModule(
                        "user",
                        "com.example.user",
                        false,
                        false,
                        Set.of(),
                        List.of(
                                new NamedInterface(
                                        "api",
                                        "com.example.user.api",
                                        Set.of(
                                                "com.example.user.api.UserApi"
                                        )
                                )
                        )
                );

        assertTrue(
                account.allowsType(
                        "com.example.user.api.UserApi",
                        "com.example.user.api",
                        user
                )
        );
    }


    @Test
    void dependencyRuleParsesModuleOnly() {
        ModulithModule.DependencyRule rule =
                ModulithModule.DependencyRule.parse(" user ");

        assertNotNull(rule);
        assertEquals("user", rule.moduleId());
        assertNull(rule.interfaceId());
    }

    @Test
    void dependencyRuleParsesQualifiedNamedInterface() {
        ModulithModule.DependencyRule rule =
                ModulithModule.DependencyRule.parse(" com.example.user :: api ");

        assertNotNull(rule);
        assertEquals("com.example.user", rule.moduleId());
        assertEquals("api", rule.interfaceId());
    }

    @Test
    void dependencyRuleRejectsMalformedValues() {
        assertNull(ModulithModule.DependencyRule.parse(""));
        assertNull(ModulithModule.DependencyRule.parse("user ::"));
        assertNull(ModulithModule.DependencyRule.parse(":: api"));
        assertNull(ModulithModule.DependencyRule.parse("user :: api :: extra"));
    }

    @Test
    void wildcardNamedInterfaceRuleAllowsWholeTargetModule() {
        ModulithModule account = module("account", "com.example.account", false, true, Set.of("user :: *"));
        ModulithModule user = module("user", "com.example.user", false, false, Set.of());

        assertTrue(account.allowsType(
                "com.example.user.internal.UserRepository",
                "com.example.user.internal",
                user
        ));
    }

    @Test
    void namedInterfacePackageRuleAllowsNestedPackage() {
        ModulithModule account = module("account", "com.example.account", false, true, Set.of("user :: api"));
        ModulithModule user = new ModulithModule(
                "user", "com.example.user", false, false, Set.of(),
                List.of(new NamedInterface("api", "com.example.user.api", Set.of()))
        );

        assertTrue(account.allowsType(
                "com.example.user.api.v1.UserApi",
                "com.example.user.api.v1",
                user
        ));
        assertFalse(account.allowsType(
                "com.example.user.apix.UserApi",
                "com.example.user.apix",
                user
        ));
    }

    private ModulithModule module(String name, String pkg, boolean open, boolean configured, Set<String> deps) {
        return new ModulithModule(name, pkg, open, configured, deps, List.of());
    }
}
