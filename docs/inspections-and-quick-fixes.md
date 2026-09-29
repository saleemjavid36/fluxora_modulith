# Inspections and Quick Fixes

Fluxora Modulith integrates with IntelliJ editor inspections to surface supported architecture issues in Java source code.

## Cross-module dependency

A reference that crosses a recognized module boundary can be reported when it violates applicable dependency rules.

Example:

```text
billing → payment
```

## Quick fixes

The Alt+Enter menu can provide supported actions such as:

- Add the target module as an allowed dependency.
- Suppress inspection.
- Add a dependency to `@ApplicationModule`.
- Navigate to the target module.

The exact actions depend on the detected situation.

## Same-module references

References between classes/packages in the same logical module are not treated as cross-module violations.

## Ignored references

The analyzer avoids reporting unsupported or irrelevant references such as JDK types, third-party library types, unresolved symbols, and references that do not cross a recognized application-module boundary.
