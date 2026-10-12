package com.codingmates.ghidra.intellij.ide.installation

import java.nio.file.Path

/**
 * Metadata discovered from a local Ghidra release installation.
 *
 * [configuredPath] preserves the path chosen by the user, which may be a symlink.
 * [canonicalPath] resolves symlinks and is used for validation and filesystem access.
 */
data class GhidraInstallation(
    val configuredPath: Path,
    val canonicalPath: Path,
    val version: String,
    val releaseName: String,
    val javaCompilerVersion: Int?,
    val javaMinimumVersion: Int?,
    val javaMaximumVersion: Int?,
    val gradleMinimumVersion: String?,
    val gradleMaximumVersion: String?,
) {
    val applicationPropertiesPath: Path
        get() = canonicalPath.resolve("Ghidra/application.properties")

    val supportDirectory: Path
        get() = canonicalPath.resolve("support")

    val buildExtensionScript: Path
        get() = supportDirectory.resolve("buildExtension.gradle")

    val launchPropertiesPath: Path
        get() = supportDirectory.resolve("launch.properties")

    val apiJavadocPath: Path
        get() = canonicalPath.resolve("docs/GhidraAPI_javadoc.zip")

    val userSettingsDirectoryName: String
        get() = "ghidra_${version}_${releaseName}"

    fun userExtensionsDirectory(userSettingsRoot: Path): Path =
        userSettingsRoot.resolve(userSettingsDirectoryName).resolve("Extensions")
}
