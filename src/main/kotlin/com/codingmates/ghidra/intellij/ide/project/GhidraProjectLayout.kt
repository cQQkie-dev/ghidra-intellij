package com.codingmates.ghidra.intellij.ide.project

import java.nio.file.Path

enum class GhidraProjectKind {
    EXTENSION,
    SCRIPT,
    UNKNOWN,
}

/**
 * Filesystem-level description of a potential Ghidra Gradle project.
 */
data class GhidraProjectLayout(
    val projectRoot: Path,
    val kind: GhidraProjectKind,
    val buildFile: Path?,
    val usesGhidraExtensionBuild: Boolean,
) {
    val isGradleProject: Boolean
        get() = buildFile != null
}