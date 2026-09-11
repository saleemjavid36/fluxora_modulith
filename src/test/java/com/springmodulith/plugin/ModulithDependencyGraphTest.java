package com.springmodulith.plugin;

import com.springmodulith.plugin.model.ModulithDependencyGraph;
import com.springmodulith.plugin.model.ModulithModule;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class ModulithDependencyGraphTest {

    @Test
    void dependencyIsDetected() {

        ModulithModule account =
                module(
                        "account",
                        "com.example.account"
                );

        ModulithModule user =
                module(
                        "user",
                        "com.example.user"
                );

        ModulithDependencyGraph graph =
                new ModulithDependencyGraph(
                        List.of(account, user),
                        Set.of(
                                new ModulithDependencyGraph.ModuleDependency(
                                        "com.example.account",
                                        "com.example.user"
                                )
                        )
                );

        assertTrue(
                graph.dependsOn(account, user)
        );
    }

    @Test
    void unrelatedModulesHaveNoDependency() {

        ModulithModule account =
                module(
                        "account",
                        "com.example.account"
                );

        ModulithModule user =
                module(
                        "user",
                        "com.example.user"
                );

        ModulithDependencyGraph graph =
                new ModulithDependencyGraph(
                        List.of(account, user),
                        Set.of()
                );

        assertFalse(
                graph.dependsOn(account, user)
        );
    }

    private ModulithModule module(
            String name,
            String packageName) {

        return new ModulithModule(
                name,
                packageName,
                false,
                false,
                Set.of(),
                List.of()
        );
    }
}