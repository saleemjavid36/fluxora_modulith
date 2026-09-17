package com.springmodulith.plugin;

import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase5;
import com.springmodulith.plugin.configuration.ModulithSettings;
import com.springmodulith.plugin.model.ModulithModule;
import com.springmodulith.plugin.resolver.ModulithModuleResolver;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class ModulithModuleResolverTest extends LightJavaCodeInsightFixtureTestCase5 {

//    @Test
//    void directDiscoveryFindsNestedExplicitModulesAndKeepsPackageBoundary() {
//        createProject();
//
//        ModulithSettings settings =
//                ModulithSettings.getInstance(getProject());
//
//        settings.setRootPackage("com.example");
//        settings.setDetectionStrategy(
//                ModulithSettings.DIRECT_SUB_PACKAGES
//        );
//
//        List<ModulithModule> modules =
//                new ModulithModuleResolver(getProject()).resolveModules();
//
//        Set<String> packages = modules.stream()
//                .map(ModulithModule::getPackageName)
//                .collect(Collectors.toSet());
//
//        assertTrue(packages.contains("com.example.account"));
//        assertTrue(packages.contains("com.example.user"));
//        assertTrue(packages.contains("com.example.user.admin"));
//        assertFalse(packages.contains("com.example.user.internal"));
//    }
//
//    @Test
//    void explicitStrategyFindsAnnotatedModulesEvenWhenRootInferenceIsUnavailable() {
//        getFixture().addFileToProject(
//                "com/example/account/package-info.java",
//                "@org.springframework.modulith.ApplicationModule\n" +
//                        "package com.example.account;"
//        );
//
//        getFixture().addFileToProject(
//                "com/example/user/package-info.java",
//                "@org.springframework.modulith.ApplicationModule\n" +
//                        "package com.example.user;"
//        );
//
//        getFixture().addFileToProject(
//                "com/example/user/internal/UserRepository.java",
//                "package com.example.user.internal; public class UserRepository {}"
//        );
//
//        ModulithSettings settings =
//                ModulithSettings.getInstance(getProject());
//
//        settings.setRootPackage("com.example.missing");
//        settings.setDetectionStrategy(
//                ModulithSettings.EXPLICITLY_ANNOTATED
//        );
//
//        List<ModulithModule> modules =
//                new ModulithModuleResolver(getProject()).resolveModules();
//
//        assertEquals(
//                Set.of(
//                        "com.example.account",
//                        "com.example.user"
//                ),
//                modules.stream()
//                        .map(ModulithModule::getPackageName)
//                        .collect(Collectors.toSet())
//        );
//    }
//
//    @Test
//    void configuredRootCanItselfBeAnExplicitModule() {
//        getFixture().addFileToProject(
//                "com/example/account/package-info.java",
//                "@org.springframework.modulith.ApplicationModule\n" +
//                        "package com.example.account;"
//        );
//
//        getFixture().addFileToProject(
//                "com/example/account/AccountService.java",
//                "package com.example.account; public class AccountService {}"
//        );
//
//        ModulithSettings settings =
//                ModulithSettings.getInstance(getProject());
//
//        settings.setRootPackage("com.example.account");
//        settings.setDetectionStrategy(
//                ModulithSettings.DIRECT_SUB_PACKAGES
//        );
//
//        List<ModulithModule> modules =
//                new ModulithModuleResolver(getProject()).resolveModules();
//
//        assertTrue(
//                modules.stream()
//                        .anyMatch(module ->
//                                module.getPackageName()
//                                        .equals("com.example.account"))
//        );
//    }
//
//    private void createProject() {
//        getFixture().addFileToProject(
//                "com/example/account/AccountService.java",
//                "package com.example.account; public class AccountService {}"
//        );
//
//        getFixture().addFileToProject(
//                "com/example/user/UserService.java",
//                "package com.example.user; public class UserService {}"
//        );
//
//        getFixture().addFileToProject(
//                "com/example/user/admin/package-info.java",
//                "@org.springframework.modulith.ApplicationModule\n" +
//                        "package com.example.user.admin;"
//        );
//
//        getFixture().addFileToProject(
//                "com/example/user/admin/AdminService.java",
//                "package com.example.user.admin; public class AdminService {}"
//        );
//
//        getFixture().addFileToProject(
//                "com/example/user/internal/UserRepository.java",
//                "package com.example.user.internal; public class UserRepository {}"
//        );
//    }
}