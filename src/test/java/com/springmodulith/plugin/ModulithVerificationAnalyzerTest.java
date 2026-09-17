package com.springmodulith.plugin;

import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase5;
import com.springmodulith.plugin.analyzer.ModulithVerificationAnalyzer;
import com.springmodulith.plugin.configuration.ModulithSettings;
import com.springmodulith.plugin.model.ModulithVerificationResult;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;

class ModulithVerificationAnalyzerTest
        extends LightJavaCodeInsightFixtureTestCase5 {

    @Test
    void verificationReportsForbiddenDependency() {
        add(
                "com/example/account/package-info.java",
                "@org.springframework.modulith.ApplicationModule(" +
                        "allowedDependencies = {})\n" +
                        "package com.example.account;"
        );

        add(
                "com/example/user/package-info.java",
                "package com.example.user;"
        );

        add(
                "com/example/user/UserService.java",
                "package com.example.user; " +
                        "public class UserService {}"
        );

        add(
                "com/example/account/AccountService.java",
                "package com.example.account; " +
                        "import com.example.user.UserService; " +
                        "class AccountService { " +
                        "UserService value; " +
                        "}"
        );

        ModulithSettings settings =
                ModulithSettings.getInstance(myFixture.getProject());

        settings.setRootPackage("com.example");
        settings.setDetectionStrategy(
                ModulithSettings.DIRECT_SUB_PACKAGES
        );

        ModulithVerificationResult result =
                new ModulithVerificationAnalyzer(
                        myFixture.getProject()
                ).verify();

        assertFalse(
                result.dependencyViolations().isEmpty()
        );
    }

    private void add(String path, String text) {
        fixture.addFileToProject(path, text);
    }
}