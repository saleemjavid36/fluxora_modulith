# Module Dependency Graph

The Module Graph provides a visual representation of recognized module relationships.

Example:

```text
account ─────→ billing
billing ─────→ payment
order ───────→ payment
```

Each node represents a recognized module and arrows represent detected dependency direction:

```text
source module → target module
```

Selecting a module can display related information and provide navigation into the corresponding project package.

The graph is derived from the current project and does not depend on fixed Fluxora application packages or business entities.
