package com.codingmates.ghidra.intellij.ide.extension

import com.codingmates.ghidra.intellij.ide.installation.GhidraInstallation
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.UUID
import java.util.zip.ZipFile
import kotlin.streams.toList

data class InstalledGhidraExtension(
    val archive: GhidraExtensionArchive,
    val installationDirectory: Path,
)

class GhidraExtensionInstallationException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)

/**
 * Safely installs a validated Ghidra extension archive.
 *
 * Existing extension directories are replaced only when [replaceExisting] is true. Callers must
 * obtain explicit user confirmation before requesting replacement.
 */
object GhidraExtensionInstaller {

    @Throws(GhidraExtensionInstallationException::class)
    fun install(
        archivePath: Path,
        ghidraInstallation: GhidraInstallation,
        extensionsDirectory: Path,
        replaceExisting: Boolean = false,
    ): InstalledGhidraExtension {
        val archive = try {
            GhidraExtensionArchiveInspector.inspect(archivePath)
        }
        catch (e: GhidraExtensionArchiveException) {
            throw GhidraExtensionInstallationException(e.message ?: "Invalid extension archive.", e)
        }

        if (archive.ghidraVersion != ghidraInstallation.version) {
            throw GhidraExtensionInstallationException(
                "Extension '${archive.extensionName}' targets Ghidra ${archive.ghidraVersion}, " +
                    "but the selected installation is Ghidra ${ghidraInstallation.version}."
            )
        }

        val normalizedExtensionsDirectory =
            extensionsDirectory.toAbsolutePath().normalize()

        try {
            Files.createDirectories(normalizedExtensionsDirectory)
        }
        catch (e: IOException) {
            throw GhidraExtensionInstallationException(
                "Cannot create Ghidra Extensions directory: $normalizedExtensionsDirectory",
                e,
            )
        }

        val targetDirectory = normalizedExtensionsDirectory
            .resolve(archive.rootDirectoryName)
            .normalize()

        if (!targetDirectory.startsWith(normalizedExtensionsDirectory)) {
            throw GhidraExtensionInstallationException(
                "Extension archive has an unsafe installation directory: " +
                    archive.rootDirectoryName
            )
        }

        if (Files.exists(targetDirectory) && !replaceExisting) {
            throw GhidraExtensionInstallationException(
                "Extension '${archive.rootDirectoryName}' is already installed at: " +
                    targetDirectory
            )
        }

        val stagingDirectory = normalizedExtensionsDirectory.resolve(
            ".installing-${archive.rootDirectoryName}-${UUID.randomUUID()}"
        )

        var backupDirectory: Path? = null

        try {
            Files.createDirectories(stagingDirectory)
            extractArchive(archive, stagingDirectory)

            val stagedExtensionDirectory =
                stagingDirectory.resolve(archive.rootDirectoryName)

            if (!Files.isDirectory(stagedExtensionDirectory)) {
                throw GhidraExtensionInstallationException(
                    "Extension archive did not produce its expected root directory: " +
                        archive.rootDirectoryName
                )
            }

            if (Files.exists(targetDirectory)) {
                backupDirectory = normalizedExtensionsDirectory.resolve(
                    ".replacing-${archive.rootDirectoryName}-${UUID.randomUUID()}"
                )

                Files.move(
                    targetDirectory,
                    backupDirectory,
                    StandardCopyOption.ATOMIC_MOVE,
                )
            }

            Files.move(
                stagedExtensionDirectory,
                targetDirectory,
                StandardCopyOption.ATOMIC_MOVE,
            )

            backupDirectory?.let(::deleteRecursively)

            return InstalledGhidraExtension(
                archive = archive,
                installationDirectory = targetDirectory,
            )
        }
        catch (e: GhidraExtensionInstallationException) {
            restoreBackup(
                backupDirectory = backupDirectory,
                targetDirectory = targetDirectory,
            )
            throw e
        }
        catch (e: IOException) {
            restoreBackup(
                backupDirectory = backupDirectory,
                targetDirectory = targetDirectory,
            )

            throw GhidraExtensionInstallationException(
                "Failed to install Ghidra extension '${archive.extensionName}'.",
                e,
            )
        }
        finally {
            deleteRecursively(stagingDirectory)
        }
    }

    @Throws(IOException::class, GhidraExtensionInstallationException::class)
    private fun extractArchive(
        archive: GhidraExtensionArchive,
        stagingDirectory: Path,
    ) {
        ZipFile(archive.archivePath.toFile()).use { zipFile ->
            val entries = zipFile.entries()

            while (entries.hasMoreElements()) {
                val entry = entries.nextElement()
                val destination = stagingDirectory
                    .resolve(entry.name)
                    .normalize()

                if (!destination.startsWith(stagingDirectory)) {
                    throw GhidraExtensionInstallationException(
                        "Extension archive contains an unsafe entry: ${entry.name}"
                    )
                }

                if (entry.isDirectory) {
                    Files.createDirectories(destination)
                }
                else {
                    Files.createDirectories(destination.parent)

                    zipFile.getInputStream(entry).use { input ->
                        Files.copy(input, destination)
                    }
                }
            }
        }
    }

    private fun restoreBackup(
        backupDirectory: Path?,
        targetDirectory: Path,
    ) {
        if (
            backupDirectory == null ||
            !Files.exists(backupDirectory) ||
            Files.exists(targetDirectory)
        ) {
            return
        }

        try {
            Files.move(
                backupDirectory,
                targetDirectory,
                StandardCopyOption.ATOMIC_MOVE,
            )
        }
        catch (_: IOException) {
            // Preserve the original installation failure. The backup remains available for recovery.
        }
    }

    private fun deleteRecursively(path: Path) {
        if (!Files.exists(path)) {
            return
        }

        try {
            Files.walk(path).use { paths ->
                paths
                    .toList()
                    .sortedDescending()
                    .forEach(Files::deleteIfExists)
            }
        }
        catch (_: IOException) {
            // A failed cleanup must not hide the actual installation result or error.
        }
    }
}