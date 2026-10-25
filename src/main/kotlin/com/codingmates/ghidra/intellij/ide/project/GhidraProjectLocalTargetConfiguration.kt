package com.codingmates.ghidra.intellij.ide.project

import java.io.IOException
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

/**
 * Maintains the local Ghidra target configuration used by Gradle-backed Ghidra projects.
 *
 * The target remains machine-local because Ghidra installation paths are normally absolute.
 * The generated or updated `gradle.properties` file is therefore added to `.gitignore`.
 */
object GhidraProjectLocalTargetConfiguration {

    const val GHIDRA_INSTALL_DIR = "GHIDRA_INSTALL_DIR"

    /**
     * Writes [ghidraInstallationPath] as the project-local Ghidra target.
     *
     * Existing unrelated Gradle properties and comments are preserved. The resulting
     * `gradle.properties` file is ensured to be Git-ignored.
     *
     * @return the updated `gradle.properties` path.
     */
    @Throws(IOException::class)
    fun writeInstallation(
        projectRoot: Path,
        ghidraInstallationPath: Path,
    ): Path {
        val normalizedProjectRoot = projectRoot.toAbsolutePath().normalize()
        val normalizedInstallationPath =
            ghidraInstallationPath.toAbsolutePath().normalize()

        val propertiesFile = normalizedProjectRoot.resolve("gradle.properties")

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

        ensureGradlePropertiesIgnored(normalizedProjectRoot)

        return propertiesFile
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
        projectRoot: Path,
    ) {
        val gitIgnoreFile = projectRoot.resolve(".gitignore")

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