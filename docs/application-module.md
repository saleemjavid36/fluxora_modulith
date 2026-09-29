# `@ApplicationModule`

Fluxora Modulith understands Spring Modulith's package-level `@ApplicationModule` configuration where applicable.

Example:

```java
@ApplicationModule(
    allowedDependencies = {
        "payment"
    }
)
package com.example.shop.billing;
```

## Dependency rules

`allowedDependencies` can define which other application modules a module is explicitly allowed to depend on.

Fluxora Modulith uses actual annotation configuration when evaluating supported cross-module references.

Qualified dependency declarations are supported where applicable. Comments and arbitrary strings are not treated as actual dependency declarations.

Module and package names are always derived from the current project.
