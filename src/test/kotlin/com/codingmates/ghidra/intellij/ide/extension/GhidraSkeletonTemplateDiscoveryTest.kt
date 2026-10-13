package com.codingmates.ghidra.intellij.ide.extension

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class GhidraSkeletonTemplateDiscoveryTest {

    @Test
    fun `discovers valid Skeleton template`() {
        val skeletonDirectory = createSkeletonTemplate()

        val template = GhidraSkeletonTemplateDiscovery.discover(
            skeletonDirectory
        )

        assertEquals(
            skeletonDirectory.toRealPath(),
            template.configuredPath,
        )
        assertEquals(
            skeletonDirectory.toRealPath(),
            template.canonicalPath,
        )
        assertTrue(Files.isRegularFile(template.buildTemplateFile))
        assertTrue(Files.isRegularFile(template.extensionPropertiesFile))
        assertTrue(Files.isRegularFile(template.moduleManifestFile))
        assertTrue(Files.isRegularFile(template.gitIgnoreTemplateFile))
        assertTrue(Files.isDirectory(template.javaTemplateDirectory))
    }

    @Test
    fun `rejects Skeleton template missing external build file`() {
        val skeletonDirectory = createSkeletonTemplate()
        Files.delete(skeletonDirectory.resolve("buildTemplate.gradle"))

        val exception =
            assertFailsWith<GhidraSkeletonTemplateException> {
                GhidraSkeletonTemplateDiscovery.discover(skeletonDirectory)
            }

        assertTrue(exception.message!!.contains("buildTemplate.gradle"))
    }

    @Test
    fun `rejects missing Skeleton directory`() {
        val missingDirectory = Files.createTempDirectory("ghidra-skeleton")
            .resolve("Skeleton")

        assertFailsWith<GhidraSkeletonTemplateException> {
            GhidraSkeletonTemplateDiscovery.discover(missingDirectory)
        }
    }

    private fun createSkeletonTemplate(): Path {
        val skeletonDirectory =
            Files.createTempDirectory("ghidra-skeleton")

        skeletonDirectory
            .resolve("buildTemplate.gradle")
            .writeText("// external extension build template")

        skeletonDirectory
            .resolve("extension.properties")
            .writeText("name=@extname@\nversion=@extversion@\n")

        skeletonDirectory
            .resolve("Module.manifest")
            .writeText("")

        skeletonDirectory
            .resolve("gitignore")
            .writeText("build/\n")

        skeletonDirectory
            .resolve("src/main/java/skeleton")
            .createDirectories()

        return skeletonDirectory
    }
}