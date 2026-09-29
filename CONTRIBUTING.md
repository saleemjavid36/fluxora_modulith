# Contributing to Fluxora Modulith

Thank you for your interest in contributing to Fluxora Modulith!

Fluxora Modulith is an IntelliJ IDEA plugin for analyzing, visualizing,
and enforcing Spring Modulith-style module boundaries in Java applications.

We welcome contributions including:

- Bug fixes
- New inspections
- Quick fixes
- Module graph improvements
- Module structure improvements
- Performance improvements
- IntelliJ IDEA compatibility improvements
- Documentation improvements
- Tests
- UI/UX improvements

## Before You Start

For larger changes, please open an issue first so the proposed approach
can be discussed before implementation.

For small bug fixes and documentation changes, you may directly submit
a pull request.

## Development Setup

### Requirements

- JDK 21
- IntelliJ IDEA
- Gradle
- Git

### Clone the Repository

Fork the repository first and then clone your fork:

    git clone https://github.com/saleemjavid36/fluxora_modulith.git

Enter the project:

    cd fluxora_modulith

### Build

Run:

    ./gradlew build

On Windows:

    gradlew.bat build

### Run the Plugin

Run:

    ./gradlew runIde

This launches a development IntelliJ IDEA instance with the plugin installed.

## Development Workflow

Create a feature branch:

    git checkout -b feature/my-feature

For bug fixes:

    git checkout -b fix/my-bug

Make your changes and test them locally.

Then commit your changes:

    git add .
    git commit -m "Add support for XYZ"

Push your branch:

    git push origin feature/my-feature

Then open a Pull Request against the `main` branch.

## Pull Requests

Please ensure that:

- The project builds successfully.
- Existing functionality is not unnecessarily changed.
- New functionality includes appropriate tests where possible.
- Existing tests continue to pass.
- The Pull Request clearly explains the change.
- Related issues are referenced.
- UI changes include screenshots when useful.

## Code Style

Please follow the existing project structure and coding style.

Avoid unrelated changes in the same Pull Request.

Keep Pull Requests focused on one feature or problem whenever possible.

## Architecture

Fluxora Modulith is an IntelliJ IDEA plugin.

Before making architectural changes, please review the existing
module structure and documentation in the `docs` directory.

## Issues

Before opening a new issue, please search existing issues to avoid duplicates.

For bugs, include:

- IntelliJ IDEA version
- Plugin version
- Operating system
- JDK version
- Steps to reproduce
- Expected behavior
- Actual behavior
- Relevant logs or screenshots

## Security

Do not report security vulnerabilities through public GitHub issues.

Please use the security reporting process described in `SECURITY.md`.

## License

By contributing to this project, you agree that your contributions
will be licensed under the same license as this project.