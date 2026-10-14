package com.codingmates.ghidra.intellij.ide.extension

import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.outputStream
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GhidraExtensionSourceImporterTest {

    @Test
    fun `inspects existing extension source project`() {
        val projectDirectory = createExtensionProject()

        val sourceProject =
            GhidraExtensionSourceImporter.inspect(projectDirectory)

        assertEquals(
            projectDirectory.toAbsolutePath().normalize(),
            sourceProject.projectDirectory,
        )
        assertNull(sourceProject.configuredInstallationPath)
        assertTrue(sourceProject.detectedGhidraVersions.isEmpty())
    }

    @Test
    fun `detects historical Ghidra versions from extension archives`() {
        val projectDirectory = createExtensionProject()
        val distributionDirectory = projectDirectory.resolve("dist")
        distributionDirectory.createDirectories()

        createArchive(
            distributionDirectory.resolve("old.zip"),
            ghidraVersion = "12.0.4",
        )
        createArchive(
            distributionDirectory.resolve("newer.zip"),
            ghidraVersion = "12.1",
        )

        val sourceProject =
            GhidraExtensionSourceImporter.inspect(projectDirectory)

        assertEquals(
            setOf("12.0.4", "12.1"),
            sourceProject.historicalGhidraVersions,
        )
        assertTrue(
            sourceProject.migrationWarning("12.1.4")
                ?.contains("12.0.4, 12.1") == true
        )
        assertNull(sourceProject.migrationWarning("12.1"))
    }

    @Test
    fun `writes local Ghidra target and preserves existing Gradle properties`() {
        val projectDirectory = createExtensionProject()

        projectDirectory.resolve("gradle.properties").writeText(
            "# Existing local setting\n" +
                "custom.property=value\n" +
                "GHIDRA_INSTALL_DIR=/old/ghidra\n"
        )
        projectDirectory.resolve(".gitignore").writeText(
            "build/\n"
        )

        val propertiesFile =
            GhidraExtensionSourceImporter.configureLocalInstallation(
                projectDirectory = projectDirectory,
                ghidraInstallationPath = Path.of("/new/ghidra"),
            )

        assertEquals(
            """
            # Existing local setting
            custom.property=value
            GHIDRA_INSTALL_DIR=/new/ghidra
            """.trimIndent() + "\n",
            propertiesFile.readText(),
        )
        assertTrue(
            projectDirectory.resolve(".gitignore")
                .readText()
                .contains("gradle.properties")
        )
    }

    @Test
    fun `creates ignored local Gradle properties when absent`() {
        val projectDirectory = createExtensionProject()

        val propertiesFile =
            GhidraExtensionSourceImporter.configureLocalInstallation(
                projectDirectory = projectDirectory,
                ghidraInstallationPath = Path.of("/example/ghidra"),
            )

        assertTrue(propertiesFile.exists())
        assertEquals(
            "GHIDRA_INSTALL_DIR=/example/ghidra\n",
            propertiesFile.readText(),
        )
        assertTrue(
            projectDirectory.resolve(".gitignore")
                .readText()
                .contains("gradle.properties")
        )
    }

    @Test
    fun `rejects non extension project`() {
        val projectDirectory = Files.createTempDirectory("plain-project")
        projectDirectory.resolve("build.gradle").writeText("// plain Gradle")

        assertFailsWith<GhidraExtensionSourceImportException> {
            GhidraExtensionSourceImporter.inspect(projectDirectory)
        }
    }

    private fun createExtensionProject(): Path {
        val projectDirectory =
            Files.createTempDirectory("ghidra-extension-source")

        projectDirectory.resolve("build.gradle").writeText(
            """
            apply from: new File(ghidraInstallDir).getCanonicalPath() +
                "/support/buildExtension.gradle"
            """.trimIndent() + "\n"
        )
        projectDirectory.resolve("extension.properties").writeText(
            "name=Example\nversion=@extversion@\n"
        )
        projectDirectory.resolve("Module.manifest").writeText("")

        return projectDirectory
    }

    private fun createArchive(
        archivePath: Path,
        ghidraVersion: String,
    ) {
        ZipOutputStream(archivePath.outputStream()).use { zip ->
            writeEntry(
                zip,
                "ExampleExtension/extension.properties",
                "name=Example\nversion=$ghidraVersion\n",
            )
            writeEntry(
                zip,
                "ExampleExtension/Module.manifest",
                "",
            )
        }
    }

    private fun writeEntry(
        zip: ZipOutputStream,
        name: String,
        content: String,
    ) {
        zip.putNextEntry(ZipEntry(name))
        zip.write(content.encodeToByteArray())
        zip.closeEntry()
    }
}