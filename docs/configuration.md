# Configuration

Fluxora Modulith is designed to work without application-specific hardcoded module names.

The plugin analyzes the current project's:

- Java packages;
- PSI types and references;
- supported Spring Modulith annotations;
- module dependency declarations.

Project-specific configuration can be used when a project's conventions cannot be reliably derived from standard Spring Modulith structure.

Avoid assumptions such as:

```text
com.fluxora
account
user
common
```

An unrelated Java/Spring project should be able to use the plugin without changing plugin source code.
