# Troubleshooting

## No modules appear

Check that:

1. The project contains Java source files.
2. IntelliJ IDEA has finished indexing.
3. Expected application packages are available to Java PSI.
4. Project-specific configuration is valid, if used.

## A dependency is not reported

Check whether:

- source and target belong to the same logical module;
- the target is a JDK or third-party type;
- the symbol is unresolved;
- the dependency is explicitly allowed;
- the target package is recognized as an application module.

## A dependency is reported unexpectedly

Review the detected module boundaries and applicable `@ApplicationModule` / dependency configuration.

## IDE internal threading error

PSI and project-model reads must use the appropriate IntelliJ Platform read-access rules. UI/editor navigation should be separated from PSI analysis where necessary.

## Reporting a bug

Include the IntelliJ IDEA version, plugin version, Java version, a minimal reproducible project or package structure, the inspection message, and the stack trace if an IDE internal error occurred.

Do not include credentials, API keys, or other sensitive project information.
