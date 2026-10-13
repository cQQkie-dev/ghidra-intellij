package com.codingmates.ghidra.intellij.ide.gradle

import com.codingmates.ghidra.intellij.ide.installation.GhidraPlatform
import java.nio.file.Files
import java.nio.file.Path

object GhidraGradleCommandBuilder {

    const val DISTRIBUTE_EXTENSION_TASK = "distributeExtension"

    /**
     * Builds the Gradle command used to package a Ghidra extension.
     *
     * The project Gradle Wrapper is preferred. When no wrapper exists, Gradle is resolved through
     * the user's PATH.
     */
    fun distributeExtension(
        projectRoot: Path,
        ghidraInstallationPath: Path,
        gradleExecutablePath: Path? = null,
        platform: GhidraPlatform = GhidraPlatform.current(),
    ): GhidraGradleCommand {
        val normalizedProjectRoot = projectRoot.toAbsolutePath().normalize()
        val wrapperPath = wrapperPath(normalizedProjectRoot, platform)

        return GhidraGradleCommand(
            executable = wrapperPath?.toString()
                ?: gradleExecutablePath?.toAbsolutePath()?.normalize()?.toString()
                ?: "gradle",
            arguments = listOf(
                "-PGHIDRA_INSTALL_DIR=${ghidraInstallationPath.toAbsolutePath().normalize()}",
                DISTRIBUTE_EXTENSION_TASK,
            ),
            workingDirectory = normalizedProjectRoot,
            usesWrapper = wrapperPath != null,
        )
    }

    private fun wrapperPath(
        projectRoot: Path,
        platform: GhidraPlatform,
    ): Path? {
        val wrapperName = when (platform) {
            GhidraPlatform.WINDOWS -> "gradlew.bat"
            else -> "gradlew"
        }

        val wrapperPath = projectRoot.resolve(wrapperName)
        return wrapperPath.takeIf(Files::isRegularFile)
    }
}