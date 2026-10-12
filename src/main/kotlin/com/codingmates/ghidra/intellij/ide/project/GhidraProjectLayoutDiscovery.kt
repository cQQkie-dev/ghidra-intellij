package com.codingmates.ghidra.intellij.ide.project

import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path

object GhidraProjectLayoutDiscovery {

    fun discover(projectRoot: Path): GhidraProjectLayout {
        val normalizedProjectRoot = projectRoot.toAbsolutePath().normalize()
        val buildFile = findGradleBuildFile(normalizedProjectRoot)
        val usesGhidraExtensionBuild = buildFile?.let(::usesGhidraExtensionBuild) ?: false

        val isExtensionProject =
            buildFile != null &&
                Files.isRegularFile(normalizedProjectRoot.resolve("extension.properties")) &&
                Files.isRegularFile(normalizedProjectRoot.resolve("Module.manifest"))

        val isScriptProject =
            buildFile != null &&
                Files.isDirectory(normalizedProjectRoot.resolve("ghidra_scripts"))

        val kind = when {
            isExtensionProject -> GhidraProjectKind.EXTENSION
            isScriptProject -> GhidraProjectKind.SCRIPT
            else -> GhidraProjectKind.UNKNOWN
        }

        return GhidraProjectLayout(
            projectRoot = normalizedProjectRoot,
            kind = kind,
            buildFile = buildFile,
            usesGhidraExtensionBuild = usesGhidraExtensionBuild,
        )
    }

    private fun findGradleBuildFile(projectRoot: Path): Path? {
        val groovyBuildFile = projectRoot.resolve("build.gradle")
        if (Files.isRegularFile(groovyBuildFile)) {
            return groovyBuildFile
        }

        val kotlinBuildFile = projectRoot.resolve("build.gradle.kts")
        if (Files.isRegularFile(kotlinBuildFile)) {
            return kotlinBuildFile
        }

        return null
    }

    private fun usesGhidraExtensionBuild(buildFile: Path): Boolean =
        try {
            Files.readString(buildFile).contains("buildExtension.gradle")
        }
        catch (_: IOException) {
            false
        }
}