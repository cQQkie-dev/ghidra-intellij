package com.codingmates.ghidra.intellij.ide.installation

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class GhidraInstallationDiscoveryTest {

    @Test
    fun `discovers release installation metadata`() {
        val installation = createInstallation(
            """
            application.version=12.1.4
            application.release.name=PUBLIC
            application.java.compiler=21
            application.java.min=21
            application.java.max=
            application.gradle.min=8.5
            application.gradle.max=
            """.trimIndent()
        )

        val discovered = GhidraInstallationDiscovery.discover(installation)

        assertEquals(installation.toRealPath(), discovered.configuredPath)
        assertEquals(installation.toRealPath(), discovered.canonicalPath)
        assertEquals("12.1.4", discovered.version)
        assertEquals("PUBLIC", discovered.releaseName)
        assertEquals(21, discovered.javaCompilerVersion)
        assertEquals(21, discovered.javaMinimumVersion)
        assertEquals(null, discovered.javaMaximumVersion)
        assertEquals("8.5", discovered.gradleMinimumVersion)
        assertEquals(null, discovered.gradleMaximumVersion)
        assertEquals(
            installation.resolve("support/buildExtension.gradle"),
            discovered.buildExtensionScript
        )
        assertEquals(
            "ghidra_12.1.4_PUBLIC",
            discovered.userSettingsDirectoryName
        )
    }

    @Test
    fun `rejects directory without application properties`() {
        val directory = Files.createTempDirectory("not-ghidra")

        val exception = assertFailsWith<GhidraInstallationException> {
            GhidraInstallationDiscovery.discover(directory)
        }

        assertTrue(exception.message!!.contains("application.properties"))
    }

    @Test
    fun `rejects missing required metadata`() {
        val installation = createInstallation(
            """
            application.release.name=PUBLIC
            """.trimIndent()
        )

        val exception = assertFailsWith<GhidraInstallationException> {
            GhidraInstallationDiscovery.discover(installation)
        }

        assertTrue(exception.message!!.contains("application.version"))
    }

    private fun createInstallation(applicationProperties: String): Path {
        val installation = Files.createTempDirectory("ghidra-installation")
        val ghidraDirectory = installation.resolve("Ghidra").createDirectories()
        ghidraDirectory.resolve("application.properties").writeText(applicationProperties)
        return installation
    }
}
