package com.codingmates.ghidra.intellij.ide.gradle

import com.codingmates.ghidra.intellij.ide.migration.GhidraGradleVersion

/**
 * Extracts the Gradle version from the standard `gradle --version` output.
 */
object GhidraGradleExecutableVersionInspector {

    private val versionPattern = Regex(
        """(?m)^Gradle\s+(\d+(?:\.\d+)*)\s*$"""
    )

    fun parseVersion(output: String): GhidraGradleVersion? =
        versionPattern
            .find(output)
            ?.groupValues
            ?.getOrNull(1)
            ?.let(GhidraGradleVersion::parseOrNull)
}