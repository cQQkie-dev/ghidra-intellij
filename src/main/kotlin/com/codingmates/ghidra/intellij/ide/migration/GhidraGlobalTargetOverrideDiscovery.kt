package com.codingmates.ghidra.intellij.ide.migration

import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.util.Properties

/**
 * Detects global Ghidra target inputs that can override a project-local target.
 *
 * The official Ghidra Skeleton gives GHIDRA_INSTALL_DIR from the process environment
 * precedence over Gradle project properties. Gradle user-home properties can also override
 * project-local gradle.properties during ordinary Gradle sync and build operations.
 */
object GhidraGlobalTargetOverrideDiscovery {

    const val GHIDRA_INSTALL_DIR = "GHIDRA_INSTALL_DIR"

    fun discoverEnvironmentOverride(
        environment: Map<String, String>,
    ): GhidraTargetOverride? =
        environment[GHIDRA_INSTALL_DIR]
            ?.trim()
            ?.takeIf(String::isNotEmpty)
            ?.let { configuredValue ->
                GhidraTargetOverride(
                    source = GhidraTargetOverrideSource.ENVIRONMENT,
                    configuredValue = configuredValue,
                    installationPath = parsePathOrNull(configuredValue),
                    propertiesFile = null,
                )
            }

    fun discoverUserGradlePropertiesOverride(
        gradleUserHome: Path,
    ): GhidraTargetOverride? {
        val propertiesFile = gradleUserHome
            .toAbsolutePath()
            .normalize()
            .resolve("gradle.properties")

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

        val configuredValue = properties
            .getProperty(GHIDRA_INSTALL_DIR)
            ?.trim()
            ?.takeIf(String::isNotEmpty)
            ?: return null

        return GhidraTargetOverride(
            source = GhidraTargetOverrideSource.USER_GRADLE_PROPERTIES,
            configuredValue = configuredValue,
            installationPath = parsePathOrNull(configuredValue),
            propertiesFile = propertiesFile,
        )
    }

    private fun parsePathOrNull(value: String): Path? =
        runCatching {
            Path.of(value)
                .toAbsolutePath()
                .normalize()
        }.getOrNull()
}

enum class GhidraTargetOverrideSource {
    ENVIRONMENT,
    USER_GRADLE_PROPERTIES,
}

data class GhidraTargetOverride(
    val source: GhidraTargetOverrideSource,
    val configuredValue: String,
    val installationPath: Path?,
    val propertiesFile: Path?,
)