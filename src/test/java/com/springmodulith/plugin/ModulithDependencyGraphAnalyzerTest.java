package com.springmodulith.plugin;

import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase5;
import com.springmodulith.plugin.analyzer.ModulithDependencyGraphAnalyzer;
import com.springmodulith.plugin.configuration.ModulithSettings;
import com.springmodulith.plugin.model.ModulithDependencyGraph;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ModulithDependencyGraphAnalyzerTest extends LightJavaCodeInsightFixtureTestCase5 {

    @BeforeEach
    void configure() {
        ModulithSettings settings = ModulithSettings.getInstance(getProject());
        settings.setRootPackage("com.example");
        settings.setDetectionStrategy(ModulithSettings.DIRECT_SUB_PACKAGES);
    }

    @Test
    void graphUsesUnifiedAnalysisAndKeepsSourceReferences() {
        add("com/example/account/package-info.java",
                "@org.springframework.modulith.ApplicationModule(allowedDependencies = {\"user :: api\"})\n" +
                        "package com.example.account;");
        add("com/example/user/package-info.java",
                "package com.example.user;");
        add("com/example/user/api/package-info.java",
                "@org.springframework.modulith.NamedInterface(\"api\")\n" +
                        "package com.example.user.api;");
        add("com/example/user/api/UserApi.java",
                "package com.example.user.api; public class UserApi {}");
        add("com/example/user/internal/UserRepository.java",
                "package com.example.user.internal; public class UserRepository {}");

        add("com/example/account/AccountService.java",
                "package com.example.account;\n" +
                        "import com.example.user.api.UserApi;\n" +
                        "class AccountService { UserApi api; }");
        add("com/example/account/AccountRepository.java",
                "package com.example.account;\n" +
                        "import com.example.user.internal.UserRepository;\n" +
                        "class AccountRepository { UserRepository repository; }");

        ModulithDependencyGraph graph =
                new ModulithDependencyGraphAnalyzer(getProject()).analyze();

        ModulithDependencyGraph.ModuleDependency edge = graph.getDependencies().stream()
                .filter(dependency -> dependency.sourcePackage().equals("com.example.account"))
                .filter(dependency -> dependency.targetPackage().equals("com.example.user"))
                .findFirst()
                .orElseThrow();

        assertTrue(edge.isForbidden());
        assertEquals(2, edge.referenceCount());
        assertEquals(2, edge.references().size());
    }

    @Test
    void jdkAndUnresolvedReferencesDoNotCreateEdges() {
        add("com/example/account/AccountService.java",
                "package com.example.account;\n" +
                        "import java.util.List;\n" +
                        "class AccountService { List<String> values; MissingType missing; }");

        add("com/example/user/UserService.java",
                "package com.example.user; public class UserService {}");

        ModulithDependencyGraph graph =
                new ModulithDependencyGraphAnalyzer(getProject()).analyze();

        assertTrue(graph.getDependencies().isEmpty());
    }

    private void add(String path, String text) {
        myFixture.addFileToProject(path, text);
    }
}
