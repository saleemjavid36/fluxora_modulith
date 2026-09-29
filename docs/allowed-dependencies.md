# Allowed Dependencies

`allowedDependencies` defines explicit module dependency rules.

## Example

```java
@ApplicationModule(
    allowedDependencies = {
        "payment"
    }
)
package com.example.shop.billing;
```

This allows the `billing` module to declare its dependency on `payment`.

## Inspection behavior

When a recognized module references another module, Fluxora Modulith evaluates the applicable dependency rule.

If the dependency is permitted, the supported inspection does not report it as an unwanted dependency.

If it is not permitted, the plugin can report the cross-module dependency and offer supported quick fixes.

## Quick fixes

Depending on the detected situation, the Alt+Enter menu can provide actions such as:

- Add the target module as an allowed dependency.
- Add the dependency to `@ApplicationModule`.
- Navigate to the target module.
- Suppress the inspection.

Supported dependency quick fixes should avoid adding duplicates and preserve existing configuration/comments.
