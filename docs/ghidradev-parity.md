# Eclipse GhidraDev parity plan

## Validated environment

| Component | Value |
|---|---|
| Operating system | Arch Linux |
| Ghidra runtime | 12.1.4 PUBLIC |
| Runtime path | `$HOME/ghidra/current` |
| Java | OpenJDK 21 |
| Extension Gradle baseline | Gradle 8.5 or later |
| Tested Gradle version | 8.13 |

## Confirmed Ghidra contracts

| Area | Confirmed behavior |
|---|---|
| Extension template | `GhidraBuild/Skeleton` in Ghidra source |
| Extension packaging | `distributeExtension` Gradle task |
| Ghidra installation property | `-PGHIDRA_INSTALL_DIR=<path>` or environment variable |
| Extension ZIP output | `<project>/dist/ghidra_<version>_<release>_<date>_<name>.zip` |
| User extension installation | `~/.config/ghidra/ghidra_<version>_<release>/Extensions/<name>/` |
| Java script source root | `ghidra_scripts/` |
| Ghidra GUI launcher | `ghidra.GhidraLauncher ghidra.GhidraRun` |
| Headless launcher | `ghidra.GhidraLauncher ghidra.app.util.headless.AnalyzeHeadless` |

## Feature matrix

| Feature | Eclipse GhidraDev | Planned IntelliJ support | Phase |
|---|---:|---:|---:|
| Registered Ghidra installations | Yes | Yes | MVP |
| Ghidra API classpath, sources, Javadocs | Yes | Yes | MVP |
| Link existing project to Ghidra | Yes | Yes | MVP |
| Ghidra Script Project | Yes | Yes | MVP |
| New Java Ghidra Script | Yes | Yes | MVP |
| New Python Ghidra Script | Yes | Yes | Phase 2 |
| Ghidra Module/Extension Project | Yes | Yes | MVP |
| Official Skeleton templates | Yes | Yes | MVP |
| Import existing module source | Yes | Yes, via Gradle recognition/import | MVP |
| Export/distribute extension | Yes | Yes | MVP |
| Install extension | Via Ghidra GUI | Yes, with safe ZIP extraction | Phase 2 |
| Ghidra GUI launcher | Yes | Yes | MVP |
| Headless launcher | Yes | Yes | MVP |
| Java debugging | Yes | Yes | MVP |
| PyGhidra run/debug | Yes | Planned | Later |
| Script Manager opens file in IDE | Yes, socket | Planned | Later |
| C/C++ symbol lookup | Yes, Eclipse CDT | Deferred | Later |
| Processor/SLEIGH support | Yes | Planned | Later |


## Validated extension debug workflow

Ghidra 12.1.4 PUBLIC was launched successfully through the custom IntelliJ
`Ghidra Launcher` Run Configuration in Debug mode.

A generated Plugin extension project was loaded directly from the IntelliJ project
classpath. The plugin was discovered in the CodeBrowser tool configuration under
`Examples`, where it could be enabled without packaging or installing an extension ZIP.

The following debugger workflow was validated:

```text
IntelliJ Debug
-> Ghidra Launcher starts Ghidra
-> CodeBrowser Configure Tool discovers the project plugin
-> enabling the plugin stops at IntelliJ constructor and init() breakpoints
-> provider and toolbar action become available in Ghidra
-> action callback stops at IntelliJ breakpoints
-> Resume Program continues the Ghidra UI thread
```

Development-classpath extensions do not appear in Ghidra's Extension Manager because
they are not installed under the user `Extensions/` directory. The Extension Manager
is used only for packaged and installed extension ZIPs.


## Existing IntelliJ foundation

The upstream `intellij-ghidra` code currently provides:

- a Ghidra facet;
- Ghidra JAR and source attachment;
- GUI and headless launch configurations;
- Java debugger integration.

It does not currently provide project generation, Gradle property injection, extension packaging,
extension installation, script generation, Javadoc attachment, or multi-version-safe libraries.
