# Fluxora Modulith

**Spring Modulith architecture analysis and module boundary enforcement for IntelliJ IDEA.**

Fluxora Modulith helps Java and Spring developers understand, visualize, and enforce modular application boundaries directly inside IntelliJ IDEA.

It is designed for projects following **Spring Modulith** and **Spring Modulith-style modular architecture** and works from the current project's Java structure and IntelliJ PSI rather than relying on application-specific package names.

## Features

### Spring Modulith Architecture

- Spring Modulith module detection
- `@ApplicationModule` support
- `allowedDependencies` analysis
- Qualified module dependency declarations
- Named interfaces
- Module boundary analysis
- Cross-module dependency detection
- Dependency cycle detection

### IntelliJ IDEA Integration

- Editor inspections for module boundary violations
- IntelliJ Alt+Enter quick fixes
- Module navigation
- Package and module exploration
- Suppression support
- Safe handling of unresolved references
- JDK and third-party dependency filtering

### Module Visualization

- Module Graph
- Module Structure
- Module dependency relationships
- Module detail navigation
- Dependency direction visualization
- Architecture verification

### Architecture Verification

- Verify recognized modules
- Verify module dependencies
- Verify allowed dependencies
- Review cross-module dependencies
- Review architecture issues
- Export verification information

## Why Fluxora Modulith?

Large Spring applications can become difficult to understand when package boundaries and module dependencies are not clearly enforced.

Fluxora Modulith provides architecture information directly inside IntelliJ IDEA so developers can see module relationships while working with their Java code.

Instead of manually maintaining a separate architecture diagram, the plugin analyzes the current project structure and Java PSI.

```text
Java/Spring Project
        │
        ▼
Module Detection
        │
        ▼
Module Structure
        │
        ▼
Dependency Analysis
        │
        ▼
Architecture Rules
        │
        ├── Inspections
        ├── Quick Fixes
        ├── Module Graph
        └── Verification
```

## Spring Modulith Support

Fluxora Modulith is designed around common Spring Modulith concepts including:

- Application modules
- `@ApplicationModule`
- `allowedDependencies`
- Named interfaces
- Module boundaries
- Module dependencies

Example:

```java
@ApplicationModule(
    allowedDependencies = {
        "payment"
    }
)
package com.example.shop.billing;
```

The actual module and package names are determined by the current project.

## Cross-Module Dependencies

Fluxora Modulith distinguishes between references inside the same module and references that cross a recognized module boundary.

For example:

```text
billing
   │
   └──────► payment
```

A reference from `billing` to `payment` can be analyzed against the project's dependency rules.

References that remain inside the same logical module are not treated as cross-module dependencies.

## Allowed Dependencies

When a module explicitly declares an allowed dependency:

```java
@ApplicationModule(
    allowedDependencies = {
        "payment"
    }
)
package com.example.shop.billing;
```

Fluxora Modulith can use that configuration when evaluating supported cross-module references.

If a dependency is not permitted, the plugin can report the dependency in the editor and provide supported IntelliJ quick fixes.

## Named Interfaces

Named interfaces allow a module to expose an intentional API while keeping implementation details internal.

Conceptually:

```text
student
├── repository
├── service
├── dto
└── internal
```

A consuming module can depend on the intended module API rather than directly depending on arbitrary internal implementation areas.

## Module Graph

The Module Graph provides a visual representation of recognized module relationships.

Example:

```text
             ┌──────────┐
             │ account  │
             └────┬─────┘
                  │
                  ▼
             ┌──────────┐
             │ billing  │
             └────┬─────┘
                  │
                  ▼
             ┌──────────┐
             │ payment  │
             └──────────┘
```

The graph is generated from the current project rather than from hardcoded application-specific modules.

## Module Structure

The Module Structure view allows developers to explore recognized modules and their package structure.

It helps answer questions such as:

- Which packages belong to a module?
- Which modules exist in the application?
- What dependencies does a module have?
- Where is a module located?
- Which module should I navigate to?

## Inspections

Fluxora Modulith integrates with IntelliJ IDEA inspections to identify supported architecture violations directly in Java source code.

For example:

```text
billing → payment
```

If the dependency violates the applicable module rules, the reference can be highlighted in the editor.

The plugin avoids reporting unsupported references such as:

- JDK classes
- Third-party library classes
- Unresolved symbols
- Same-module references
- References that do not cross a recognized module boundary

## Quick Fixes

When an architecture issue is detected, IntelliJ's **Alt+Enter** menu can provide supported actions.

Examples include:

```text
Add 'payment' as an allowed dependency of the 'billing' module
```

and:

```text
Add dependency 'payment' to @ApplicationModule
```

Other supported actions can include:

- Navigate to the target module
- Suppress inspection
- Apply the appropriate dependency configuration

Quick fixes are designed to preserve existing configuration and avoid duplicate dependency declarations.

## Architecture Verification

The Architecture Verification view provides a centralized way to review the recognized modular architecture.

Depending on the project and configured rules, verification can include:

- Recognized modules
- Module dependencies
- Allowed dependency rules
- Named interfaces
- Cross-module dependencies
- Dependency cycles

Verification results can also be exported using the plugin's export functionality.

## Generic Project Support

Fluxora Modulith is designed to work with unrelated Java and Spring projects.

It does not require packages such as:

```text
com.fluxora
account
user
common
```

and does not depend on application-specific business logic.

Module information is derived from the current IntelliJ project and supported Spring Modulith configuration.

This means the plugin can be used with different:

- Package names
- Module names
- Application structures
- Java/Spring projects

## Performance and IntelliJ Integration

Fluxora Modulith uses IntelliJ Platform APIs and Java PSI for project analysis.

The plugin is designed to keep:

- UI responsibilities
- Project analysis
- Module discovery
- Dependency analysis
- Configuration
- Inspections

separated so that architecture analysis remains maintainable and compatible with the IntelliJ Platform.

## Getting Started

### 1. Install

Install **Fluxora Modulith** from JetBrains Marketplace.

### 2. Open a Java/Spring Project

Open the project you want to analyze in IntelliJ IDEA.

### 3. Open Fluxora Modulith

Open the **Fluxora Modulith** tool window.

### 4. Explore Your Modules

Use:

- Module Graph
- Module Structure
- Module navigation

to understand the project's architecture.

### 5. Review Dependencies

Open Java source files and review any detected cross-module dependencies.

### 6. Apply Quick Fixes

Use **Alt+Enter** where supported to configure or resolve architecture issues.

### 7. Verify the Architecture

Use Architecture Verification to review the project's module boundaries and dependency rules.

## Documentation

Detailed documentation is available in the [`docs`](docs/) directory.

### Guides

- [Getting Started](docs/getting-started.md)
- [Module Detection](docs/module-detection.md)
- [`@ApplicationModule`](docs/application-module.md)
- [Allowed Dependencies](docs/allowed-dependencies.md)
- [Named Interfaces](docs/named-interfaces.md)
- [Dependency Graph](docs/dependency-graph.md)
- [Architecture Verification](docs/architecture-verification.md)
- [Inspections and Quick Fixes](docs/inspections-and-quick-fixes.md)
- [Configuration](docs/configuration.md)
- [Troubleshooting](docs/troubleshooting.md)

## Example Project Structure

Fluxora Modulith does not require a fixed package structure.

A project could look like:

```text
com.example.application
├── account
│   ├── api
│   ├── service
│   └── repository
├── billing
│   ├── api
│   └── service
├── order
│   ├── api
│   └── service
└── payment
    ├── api
    └── service
```

The actual structure is determined from the project being analyzed.

## Requirements

Fluxora Modulith requires an IntelliJ IDEA version compatible with the plugin's declared IntelliJ Platform version.

The project being analyzed should contain Java source code.

Spring Modulith-specific functionality depends on the Spring Modulith concepts and configuration used by the project.

## Privacy

Fluxora Modulith analyzes the project opened in IntelliJ IDEA to provide architecture information.

The plugin does not require application-specific source code to be uploaded to an external service for its core architecture analysis.

## Contributing

Contributions, bug reports, and feature discussions are welcome.

Please provide a minimal reproducible example when reporting an architecture-analysis issue whenever possible.

## Issues

Report issues through the project's GitHub issue tracker:

https://github.com/saleemjavid36

When reporting an issue, include:

- IntelliJ IDEA version
- Fluxora Modulith version
- Java version
- Relevant project/package structure
- Expected behavior
- Actual behavior
- Stack trace if an IDE error occurred

Do not include passwords, API keys, credentials, or other sensitive project information.

## License

See the project's license information for the applicable terms.

---

**Fluxora Modulith — understand, visualize, and enforce Spring Modulith-style architecture directly inside IntelliJ IDEA.**
