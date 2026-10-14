package com.codingmates.ghidra.intellij.ide.script

import java.nio.file.Path

data class GhidraScriptProjectSpec(
    val name: String,
    val projectParentDirectory: Path,
    val ghidraInstallationPath: Path,
)

data class GeneratedGhidraScriptProject(
    val projectDirectory: Path,
    val buildFile: Path,
    val settingsFile: Path,
    val localGradlePropertiesFile: Path,
    val scriptDirectory: Path,
)

class GhidraScriptProjectGenerationException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)