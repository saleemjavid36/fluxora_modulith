package com.springmodulith.plugin;

import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase5;
import com.springmodulith.plugin.configuration.ModulithSettings;
import com.springmodulith.plugin.inspection.ModulithDependencyInspection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ModulithDependencyInspectionTest
        extends LightJavaCodeInsightFixtureTestCase5 {

    @BeforeEach
    void configure() {
        ModulithSettings settings =
                ModulithSettings.getInstance(fixture.getProject());

        settings.setRootPackage("com.example");
        settings.setDetectionStrategy(
                ModulithSettings.DIRECT_SUB_PACKAGES
        );

        fixture.enableInspections(
                ModulithDependencyInspection.class
        );
    }

    @Test
    void sameModuleReferenceHasNoWarning() {
        add(
                "com/example/account/AccountHelper.java",
                "package com.example.account; " +
                        "public class AccountHelper {}"
        );

        fixture.configureByText(
                "AccountService.java",
                "package com.example.account;\n" +
                        "class AccountService { " +
                        "AccountHelper helper; " +
                        "}"
        );

        fixture.testHighlighting(
                false,
                false,
                false
        );
    }

    @Test
    void forbiddenNamedInterfaceReferenceIsReported() {
        add(
                "com/example/account/package-info.java",
                "@org.springframework.modulith.ApplicationModule(" +
                        "allowedDependencies = {\"user :: api\"})\n" +
                        "package com.example.account;"
        );

        add(
                "com/example/account/AccountService.java",
                "package com.example.account; " +
                        "public class AccountService {}"
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
                "package com.example.user.api; " +
                        "public class UserApi {}"
        );

        add(
                "com/example/user/internal/UserRepository.java",
                "package com.example.user.internal; " +
                        "public class UserRepository {}"
        );

        fixture.configureByText(
                "AccountClient.java",
                "package com.example.account;\n" +
                        "import com.example.user.internal.UserRepository;\n" +
                        "class AccountClient { " +
                        "UserRepository repository; " +
                        "}"
        );

        fixture.testHighlighting(
                false,
                false,
                false,
                "Modulith dependency is not allowed: account -> user"
        );
    }

    @Test
    void allowedNamedInterfaceReferenceIsNotReported() {
        add(
                "com/example/account/package-info.java",
                "@org.springframework.modulith.ApplicationModule(" +
                        "allowedDependencies = {\"user :: api\"})\n" +
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
                "package com.example.user.api; " +
                        "public class UserApi {}"
        );

        fixture.configureByText(
                "AccountClient.java",
                "package com.example.account;\n" +
                        "import com.example.user.api.UserApi;\n" +
                        "class AccountClient { " +
                        "UserApi api; " +
                        "}"
        );

        fixture.testHighlighting(
                false,
                false,
                false
        );
    }

    @Test
    void unresolvedReferenceIsIgnored() {
        add(
                "com/example/account/package-info.java",
                "@org.springframework.modulith.ApplicationModule(" +
                        "allowedDependencies = {})\n" +
                        "package com.example.account;"
        );

        fixture.configureByText(
                "AccountClient.java",
                "package com.example.account;\n" +
                        "class AccountClient { " +
                        "MissingType value; " +
                        "}"
        );

        fixture.testHighlighting(
                false,
                false,
                false
        );
    }

    private void add(String path, String text) {
        fixture.addFileToProject(path, text);
    }
}