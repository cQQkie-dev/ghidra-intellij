package com.codingmates.ghidra.intellij.ide.extension

import java.nio.file.Files
import java.nio.file.attribute.FileTime
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class GhidraExtensionArtifactFinderTest {

    @Test
    fun `finds newest ZIP directly inside distribution directory`() {
        val projectRoot = Files.createTempDirectory("ghidra-extension")
        val distributionDirectory = projectRoot.resolve("dist").createDirectories()

        val olderArchive = distributionDirectory
            .resolve("ghidra_12.1.4_PUBLIC_20260101_Older.zip")
        olderArchive.writeText("older")
        Files.setLastModifiedTime(olderArchive, FileTime.fromMillis(1_000))

        val newestArchive = distributionDirectory
            .resolve("ghidra_12.1.4_PUBLIC_20260102_Newer.zip")
        newestArchive.writeText("newer")
        Files.setLastModifiedTime(newestArchive, FileTime.fromMillis(2_000))

        distributionDirectory.resolve("not-an-extension.txt").writeText("ignored")
        distributionDirectory.resolve("nested").createDirectories()
        distributionDirectory.resolve("nested/archive.zip").writeText("ignored")

        assertEquals(
            newestArchive,
            GhidraExtensionArtifactFinder.findNewest(projectRoot),
        )
    }

    @Test
    fun `returns null when distribution directory does not exist`() {
        val projectRoot = Files.createTempDirectory("ghidra-extension")

        assertNull(GhidraExtensionArtifactFinder.findNewest(projectRoot))
    }

    @Test
    fun `returns null when distribution directory has no ZIP files`() {
        val projectRoot = Files.createTempDirectory("ghidra-extension")
        val distributionDirectory = projectRoot.resolve("dist").createDirectories()
        distributionDirectory.resolve("README.txt").writeText("no archive")

        assertNull(GhidraExtensionArtifactFinder.findNewest(projectRoot))
    }
}