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