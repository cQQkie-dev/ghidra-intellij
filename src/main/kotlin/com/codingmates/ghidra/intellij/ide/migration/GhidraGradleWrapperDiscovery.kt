package com.codingmates.ghidra.intellij.ide.migration

import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.util.Properties

/**
 * Discovers the Gradle version configured by a project's Gradle Wrapper.
 */
object GhidraGradleWrapperDiscovery {

    private const val DISTRIBUTION_URL = "distributionUrl"

    private val distributionVersionPattern = Regex(
        """.*/gradle-(\d+(?:\.\d+)*)-(?:bin|all)\.zip(?:[?#].*)?$"""
    )

    fun discover(projectRoot: Path): GhidraGradleWrapperInfo? {
        val normalizedProjectRoot = projectRoot.toAbsolutePath().normalize()

        val propertiesFile = normalizedProjectRoot.resolve(
            "gradle/wrapper/gradle-wrapper.properties"
        )

        if (!Files.isRegularFile(propertiesFile)) {
            return null
        }

        val properties = Properties()

        try {
            Files.newInputStream(propertiesFile).use(properties::load)
        }
        catch (_: IOException) {
            return GhidraGradleWrapperInfo(
                propertiesFile = propertiesFile,
                distributionUrl = null,
                version = null,
            )
        }

        val distributionUrl = properties
            .getProperty(DISTRIBUTION_URL)
            ?.trim()
            ?.takeIf(String::isNotEmpty)

        val version = distributionUrl
            ?.let(distributionVersionPattern::matchEntire)
            ?.groupValues
            ?.getOrNull(1)
            ?.let(GhidraGradleVersion::parseOrNull)

        return GhidraGradleWrapperInfo(
            propertiesFile = propertiesFile,
            distributionUrl = distributionUrl,
            version = version,
        )
    }
}

data class GhidraGradleWrapperInfo(
    val propertiesFile: Path,
    val distributionUrl: String?,
    val version: GhidraGradleVersion?,
)