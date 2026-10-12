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
 * Existing extension directories are never overwritten. Replacement support will be added by a
 * later user-facing action that explicitly asks for confirmation.
 */
object GhidraExtensionInstaller {

    @Throws(GhidraExtensionInstallationException::class)
    fun install(
        archivePath: Path,
        ghidraInstallation: GhidraInstallation,
        extensionsDirectory: Path,
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

        if (Files.exists(targetDirectory)) {
            throw GhidraExtensionInstallationException(
                "Extension '${archive.rootDirectoryName}' is already installed at: " +
                    targetDirectory
            )
        }

        val stagingDirectory = normalizedExtensionsDirectory.resolve(
            ".installing-${archive.rootDirectoryName}-${UUID.randomUUID()}"
        )

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

            Files.move(
                stagedExtensionDirectory,
                targetDirectory,
                StandardCopyOption.ATOMIC_MOVE,
            )

            return InstalledGhidraExtension(
                archive = archive,
                installationDirectory = targetDirectory,
            )
        }
        catch (e: GhidraExtensionInstallationException) {
            throw e
        }
        catch (e: IOException) {
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