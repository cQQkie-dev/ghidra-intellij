package com.codingmates.ghidra.intellij.ide.project

import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.util.Properties

/**
 * Finds the Ghidra installation configured by generated Ghidra Gradle projects.
 *
 * Extension projects use Gradle's conventional local [gradle.properties] file.
 * Script projects currently use the legacy plugin-local [ghidra-intellij.properties] file.
 */
object GhidraProjectInstallationDiscovery {

    private const val GHIDRA_INSTALL_DIR = "GHIDRA_INSTALL_DIR"

    private val localPropertiesFileNames = listOf(
        "gradle.properties",
        "ghidra-intellij.properties",
    )

    fun discover(projectRoot: Path): Path? {
        val normalizedProjectRoot = projectRoot.toAbsolutePath().normalize()

        return localPropertiesFileNames
            .asSequence()
            .map { normalizedProjectRoot.resolve(it) }
            .mapNotNull(::readInstallationPath)
            .firstOrNull()
    }

    /**
     * Returns an optional shared Ghidra script directory configured in local project properties.
     *
     * If this returns null, callers should use the project's local ghidra_scripts directory.
     */
    fun discoverScriptDirectory(projectRoot: Path): Path? {
        val normalizedProjectRoot = projectRoot.toAbsolutePath().normalize()

        return localPropertiesFileNames
            .asSequence()
            .map { normalizedProjectRoot.resolve(it) }
            .mapNotNull(::readScriptDirectory)
            .firstOrNull()
    }

    private fun readScriptDirectory(propertiesFile: Path): Path? {
        if (!Files.isRegularFile(propertiesFile)) {
            return null
        }

        val properties = Properties()

        try {
            Files.newInputStream(propertiesFile).use(properties::load)
        }
        catch (_: IOException) {
            return null
        }

        val configuredPath = properties
            .getProperty("GHIDRA_SCRIPT_DIR")
            ?.trim()
            ?.takeIf(String::isNotEmpty)
            ?: return null

        return runCatching {
            Path.of(configuredPath)
                .toAbsolutePath()
                .normalize()
        }.getOrNull()
    }

    private fun readInstallationPath(propertiesFile: Path): Path? {
        if (!Files.isRegularFile(propertiesFile)) {
            return null
        }

        val properties = Properties()

        try {
            Files.newInputStream(propertiesFile).use(properties::load)
        }
        catch (_: IOException) {
            return null
        }

        val configuredPath = properties
            .getProperty(GHIDRA_INSTALL_DIR)
            ?.trim()
            ?.takeIf(String::isNotEmpty)
            ?: return null

        return try {
            Path.of(configuredPath)
        }
        catch (_: Exception) {
            null
        }
    }
}