# Named Interfaces

Named interfaces provide an explicit API surface for a module.

Conceptually:

```text
student
├── repository  ← exposed API
├── service
├── dto
└── internal
```

Another module can depend on the exposed API without automatically gaining permission to use every internal package.

Fluxora Modulith can use named-interface information when determining which parts of a module are intended to be consumed by other modules.

The exact package and interface names are determined by the application.
