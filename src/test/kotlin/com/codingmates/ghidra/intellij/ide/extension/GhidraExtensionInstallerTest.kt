package com.codingmates.ghidra.intellij.ide.extension

import com.codingmates.ghidra.intellij.ide.installation.GhidraInstallation
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
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class GhidraExtensionInstallerTest {

    @Test
    fun `installs matching extension archive`() {
        val archive = createArchive(
            rootDirectoryName = "ExampleExtension",
            ghidraVersion = "12.1.4",
        )
        val extensionsDirectory = Files.createTempDirectory("ghidra-extensions")

        val installed = GhidraExtensionInstaller.install(
            archivePath = archive,
            ghidraInstallation = installation("12.1.4"),
            extensionsDirectory = extensionsDirectory,
        )

        assertEquals(
            extensionsDirectory.resolve("ExampleExtension"),
            installed.installationDirectory,
        )
        assertTrue(
            installed.installationDirectory
                .resolve("extension.properties")
                .exists()
        )
        assertEquals(
            "example content",
            installed.installationDirectory.resolve("data/example.txt").readText(),
        )
    }

    @Test
    fun `replaces installed extension when explicitly requested`() {
        val archive = createArchive(
            rootDirectoryName = "ExampleExtension",
            ghidraVersion = "12.1.4",
            exampleContent = "new content",
        )
        val extensionsDirectory = Files.createTempDirectory("ghidra-extensions")
        val existingDirectory = extensionsDirectory
            .resolve("ExampleExtension")

        existingDirectory.createDirectories()
        existingDirectory.resolve("extension.properties").writeText(
            "name=Old Extension\nversion=12.1.4\n"
        )
        existingDirectory.resolve("old-content.txt").writeText("old content")

        val installed = GhidraExtensionInstaller.install(
            archivePath = archive,
            ghidraInstallation = installation("12.1.4"),
            extensionsDirectory = extensionsDirectory,
            replaceExisting = true,
        )

        assertEquals(existingDirectory, installed.installationDirectory)
        assertEquals(
            "new content",
            installed.installationDirectory.resolve("data/example.txt").readText(),
        )
        assertFalse(
            installed.installationDirectory.resolve("old-content.txt").exists()
        )
    }

    @Test
    fun `rejects archive for a different Ghidra version`() {
        val archive = createArchive(
            rootDirectoryName = "ExampleExtension",
            ghidraVersion = "12.0.3",
        )

        val exception = assertFailsWith<GhidraExtensionInstallationException> {
            GhidraExtensionInstaller.install(
                archivePath = archive,
                ghidraInstallation = installation("12.1.4"),
                extensionsDirectory = Files.createTempDirectory("ghidra-extensions"),
            )
        }

        assertTrue(exception.message!!.contains("12.0.3"))
        assertTrue(exception.message!!.contains("12.1.4"))
    }

    @Test
    fun `rejects replacement of installed extension`() {
        val archive = createArchive(
            rootDirectoryName = "ExampleExtension",
            ghidraVersion = "12.1.4",
        )
        val extensionsDirectory = Files.createTempDirectory("ghidra-extensions")
        extensionsDirectory.resolve("ExampleExtension").createDirectories()

        assertFailsWith<GhidraExtensionInstallationException> {
            GhidraExtensionInstaller.install(
                archivePath = archive,
                ghidraInstallation = installation("12.1.4"),
                extensionsDirectory = extensionsDirectory,
            )
        }
    }

    private fun installation(version: String): GhidraInstallation {
        val installationPath = Files.createTempDirectory("ghidra-installation")

        return GhidraInstallation(
            configuredPath = installationPath,
            canonicalPath = installationPath,
            version = version,
            releaseName = "PUBLIC",
            javaCompilerVersion = 21,
            javaMinimumVersion = 21,
            javaMaximumVersion = null,
            gradleMinimumVersion = "8.5",
            gradleMaximumVersion = null,
        )
    }

    private fun createArchive(
        rootDirectoryName: String,
        ghidraVersion: String,
        exampleContent: String = "example content",
    ): Path {
        val archive = Files.createTempFile("ghidra-extension", ".zip")

        ZipOutputStream(archive.outputStream()).use { zip ->
            writeEntry(
                zip,
                "$rootDirectoryName/extension.properties",
                """
                name=Example Extension
                version=$ghidraVersion
                """.trimIndent(),
            )
            writeEntry(zip, "$rootDirectoryName/Module.manifest", "")
            writeEntry(zip, "$rootDirectoryName/data/example.txt", exampleContent)
        }

        return archive
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