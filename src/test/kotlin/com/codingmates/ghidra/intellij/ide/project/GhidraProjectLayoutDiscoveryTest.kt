package com.codingmates.ghidra.intellij.ide.project

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GhidraProjectLayoutDiscoveryTest {

    @Test
    fun `recognizes Gradle extension project`() {
        val projectRoot = Files.createTempDirectory("ghidra-extension")
        projectRoot.resolve("build.gradle").writeText(
            """
            apply from: new File(ghidraInstallDir).getCanonicalPath() +
                "/support/buildExtension.gradle"
            """.trimIndent()
        )
        projectRoot.resolve("extension.properties").writeText("name=Example")
        projectRoot.resolve("Module.manifest").writeText("")

        val layout = GhidraProjectLayoutDiscovery.discover(projectRoot)

        assertEquals(GhidraProjectKind.EXTENSION, layout.kind)
        assertEquals(projectRoot.resolve("build.gradle"), layout.buildFile)
        assertTrue(layout.isGradleProject)
        assertTrue(layout.usesGhidraExtensionBuild)
    }

    @Test
    fun `recognizes Gradle script project`() {
        val projectRoot = Files.createTempDirectory("ghidra-scripts")
        projectRoot.resolve("build.gradle").writeText(
            """
            apply from: new File(ghidraInstallDir).getCanonicalPath() +
                "/support/buildExtension.gradle"
            """.trimIndent()
        )
        projectRoot.resolve("ghidra_scripts").createDirectories()

        val layout = GhidraProjectLayoutDiscovery.discover(projectRoot)

        assertEquals(GhidraProjectKind.SCRIPT, layout.kind)
        assertTrue(layout.isGradleProject)
        assertTrue(layout.usesGhidraExtensionBuild)
    }

    @Test
    fun `recognizes generated shared-root Gradle script project`() {
        val projectRoot = Files.createTempDirectory("ghidra-shared-scripts")

        projectRoot.resolve("build.gradle").writeText(
            """
            // Ghidra Script Project
            apply from: new File(ghidraInstallDir).getCanonicalPath() +
                "/support/buildExtension.gradle"

            def scriptDirectory =
                providers.gradleProperty("GHIDRA_SCRIPT_DIR").orNull ?:
                "ghidra_scripts"
            """.trimIndent()
        )

        projectRoot.resolve("gradle.properties").writeText(
            "GHIDRA_SCRIPT_DIR=/example/ghidra_scripts\n"
        )

        val layout = GhidraProjectLayoutDiscovery.discover(projectRoot)

        assertEquals(GhidraProjectKind.SCRIPT, layout.kind)
        assertTrue(layout.isGradleProject)
        assertTrue(layout.usesGhidraExtensionBuild)
        assertFalse(projectRoot.resolve("ghidra_scripts").toFile().exists())
    }

    @Test
    fun `extension project takes precedence over script directory`() {
        val projectRoot = Files.createTempDirectory("ghidra-extension-scripts")
        projectRoot.resolve("build.gradle").writeText("// no Ghidra build integration")
        projectRoot.resolve("extension.properties").writeText("name=Example")
        projectRoot.resolve("Module.manifest").writeText("")
        projectRoot.resolve("ghidra_scripts").createDirectories()

        val layout = GhidraProjectLayoutDiscovery.discover(projectRoot)

        assertEquals(GhidraProjectKind.EXTENSION, layout.kind)
        assertFalse(layout.usesGhidraExtensionBuild)
    }

    @Test
    fun `does not recognize non Gradle script directory as project`() {
        val projectRoot = Files.createTempDirectory("plain-scripts")
        projectRoot.resolve("ghidra_scripts").createDirectories()

        val layout = GhidraProjectLayoutDiscovery.discover(projectRoot)

        assertEquals(GhidraProjectKind.UNKNOWN, layout.kind)
        assertEquals(null, layout.buildFile)
        assertFalse(layout.isGradleProject)
        assertFalse(layout.usesGhidraExtensionBuild)
    }
}