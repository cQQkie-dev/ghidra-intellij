package com.codingmates.ghidra.intellij.ide.extension

import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.io.path.outputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class GhidraExtensionArchiveInspectorTest {

    @Test
    fun `inspects valid Ghidra extension archive`() {
        val archive = createArchive(
            "ExampleExtension" to mapOf(
                "extension.properties" to """
                    name=Example Extension
                    version=12.1.4
                """.trimIndent(),
                "Module.manifest" to "",
                "lib/ExampleExtension.jar" to "not a real jar",
            )
        )

        val inspected = GhidraExtensionArchiveInspector.inspect(archive)

        assertEquals(archive.toAbsolutePath().normalize(), inspected.archivePath)
        assertEquals("ExampleExtension", inspected.rootDirectoryName)
        assertEquals("Example Extension", inspected.extensionName)
        assertEquals("12.1.4", inspected.ghidraVersion)
    }

    @Test
    fun `rejects archive with multiple top level directories`() {
        val archive = createArchive(
            "FirstExtension" to mapOf(
                "extension.properties" to "name=First\nversion=12.1.4",
                "Module.manifest" to "",
            ),
            "SecondExtension" to mapOf(
                "README.md" to "unexpected second root",
            ),
        )

        assertFailsWith<GhidraExtensionArchiveException> {
            GhidraExtensionArchiveInspector.inspect(archive)
        }
    }

    @Test
    fun `rejects archive missing required manifest`() {
        val archive = createArchive(
            "ExampleExtension" to mapOf(
                "extension.properties" to "name=Example\nversion=12.1.4",
            )
        )

        assertFailsWith<GhidraExtensionArchiveException> {
            GhidraExtensionArchiveInspector.inspect(archive)
        }
    }

    @Test
    fun `rejects archive with unsafe path traversal entry`() {
        val archive = Files.createTempFile("unsafe-extension", ".zip")

        ZipOutputStream(archive.outputStream()).use { zip ->
            writeEntry(
                zip,
                "ExampleExtension/extension.properties",
                "name=Example\nversion=12.1.4",
            )
            writeEntry(zip, "ExampleExtension/Module.manifest", "")
            writeEntry(zip, "ExampleExtension/../../outside.txt", "unsafe")
        }

        assertFailsWith<GhidraExtensionArchiveException> {
            GhidraExtensionArchiveInspector.inspect(archive)
        }
    }

    private fun createArchive(
        vararg roots: Pair<String, Map<String, String>>,
    ): Path {
        val archive = Files.createTempFile("ghidra-extension", ".zip")

        ZipOutputStream(archive.outputStream()).use { zip ->
            roots.forEach { (root, files) ->
                files.forEach { (relativePath, content) ->
                    writeEntry(zip, "$root/$relativePath", content)
                }
            }
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