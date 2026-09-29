# Architecture Verification

Architecture Verification provides a dedicated view for reviewing the recognized modular architecture.

Depending on the project and configured rules, verification can cover:

- recognized modules;
- module dependencies;
- allowed dependency rules;
- named interfaces;
- cross-module dependencies;
- dependency cycles.

## Workflow

```text
Discover modules
      ↓
Analyze dependencies
      ↓
Apply module rules
      ↓
Report detected issues
      ↓
Review or export results
```

The verification export represents a snapshot of the analyzed project state.
