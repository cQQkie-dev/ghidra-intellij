package com.codingmates.ghidra.intellij.ide.project

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GhidraProjectLocalTargetConfigurationTest {

    @Test
    fun `writes local Ghidra target and preserves unrelated Gradle properties`() {
        val projectRoot = Files.createTempDirectory("ghidra-local-target")

        projectRoot.resolve("gradle.properties").writeText(
            """
            # Existing local settings
            org.gradle.parallel=true
            GHIDRA_INSTALL_DIR=/old/ghidra
            custom.property=value
            """.trimIndent() + "\n"
        )

        val propertiesFile =
            GhidraProjectLocalTargetConfiguration.writeInstallation(
                projectRoot = projectRoot,
                ghidraInstallationPath = Path.of("/new/ghidra"),
            )

        assertEquals(
            projectRoot.resolve("gradle.properties"),
            propertiesFile,
        )

        assertEquals(
            """
            # Existing local settings
            org.gradle.parallel=true
            GHIDRA_INSTALL_DIR=/new/ghidra
            custom.property=value
            """.trimIndent() + "\n",
            propertiesFile.readText(),
        )

        assertTrue(projectRoot.resolve(".gitignore").exists())
        assertTrue(
            projectRoot.resolve(".gitignore")
                .readText()
                .contains("gradle.properties")
        )
    }

    @Test
    fun `creates local Gradle properties and Git ignore entry when absent`() {
        val projectRoot = Files.createTempDirectory("ghidra-local-target")

        val propertiesFile =
            GhidraProjectLocalTargetConfiguration.writeInstallation(
                projectRoot = projectRoot,
                ghidraInstallationPath = Path.of("/example/ghidra"),
            )

        assertEquals(
            "GHIDRA_INSTALL_DIR=/example/ghidra\n",
            propertiesFile.readText(),
        )

        assertEquals(
            """
            # Local Ghidra target configuration
            gradle.properties
            """.trimIndent() + "\n",
            projectRoot.resolve(".gitignore").readText(),
        )
    }

    @Test
    fun `does not duplicate existing Gradle properties Git ignore entry`() {
        val projectRoot = Files.createTempDirectory("ghidra-local-target")

        projectRoot.resolve(".gitignore").writeText(
            """
            build/
            /gradle.properties
            """.trimIndent() + "\n"
        )

        GhidraProjectLocalTargetConfiguration.writeInstallation(
            projectRoot = projectRoot,
            ghidraInstallationPath = Path.of("/example/ghidra"),
        )

        assertEquals(
            """
            build/
            /gradle.properties
            """.trimIndent() + "\n",
            projectRoot.resolve(".gitignore").readText(),
        )
    }
}