package com.springmodulith.plugin;

import com.springmodulith.plugin.model.ModulithDependencyGraph;
import com.springmodulith.plugin.model.ModulithModule;
import com.springmodulith.plugin.model.NamedInterface;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class ModulithDependencyGraphTest {

    @Test
    void dependencyIsDetected() {
        ModulithModule account = module("account", "com.example.account", false);
        ModulithModule user = module("user", "com.example.user", false);

        ModulithDependencyGraph graph = new ModulithDependencyGraph(
                List.of(account, user),
                Set.of(new ModulithDependencyGraph.ModuleDependency(
                        "com.example.account", "com.example.user"))
        );

        assertTrue(graph.dependsOn(account, user));
    }

    @Test
    void forbiddenEdgeIsExposed() {
        ModulithModule account = module("account", "com.example.account", false);
        ModulithModule user = module("user", "com.example.user", false);
        ModulithDependencyGraph.ModuleDependency edge =
                new ModulithDependencyGraph.ModuleDependency(
                        account.getPackageName(), user.getPackageName(),
                        ModulithDependencyGraph.EdgeKind.FORBIDDEN, false, null);

        ModulithDependencyGraph graph = new ModulithDependencyGraph(
                List.of(account, user), Set.of(edge));

        ModulithDependencyGraph.ModuleDependency actual = graph.getDependencies().iterator().next();
        assertTrue(actual.isForbidden());
        assertFalse(actual.isAllowed());
    }

    @Test
    void namedInterfaceEdgeIsExposed() {
        ModulithModule account = module("account", "com.example.account", false);
        ModulithModule user = new ModulithModule(
                "user", "com.example.user", false, true, Set.of(),
                List.of(new NamedInterface("api", "com.example.user.api")));
        ModulithDependencyGraph.ModuleDependency edge =
                new ModulithDependencyGraph.ModuleDependency(
                        account.getPackageName(), user.getPackageName(),
                        ModulithDependencyGraph.EdgeKind.NAMED_INTERFACE, false, "api");

        ModulithDependencyGraph graph = new ModulithDependencyGraph(
                List.of(account, user), Set.of(edge));

        ModulithDependencyGraph.ModuleDependency actual = graph.getDependencies().iterator().next();
        assertTrue(actual.isNamedInterface());
        assertEquals("api", actual.namedInterface());
    }

    @Test
    void cycleEdgesAreDetected() {
        ModulithModule account = module("account", "com.example.account", false);
        ModulithModule user = module("user", "com.example.user", false);
        ModulithModule order = module("order", "com.example.order", false);

        ModulithDependencyGraph.ModuleDependency accountToUser =
                new ModulithDependencyGraph.ModuleDependency("com.example.account", "com.example.user");
        ModulithDependencyGraph.ModuleDependency userToOrder =
                new ModulithDependencyGraph.ModuleDependency("com.example.user", "com.example.order");
        ModulithDependencyGraph.ModuleDependency orderToAccount =
                new ModulithDependencyGraph.ModuleDependency("com.example.order", "com.example.account");

        ModulithDependencyGraph graph = new ModulithDependencyGraph(
                List.of(account, user, order),
                Set.of(accountToUser, userToOrder, orderToAccount)
        );

        assertTrue(graph.isCyclicEdge(accountToUser));
        assertTrue(graph.isCyclicEdge(userToOrder));
        assertTrue(graph.isCyclicEdge(orderToAccount));
        assertEquals(1, graph.getCycles().size());
    }

    @Test
    void openModuleDoesNotParticipateInCycles() {
        ModulithModule account = module("account", "com.example.account", false);
        ModulithModule user = module("user", "com.example.user", true);

        ModulithDependencyGraph.ModuleDependency accountToUser =
                new ModulithDependencyGraph.ModuleDependency("com.example.account", "com.example.user");
        ModulithDependencyGraph.ModuleDependency userToAccount =
                new ModulithDependencyGraph.ModuleDependency("com.example.user", "com.example.account");

        ModulithDependencyGraph graph = new ModulithDependencyGraph(
                List.of(account, user), Set.of(accountToUser, userToAccount)
        );

        assertFalse(graph.isCyclicEdge(accountToUser));
        assertFalse(graph.isCyclicEdge(userToAccount));
        assertTrue(graph.getCycles().isEmpty());
    }

    @Test
    void unrelatedModulesHaveNoDependency() {
        ModulithModule account = module("account", "com.example.account", false);
        ModulithModule user = module("user", "com.example.user", false);

        ModulithDependencyGraph graph = new ModulithDependencyGraph(
                List.of(account, user), Set.of());

        assertFalse(graph.dependsOn(account, user));
    }


    @Test
    void twoIndependentCyclesAreDetected() {
        ModulithModule a = module("a", "com.example.a", false);
        ModulithModule b = module("b", "com.example.b", false);
        ModulithModule c = module("c", "com.example.c", false);
        ModulithModule d = module("d", "com.example.d", false);

        ModulithDependencyGraph.ModuleDependency aToB =
                new ModulithDependencyGraph.ModuleDependency("com.example.a", "com.example.b");
        ModulithDependencyGraph.ModuleDependency bToA =
                new ModulithDependencyGraph.ModuleDependency("com.example.b", "com.example.a");
        ModulithDependencyGraph.ModuleDependency cToD =
                new ModulithDependencyGraph.ModuleDependency("com.example.c", "com.example.d");
        ModulithDependencyGraph.ModuleDependency dToC =
                new ModulithDependencyGraph.ModuleDependency("com.example.d", "com.example.c");

        ModulithDependencyGraph graph = new ModulithDependencyGraph(
                List.of(a, b, c, d),
                Set.of(aToB, bToA, cToD, dToC)
        );

        assertEquals(2, graph.getCycles().size());
        assertTrue(graph.isCyclicEdge(aToB));
        assertTrue(graph.isCyclicEdge(bToA));
        assertTrue(graph.isCyclicEdge(cToD));
        assertTrue(graph.isCyclicEdge(dToC));
    }

    @Test
    void oneWayEdgeIsNotMarkedAsCycle() {
        ModulithModule a = module("a", "com.example.a", false);
        ModulithModule b = module("b", "com.example.b", false);

        ModulithDependencyGraph.ModuleDependency aToB =
                new ModulithDependencyGraph.ModuleDependency("com.example.a", "com.example.b");

        ModulithDependencyGraph graph = new ModulithDependencyGraph(
                List.of(a, b), Set.of(aToB)
        );

        assertFalse(graph.isCyclicEdge(aToB));
        assertTrue(graph.getCycles().isEmpty());
    }

    private ModulithModule module(String name, String packageName, boolean open) {
        return new ModulithModule(name, packageName, open, false, Set.of(), List.of());
    }
}
