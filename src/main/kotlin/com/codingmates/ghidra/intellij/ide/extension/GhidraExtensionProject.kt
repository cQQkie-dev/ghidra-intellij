package com.codingmates.ghidra.intellij.ide.extension

import java.nio.file.Path

data class GhidraExtensionProjectSpec(
    val name: String,
    val projectParentDirectory: Path,
    val skeletonTemplatePath: Path,
    val ghidraInstallationPath: Path,
)

data class GeneratedGhidraExtensionProject(
    val projectDirectory: Path,
    val buildFile: Path,
    val settingsFile: Path,
    val localGradlePropertiesFile: Path,
)

class GhidraExtensionProjectGenerationException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)