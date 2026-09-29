# Module Detection

Fluxora Modulith determines logical modules from the current project's Java package structure and supported Spring Modulith conventions.

## Example

```text
com.example.shop
├── account
├── billing
├── order
└── payment
```

The actual package names are project-specific.

## Module boundaries

A module represents a logical application boundary rooted at a recognized package. Nested packages can belong to that logical module.

References inside the same logical module are not reported as cross-module dependencies.

## Cross-module dependencies

A dependency is relevant when a reference crosses from one recognized application module to another:

```text
billing → payment
```

The analyzer distinguishes this from references to:

- the same module;
- JDK classes;
- third-party libraries;
- unresolved symbols;
- packages that are not recognized as application modules.

Module discovery is based on the current project rather than hardcoded business-domain package names.
