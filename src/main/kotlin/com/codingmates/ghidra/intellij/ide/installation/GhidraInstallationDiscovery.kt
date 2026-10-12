package com.codingmates.ghidra.intellij.ide.installation

import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.util.Properties

object GhidraInstallationDiscovery {

    /**
     * Discovers metadata from a Ghidra release installation.
     *
     * The provided path may be a symlink. The returned object preserves it as
     * [GhidraInstallation.configuredPath] and stores the resolved path separately.
     */
    @Throws(GhidraInstallationException::class)
    fun discover(configuredPath: Path): GhidraInstallation {
        val normalizedConfiguredPath = configuredPath.toAbsolutePath().normalize()
        val canonicalPath = try {
            normalizedConfiguredPath.toRealPath()
        }
        catch (e: IOException) {
            throw GhidraInstallationException(
                "Cannot resolve Ghidra installation: $normalizedConfiguredPath",
                e
            )
        }

        val applicationPropertiesPath =
            canonicalPath.resolve("Ghidra/application.properties")

        if (!Files.isRegularFile(applicationPropertiesPath)) {
            throw GhidraInstallationException(
                "Not a Ghidra release installation: missing $applicationPropertiesPath"
            )
        }

        val properties = Properties()
        try {
            Files.newInputStream(applicationPropertiesPath).use(properties::load)
        }
        catch (e: IOException) {
            throw GhidraInstallationException(
                "Cannot read $applicationPropertiesPath",
                e
            )
        }

        val version = properties.required("application.version", applicationPropertiesPath)
        val releaseName =
            properties.required("application.release.name", applicationPropertiesPath)

        return GhidraInstallation(
            configuredPath = normalizedConfiguredPath,
            canonicalPath = canonicalPath,
            version = version,
            releaseName = releaseName,
            javaCompilerVersion = properties.optionalJavaVersion("application.java.compiler"),
            javaMinimumVersion = properties.optionalJavaVersion("application.java.min"),
            javaMaximumVersion = properties.optionalJavaVersion("application.java.max"),
            gradleMinimumVersion = properties.optional("application.gradle.min"),
            gradleMaximumVersion = properties.optional("application.gradle.max"),
        )
    }

    private fun Properties.required(key: String, source: Path): String =
        optional(key) ?: throw GhidraInstallationException(
            "Missing required property '$key' in $source"
        )

    private fun Properties.optional(key: String): String? =
        getProperty(key)?.trim()?.takeIf(String::isNotEmpty)

    private fun Properties.optionalJavaVersion(key: String): Int? {
        val value = optional(key) ?: return null
        return value.toIntOrNull()
            ?: throw GhidraInstallationException(
                "Invalid Java version '$value' for property '$key'"
            )
    }
}
