package com.springmodulith.plugin;

import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase5;
import com.springmodulith.plugin.analyzer.ModulithDependencyAnalyzer;
import com.springmodulith.plugin.configuration.ModulithSettings;
import com.springmodulith.plugin.model.ModulithDependencyAnalysis;
import com.springmodulith.plugin.resolver.ModulithModuleResolver;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModulithAdvancedFeaturesTest extends LightJavaCodeInsightFixtureTestCase5 {

    @Test
    void additionalModulePackageCanBeConfigured() {
        add(
                "com/example/custom/CustomService.java",
                "package com.example.custom; public class CustomService {}"
        );

        ModulithSettings settings = ModulithSettings.getInstance(getFixture().getJavaFacade().getProject());

        settings.setRootPackage("com.example.missing");
        settings.setAdditionalModulePackages("com.example.custom");
        settings.setDetectionStrategy(ModulithSettings.EXPLICITLY_ANNOTATED);

        assertTrue(
                new ModulithModuleResolver(getFixture().getJavaFacade().getProject())
                        .resolveModules()
                        .stream()
                        .anyMatch(module ->
                                module.getPackageName()
                                        .equals("com.example.custom"))
        );
    }

    @Test
    void dependencyOverrideCanAllowNamedInterface() {
        add(
                "com/example/account/package-info.java",
                "@org.springframework.modulith.ApplicationModule(allowedDependencies = {})\n" +
                        "package com.example.account;"
        );

        add(
                "com/example/user/package-info.java",
                "package com.example.user;"
        );

        add(
                "com/example/user/api/package-info.java",
                "@org.springframework.modulith.NamedInterface(\"api\")\n" +
                        "package com.example.user.api;"
        );

        add(
                "com/example/user/api/UserApi.java",
                "package com.example.user.api; public class UserApi {}"
        );

        add(
                "com/example/account/AccountService.java",
                "package com.example.account;\n" +
                        "import com.example.user.api.UserApi;\n" +
                        "class AccountService { UserApi value; }"
        );

        ModulithSettings settings =
                ModulithSettings.getInstance(getFixture().getJavaFacade().getProject());

        settings.setRootPackage("com.example");
        settings.setDetectionStrategy(ModulithSettings.DIRECT_SUB_PACKAGES);
        settings.setDependencyOverrides(
                "com.example.account=user :: api"
        );

        List<ModulithDependencyAnalysis> analyses =
                new ModulithDependencyAnalyzer(
                        new ModulithModuleResolver(getFixture().getJavaFacade().getProject()),
                        getFixture().getJavaFacade().getProject()
                ).analyzeProject();

        assertFalse(
                analyses.stream()
                        .anyMatch(ModulithDependencyAnalysis::isForbidden)
        );
    }

    private void add(String path, String text) {
        getFixture().addFileToProject(path, text);
    }
}