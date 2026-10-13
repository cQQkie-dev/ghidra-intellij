package com.codingmates.ghidra.intellij.ide.extension

import java.nio.file.Path

enum class GhidraExtensionTemplate(
    val displayName: String,
    val skeletonFileName: String,
    val skeletonClassName: String,
    val generatedClassSuffix: String,
) {
    PLUGIN(
        displayName = "Plugin",
        skeletonFileName = "SkeletonPlugin.java",
        skeletonClassName = "SkeletonPlugin",
        generatedClassSuffix = "Plugin",
    ),
    ANALYZER(
        displayName = "Analyzer",
        skeletonFileName = "SkeletonAnalyzer.java",
        skeletonClassName = "SkeletonAnalyzer",
        generatedClassSuffix = "Analyzer",
    );

    override fun toString(): String = displayName
}

data class GhidraExtensionProjectSpec(
    val name: String,
    val projectParentDirectory: Path,
    val skeletonTemplatePath: Path,
    val ghidraInstallationPath: Path,
    val templates: Set<GhidraExtensionTemplate> = emptySet(),
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