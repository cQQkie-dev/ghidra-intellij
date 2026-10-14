package com.codingmates.ghidra.intellij.ide.extension

import com.codingmates.ghidra.intellij.ide.installation.GhidraInstallationDiscovery
import com.codingmates.ghidra.intellij.ide.project.GhidraProjectInstallationDiscovery
import com.codingmates.ghidra.intellij.ide.project.GhidraProjectKind
import com.codingmates.ghidra.intellij.ide.project.GhidraProjectLayoutDiscovery
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import kotlin.streams.toList

data class GhidraExtensionSourceProject(
    val projectDirectory: Path,
    val configuredInstallationPath: Path?,
    val configuredGhidraVersion: String?,
    val historicalGhidraVersions: Set<String>,
) {
    val detectedGhidraVersions: Set<String>
        get() = buildSet {
            configuredGhidraVersion?.let(::add)
            addAll(historicalGhidraVersions)
        }

    /**
     * Returns a migration warning when [targetGhidraVersion] is not among the detected historical
     * or locally configured project targets.
     */
    fun migrationWarning(targetGhidraVersion: String): String? {
        val detectedVersions = detectedGhidraVersions

        if (detectedVersions.isEmpty() || targetGhidraVersion in detectedVersions) {
            return null
        }

        return buildString {
            append(
                "This extension source appears to target Ghidra version(s): " +
                    detectedVersions.sorted().joinToString(", ") +
                    ".\n\n"
            )
            append("Selected target: Ghidra $targetGhidraVersion.\n\n")
            append(
                "Import updates only local build/runtime configuration. It does not " +
                    "automatically migrate Java API changes, deprecated APIs, third-party " +
                    "dependencies, or Processor/SLEIGH definitions. Compile and test the " +
                    "project after import."
            )
        }
    }
}

class GhidraExtensionSourceImportException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)

/**
 * Inspects and prepares an existing Gradle-backed Ghidra extension source directory.
 */
object GhidraExtensionSourceImporter {

    private const val GHIDRA_INSTALL_DIR = "GHIDRA_INSTALL_DIR"

    @Throws(GhidraExtensionSourceImportException::class)
    fun inspect(projectDirectory: Path): GhidraExtensionSourceProject {
        val normalizedProjectDirectory =
            projectDirectory.toAbsolutePath().normalize()

        val layout = GhidraProjectLayoutDiscovery.discover(
            normalizedProjectDirectory
        )

        if (layout.kind != GhidraProjectKind.EXTENSION) {
            throw GhidraExtensionSourceImportException(
                "Not a recognized Ghidra extension source directory. Expected build.gradle, " +
                    "extension.properties, and Module.manifest: $normalizedProjectDirectory"
            )
        }

        val configuredInstallationPath =
            GhidraProjectInstallationDiscovery.discover(
                normalizedProjectDirectory
            )

        val configuredGhidraVersion = configuredInstallationPath
            ?.let { path ->
                runCatching {
                    GhidraInstallationDiscovery.discover(path).version
                }.getOrNull()
            }

        return GhidraExtensionSourceProject(
            projectDirectory = normalizedProjectDirectory,
            configuredInstallationPath = configuredInstallationPath,
            configuredGhidraVersion = configuredGhidraVersion,
            historicalGhidraVersions =
                discoverHistoricalGhidraVersions(normalizedProjectDirectory),
        )
    }

    /**
     * Writes or updates the machine-local Ghidra target property and ensures the local file is
     * ignored by Git. Existing unrelated Gradle properties and comments are preserved.
     */
    @Throws(GhidraExtensionSourceImportException::class)
    fun configureLocalInstallation(
        projectDirectory: Path,
        ghidraInstallationPath: Path,
    ): Path {
        val sourceProject = inspect(projectDirectory)
        val normalizedInstallationPath =
            ghidraInstallationPath.toAbsolutePath().normalize()

        val propertiesFile =
            sourceProject.projectDirectory.resolve("gradle.properties")

        try {
            val existingContent = if (Files.isRegularFile(propertiesFile)) {
                Files.readString(propertiesFile, StandardCharsets.UTF_8)
            }
            else {
                ""
            }

            Files.writeString(
                propertiesFile,
                upsertProperty(
                    content = existingContent,
                    key = GHIDRA_INSTALL_DIR,
                    value = normalizedInstallationPath.toString(),
                ),
                StandardCharsets.UTF_8,
            )

            ensureGradlePropertiesIgnored(sourceProject.projectDirectory)

            return propertiesFile
        }
        catch (e: IOException) {
            throw GhidraExtensionSourceImportException(
                "Cannot configure local Ghidra installation for: " +
                    sourceProject.projectDirectory,
                e,
            )
        }
    }

    private fun discoverHistoricalGhidraVersions(
        projectDirectory: Path,
    ): Set<String> {
        val distributionDirectory = projectDirectory.resolve("dist")

        if (!Files.isDirectory(distributionDirectory)) {
            return emptySet()
        }

        return try {
            Files.list(distributionDirectory).use { entries ->
                entries
                    .filter(Files::isRegularFile)
                    .filter { path ->
                        path.fileName.toString()
                            .endsWith(".zip", ignoreCase = true)
                    }
                    .toList()
                    .mapNotNull { archivePath ->
                        runCatching {
                            GhidraExtensionArchiveInspector.inspect(
                                archivePath
                            ).ghidraVersion
                        }.getOrNull()
                    }
                    .toSortedSet()
            }
        }
        catch (_: IOException) {
            emptySet()
        }
    }

    private fun upsertProperty(
        content: String,
        key: String,
        value: String,
    ): String {
        val propertyLine = "$key=$value"
        val propertyPattern = Regex(
            """(?m)^[\t ]*${Regex.escape(key)}[\t ]*=.*$"""
        )

        if (propertyPattern.containsMatchIn(content)) {
            return propertyPattern.replace(content, propertyLine)
        }

        val separator = when {
            content.isEmpty() -> ""
            content.endsWith("\n") -> ""
            else -> "\n"
        }

        return content + separator + propertyLine + "\n"
    }

    @Throws(IOException::class)
    private fun ensureGradlePropertiesIgnored(
        projectDirectory: Path,
    ) {
        val gitIgnoreFile = projectDirectory.resolve(".gitignore")

        val existingContent = if (Files.isRegularFile(gitIgnoreFile)) {
            Files.readString(gitIgnoreFile, StandardCharsets.UTF_8)
        }
        else {
            ""
        }

        val alreadyIgnored = existingContent
            .lineSequence()
            .map(String::trim)
            .any { line ->
                line == "gradle.properties" ||
                    line == "/gradle.properties"
            }

        if (alreadyIgnored) {
            return
        }

        val separator = when {
            existingContent.isEmpty() -> ""
            existingContent.endsWith("\n") -> "\n"
            else -> "\n\n"
        }

        Files.writeString(
            gitIgnoreFile,
            existingContent +
                separator +
                "# Local Ghidra target configuration\n" +
                "gradle.properties\n",
            StandardCharsets.UTF_8,
        )
    }
}