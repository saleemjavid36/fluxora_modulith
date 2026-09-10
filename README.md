# Spring Modulith Assistance — Community Edition

A Java-only IntelliJ IDEA plugin that provides Spring Modulith-style assistance without depending on the Ultimate-only Spring tooling.

## Features

- Detect modules from the current project's package structure.
- Default `DIRECT_SUB_PACKAGES` strategy: direct children of the configured/discovered root package are modules, even when they have no `package-info.java`.
- `EXPLICITLY_ANNOTATED` strategy: modules are declared with `@ApplicationModule`.
- Read `@ApplicationModule(open = true)` and `allowedDependencies` from `package-info.java`.
- Support module dependencies and qualified `module :: namedInterface` dependencies, including `*`.
- Recognize package-level and class-level `@NamedInterface`.
- Check cross-module API access using Java PSI resolution.
- Ignore JDK/third-party/unresolved references because only project source files participate in module analysis.
- Validate invalid `allowedDependencies` declarations.
- Completion and reference navigation for `allowedDependencies` values.
- Quick fixes for allowed dependencies, opening a module, and simplifying event listeners.
- Detect async transactional event listeners that can use `@ApplicationModuleListener`.
- Project-view module indicators.
- A lightweight Spring Modulith tool window showing detected modules.
- Project-scoped configuration for root package, module detection strategy, and inspections.

## Community edition boundary

This implementation intentionally uses IntelliJ Platform + Java PSI APIs and does not declare JetBrains' Ultimate Spring Modulith/Spring-specific modules. It is therefore suitable for IntelliJ IDEA Community where the Java plugin APIs used by the plugin are available.

The reference `spring-modulith_extracted` archive contains compiled JetBrains classes rather than source files. The implementation here is an independent Community-compatible implementation of the supported behavior, not a copy of JetBrains proprietary source.

## Build

Use IntelliJ IDEA's Gradle import with Java 21. The project targets IntelliJ IDEA Community 2025.3.5.

On Windows, if Gradle Wrapper files are present:

```text
.\\gradlew.bat build
.\\gradlew.bat runIde
```

## Test project example

```text
com.example.app
├── account
│   ├── AccountService.java
│   └── package-info.java
├── user
│   ├── UserService.java
│   └── package-info.java
├── order
│   └── OrderService.java
└── AppApplication.java
```

`account/package-info.java`:

```java
@ApplicationModule(allowedDependencies = {"user"})
package com.example.app.account;

import org.springframework.modulith.ApplicationModule;
```

A reference from `account` to `user`'s base-package public API is allowed. A reference to an internal package is reported unless that API is exposed through a named interface or the target module is open.

## Important for IntelliJ IDEA 2025.3+

The 2025.3 platform line uses the unified IntelliJ IDEA target in the IntelliJ Platform Gradle Plugin. The plugin itself declares only the Java plugin (`com.intellij.java`) and platform APIs, so it does not require the Ultimate Spring plugin. JetBrains documents the unified `IntellijIdea` target for 2025.3+ and the Community/Ultimate legacy helpers only for earlier releases. 
