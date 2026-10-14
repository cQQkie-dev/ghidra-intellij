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
| Generated project local configuration | Git-ignored `gradle.properties` with `GHIDRA_INSTALL_DIR` |
| Processor/SLEIGH language build | `ant -f data/buildLanguage.xml`, using configured Ant executable or `ant` from PATH |
| Processor local Ant configuration | Git-ignored `.antProperties.xml` with `ghidra.install.dir` |

## Feature matrix

| Feature | Eclipse GhidraDev | IntelliJ support | Status |
|---|---:|---:|---:|
| Registered Ghidra installations | Yes | Yes | Complete |
| Ghidra API classpath, sources, Javadocs | Yes | Yes | Complete |
| Link existing project to Ghidra | Yes | Facet configuration | Complete |
| Ghidra Script Project | Yes | Yes | Complete |
| Shared user script directory | Yes | Optional `$HOME/ghidra_scripts` root for generated Script Projects | Complete |
| New Java Ghidra Script | Yes | Yes | Complete |
| New Python Ghidra Script | Yes | PyGhidra and Jython templates | Complete |
| Ghidra Module/Extension Project | Yes | Yes | Complete |
| Official Skeleton templates | Yes | Empty, Plugin, Analyzer, Loader, File System, Exporter, Processor/SLEIGH | Complete; Plugin, Loader, File System, Exporter, and Processor workflows runtime-validated |
| Automatic Gradle Wrapper generation | No | Yes, when configured Gradle is available | Complete |
| Open/import generated Gradle project | No | Notification action opens in a new IntelliJ window | Complete |
| Automatic generated-project facet setup | No | Full facet with classes, sources, Javadocs, and selected runtime | Complete |
| Automatic Extension development launcher | No | Created after generated Extension Project import | Complete |
| Import existing module source | Yes | Filesystem recognition and manual facet setup | Partial |
| Export/distribute extension | Yes | `distributeExtension` Gradle action | Complete |
| Install/update extension | Via Ghidra GUI | Safe IntelliJ ZIP installation and explicit replacement action | Complete |
| Ghidra GUI launcher | Yes | Yes | Complete |
| Headless launcher | Yes | `AnalyzeHeadless` through Ghidra Launcher Run Configuration | Complete |
| Java debugging | Yes | Direct classpath Extension debugging | Complete |
| Processor/SLEIGH language build | Yes | `Build SLEIGH Language` Ant action | Complete |
| PyGhidra run/debug | Yes | Planned | Later |
| Script Manager opens file in IDE | Yes, socket | Yes, Eclipse-compatible loopback listener | Complete |
| C/C++ symbol lookup | Yes, Eclipse CDT | Deferred | Later |
| Gradle source/Javadoc integration without duplicate facet roots | N/A | Planned | Later |

## Generated Extension Project workflow

The generated Extension Project workflow has been validated with Ghidra 12.1.4 PUBLIC:

```text
Tools -> New Ghidra Extension Project
-> generate project from official Skeleton
-> generate Gradle 8.13 Wrapper when configured
-> Open Project notification action
-> IntelliJ Gradle import
-> automatic full Ghidra facet configuration
-> Ghidra API source and Javadoc availability
-> automatic Ghidra development launcher creation
-> Debug
-> Ghidra starts under IntelliJ debugger
```

Generated Script Projects follow the same Wrapper, Open Project, Gradle import, and automatic
facet setup workflow. They do not automatically receive a development launcher.

Script Projects use a project-local `ghidra_scripts/` directory by default. The creation dialog
can instead select the shared user script directory:

```text
$HOME/ghidra_scripts
```
The selected shared root is stored only in ignored local `gradle.properties` as `GHIDRA_SCRIPT_DIR`. New Ghidra Script defaults to the selected root, and scripts in the shared directory are immediately visible to Ghidra Script Manager.

For generated Processor/SLEIGH projects:
```text
Tools -> Build SLEIGH Language
-> regenerate .antProperties.xml from selected facet runtime
-> ant -f data/buildLanguage.xml
-> .slaspec files compile to .sla files
```

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

## Validated Script Manager editor integration

The IntelliJ plug-in provides an Eclipse GhidraDev-compatible Script Editor listener.

The listener is disabled by default and, when enabled, binds only to:

```text
127.0.0.1:12321
```

It accepts the existing Ghidra Eclipse Integration protocol:
```
open_/absolute/path/to/script.java
```

The following workflow was validated with Ghidra 12.1.4 PUBLIC:

```text
IntelliJ Settings -> Tools -> Ghidra Script Editor
-> enable listener on port 12321
-> Ghidra Edit -> Tool Options -> Script Editor Port = 12321
-> Ghidra Script Manager -> Edit with Eclipse
-> requested script opens in IntelliJ
```

No Eclipse installation directory or Eclipse workspace configuration is required for this IntelliJ listener. The Ghidra action remains named Edit with Eclipse because it is part of Ghidra's existing Eclipse Integration UI.

## Validated headless workflow

Ghidra 12.1.4 PUBLIC headless mode was successfully launched through the custom IntelliJ
`Ghidra Launcher` Run Configuration.

The following command arguments were validated:

```text
/tmp/ghidra-intellij-headless HeadlessSandbox -import /usr/bin/true -noanalysis
```

The parent project directory must already exist before invoking AnalyzeHeadless:
```
mkdir -p /tmp/ghidra-intellij-headless
```

Validated workflow:
```text
IntelliJ Run
-> ghidra.GhidraLauncher ghidra.app.util.headless.AnalyzeHeadless
-> Ghidra project created
-> ELF input imported
-> analysis skipped with -noanalysis
-> project saved successfully
-> process exits with code 0
```

## Validated extension template workflows

The following official Skeleton template workflows have been validated against Ghidra 12.1.4 PUBLIC
through direct IntelliJ classpath debugging:

| Template | Validated behavior |
|---|---|
| Plugin | Ghidra CodeBrowser discovers the plugin; constructor, `init()`, and action breakpoints hit; generated provider and toolbar action work |
| Loader | Loader recognition and `load()` breakpoints hit; Ghidra offers the custom loader and imports a matching test file |
| File System | Probe, factory creation, mount, file listing, and byte-provider breakpoints hit; a mounted test file system exposes and imports a payload file |
| Exporter | Ghidra offers the custom exporter; domain-object capability and export breakpoints hit; exporter writes the selected output file |
| Processor/SLEIGH | Official Ant build compiles the Skeleton `.slaspec` to `.sla`; generated extension ZIP includes the `.sla`; Ghidra recognizes the installed extension |

The Loader, File System, and Exporter Skeletons intentionally require developers to implement their
format-specific recognition and processing logic before they provide useful behavior.

## Upstream foundation and current implementation

The initial IntelliJ foundation imported from `sarog/intellij-ghidra` provided:

- a Ghidra facet;
- Ghidra JAR and source attachment;
- GUI and headless launch configurations;
- Java debugger runner integration.

This project has substantially extended that foundation with:

- installation discovery and global multi-installation registry;
- installation-specific Ghidra libraries and API Javadocs;
- dynamic Ghidra `launch.properties` VM arguments;
- Script Project, Extension Project, and script generation;
- official Skeleton template selection;
- Extension templates for Plugin, Analyzer, Loader, File System, Exporter, and Processor/SLEIGH;
- configured Gradle fallback and automatic wrapper generation;
- Gradle project opening/import notification actions;
- automatic generated-project facet configuration;
- automatic Extension development launcher creation;
- extension distribution, artifact discovery, archive validation, safe installation, and explicit replacement;
- Processor/SLEIGH Ant build support.
