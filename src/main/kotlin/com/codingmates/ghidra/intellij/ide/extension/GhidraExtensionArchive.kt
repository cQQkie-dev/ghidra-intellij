package com.codingmates.ghidra.intellij.ide.extension

import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.util.Properties
import java.util.zip.ZipFile

data class GhidraExtensionArchive(
    val archivePath: Path,
    val rootDirectoryName: String,
    val extensionName: String,
    val ghidraVersion: String,
)

class GhidraExtensionArchiveException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)

object GhidraExtensionArchiveInspector {

    /**
     * Validates a Ghidra extension ZIP without extracting it.
     *
     * Valid archives contain exactly one top-level directory with:
     *
     * - extension.properties
     * - Module.manifest
     */
    @Throws(GhidraExtensionArchiveException::class)
    fun inspect(archivePath: Path): GhidraExtensionArchive {
        val normalizedArchivePath = archivePath.toAbsolutePath().normalize()

        if (!Files.isRegularFile(normalizedArchivePath)) {
            throw GhidraExtensionArchiveException(
                "Ghidra extension archive does not exist: $normalizedArchivePath"
            )
        }

        try {
            ZipFile(normalizedArchivePath.toFile()).use { zipFile ->
                val rootDirectories = linkedSetOf<String>()

                val entries = zipFile.entries()
                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    val entryName = entry.name

                    validateEntryName(entryName, normalizedArchivePath)

                    val normalizedEntryName = entryName.removeSuffix("/")
                    if (normalizedEntryName.isNotEmpty()) {
                        rootDirectories.add(normalizedEntryName.substringBefore('/'))
                    }
                }

                if (rootDirectories.size != 1) {
                    throw GhidraExtensionArchiveException(
                        "Ghidra extension archive must contain exactly one top-level directory: " +
                            "$normalizedArchivePath"
                    )
                }

                val rootDirectoryName = rootDirectories.single()

                val propertiesEntry =
                    zipFile.getEntry("$rootDirectoryName/extension.properties")
                        ?: throw GhidraExtensionArchiveException(
                            "Ghidra extension archive is missing extension.properties: " +
                                "$normalizedArchivePath"
                        )

                val manifestEntry =
                    zipFile.getEntry("$rootDirectoryName/Module.manifest")
                        ?: throw GhidraExtensionArchiveException(
                            "Ghidra extension archive is missing Module.manifest: " +
                                "$normalizedArchivePath"
                        )

                if (propertiesEntry.isDirectory || manifestEntry.isDirectory) {
                    throw GhidraExtensionArchiveException(
                        "Ghidra extension archive has invalid required files: " +
                            "$normalizedArchivePath"
                    )
                }

                val properties = Properties()
                zipFile.getInputStream(propertiesEntry).use(properties::load)

                val extensionName = properties.requiredProperty(
                    "name",
                    normalizedArchivePath,
                )
                val ghidraVersion = properties.requiredProperty(
                    "version",
                    normalizedArchivePath,
                )

                return GhidraExtensionArchive(
                    archivePath = normalizedArchivePath,
                    rootDirectoryName = rootDirectoryName,
                    extensionName = extensionName,
                    ghidraVersion = ghidraVersion,
                )
            }
        }
        catch (e: GhidraExtensionArchiveException) {
            throw e
        }
        catch (e: IOException) {
            throw GhidraExtensionArchiveException(
                "Cannot read Ghidra extension archive: $normalizedArchivePath",
                e,
            )
        }
    }

    private fun validateEntryName(entryName: String, archivePath: Path) {
        if (
            entryName.isBlank() ||
            entryName.startsWith("/") ||
            entryName.startsWith("\\") ||
            '\\' in entryName
        ) {
            throw GhidraExtensionArchiveException(
                "Ghidra extension archive contains an unsafe entry '$entryName': $archivePath"
            )
        }

        val pathComponents = entryName
            .removeSuffix("/")
            .split('/')

        if (pathComponents.any { it.isBlank() || it == "." || it == ".." }) {
            throw GhidraExtensionArchiveException(
                "Ghidra extension archive contains an unsafe entry '$entryName': $archivePath"
            )
        }
    }

    private fun Properties.requiredProperty(
        key: String,
        archivePath: Path,
    ): String =
        getProperty(key)
            ?.trim()
            ?.takeIf(String::isNotEmpty)
            ?: throw GhidraExtensionArchiveException(
                "Ghidra extension archive is missing required property '$key': $archivePath"
            )
}