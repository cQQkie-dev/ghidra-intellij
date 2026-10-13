package com.codingmates.ghidra.intellij.ide.extension

import java.nio.file.Path

/**
 * Validated official Ghidra Skeleton template directory.
 *
 * This usually comes from a matching Ghidra source checkout:
 *
 * `<source-root>/GhidraBuild/Skeleton`
 */
data class GhidraSkeletonTemplate(
    val configuredPath: Path,
    val canonicalPath: Path,
) {
    val buildTemplateFile: Path
        get() = canonicalPath.resolve("buildTemplate.gradle")

    val sourceBuildFile: Path
        get() = canonicalPath.resolve("build.gradle")

    val extensionPropertiesFile: Path
        get() = canonicalPath.resolve("extension.properties")

    val moduleManifestFile: Path
        get() = canonicalPath.resolve("Module.manifest")

    val gitIgnoreTemplateFile: Path
        get() = canonicalPath.resolve("gitignore")

    val javaTemplateDirectory: Path
        get() = canonicalPath.resolve("src/main/java/skeleton")
}

class GhidraSkeletonTemplateException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)