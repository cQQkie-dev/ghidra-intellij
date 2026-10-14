# Architecture

## Goal

Provide an IntelliJ-native development experience broadly equivalent to GhidraDev for
Eclipse, focused on Ghidra extension, module, and script development.

The plugin supports development against installed Ghidra release distributions. A matching
Ghidra source checkout is optional and is used primarily to obtain the official
`GhidraBuild/Skeleton` extension template.

## Core concepts

### Ghidra runtime installation

A local Ghidra release installation used to compile, package, run, and debug projects.

Example:

```text
$HOME/ghidra/current
```

The configured path may be a symlink. The plugin preserves the configured path for display
and resolves its canonical path for validation and metadata discovery.

### Optional Ghidra source/Skeleton location

A matching source checkout, or an explicit `GhidraBuild/Skeleton` path, used to generate
official Ghidra extension projects.

Example:

```text
$HOME/git/ghidra-eclipsedev/GhidraBuild/Skeleton
```

A source checkout is not treated as a runtime Ghidra installation.

### Ghidra extension project

A Gradle project normally identified by:

```text
build.gradle
extension.properties
Module.manifest
```

Packaging uses the official Ghidra Gradle contract:

```text
./gradlew -PGHIDRA_INSTALL_DIR=<ghidra-installation> distributeExtension
```

### Ghidra script project

A Gradle-backed Java script workspace with:

```text
ghidra_scripts/
```

The project applies Ghidra's `support/buildExtension.gradle` to obtain the Ghidra API
compile classpath, but is not necessarily an installable extension.

## Architecture layers

```text
UI actions, settings, wizards, run configurations
                    |
                    v
Project and installation services
                    |
                    v
Pure Ghidra discovery, validation, generation, Gradle, and ZIP-install services
                    |
                    v
Filesystem, Gradle integration, IntelliJ Platform APIs
```

Pure filesystem and metadata logic should remain independent of IntelliJ UI APIs wherever
possible so it can be unit-tested.

## Design rules

- Ghidra owns extension build and packaging semantics.
- Generated projects use Ghidra's official Skeleton and `buildExtension.gradle` contract.
- Gradle-backed projects obtain their Ghidra compile and runtime classpath through
  Ghidra's official `support/buildExtension.gradle`.
- The Ghidra facet currently also attaches an installation-specific Ghidra library with
  matching class JARs, source ZIPs, and API Javadocs. IntelliJ requires matching class
  roots in the same library model to associate Ghidra source ZIPs and documentation
  with Gradle-managed Ghidra dependencies.
- Ghidra installations are versioned and may differ between projects.
- Absolute local Ghidra paths must not be written to version-controlled project files by default.
  Generated projects store `GHIDRA_INSTALL_DIR` in Git-ignored local `gradle.properties`.
- The plug-in reads the generated local Gradle property when automatically configuring the
  project facet after Gradle import.
- GUI and headless launch configurations must derive required VM settings from the selected
  Ghidra installation where possible.
