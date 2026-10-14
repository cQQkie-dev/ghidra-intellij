package com.codingmates.ghidra.intellij.ide.project

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class GhidraProjectInstallationDiscoveryTest {

    @Test
    fun `reads Ghidra installation from Gradle properties`() {
        val projectDirectory =
            Files.createTempDirectory("ghidra-project-installation")

        projectDirectory.resolve("gradle.properties").writeText(
            "GHIDRA_INSTALL_DIR=/example/ghidra\n"
        )

        assertEquals(
            Path.of("/example/ghidra"),
            GhidraProjectInstallationDiscovery.discover(projectDirectory),
        )
    }

    @Test
    fun `reads Ghidra installation from legacy plugin properties`() {
        val projectDirectory =
            Files.createTempDirectory("ghidra-project-installation")

        projectDirectory.resolve("ghidra-intellij.properties").writeText(
            "GHIDRA_INSTALL_DIR=/example/ghidra\n"
        )

        assertEquals(
            Path.of("/example/ghidra"),
            GhidraProjectInstallationDiscovery.discover(projectDirectory),
        )
    }

    @Test
    fun `prefers Gradle properties over legacy plugin properties`() {
        val projectDirectory =
            Files.createTempDirectory("ghidra-project-installation")

        projectDirectory.resolve("gradle.properties").writeText(
            "GHIDRA_INSTALL_DIR=/example/gradle-ghidra\n"
        )

        projectDirectory.resolve("ghidra-intellij.properties").writeText(
            "GHIDRA_INSTALL_DIR=/example/legacy-ghidra\n"
        )

        assertEquals(
            Path.of("/example/gradle-ghidra"),
            GhidraProjectInstallationDiscovery.discover(projectDirectory),
        )
    }

    @Test
    fun `reads shared script directory from Gradle properties`() {
        val projectDirectory =
            Files.createTempDirectory("ghidra-project-installation")

        projectDirectory.resolve("gradle.properties").writeText(
            "GHIDRA_INSTALL_DIR=/example/ghidra\n" +
                "GHIDRA_SCRIPT_DIR=/example/ghidra_scripts\n"
        )

        assertEquals(
            Path.of("/example/ghidra_scripts"),
            GhidraProjectInstallationDiscovery.discoverScriptDirectory(
                projectDirectory
            ),
        )
    }

    @Test
    fun `returns null when no local Ghidra installation is configured`() {
        val projectDirectory =
            Files.createTempDirectory("ghidra-project-installation")

        assertNull(
            GhidraProjectInstallationDiscovery.discover(projectDirectory)
        )
    }

    @Test
    fun `returns null when no shared script directory is configured`() {
        val projectDirectory =
            Files.createTempDirectory("ghidra-project-installation")

        projectDirectory.resolve("gradle.properties").writeText(
            "GHIDRA_INSTALL_DIR=/example/ghidra\n"
        )

        assertNull(
            GhidraProjectInstallationDiscovery.discoverScriptDirectory(
                projectDirectory
            )
        )
    }
}