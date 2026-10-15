package com.codingmates.ghidra.intellij.ide.runConfiguration

import com.codingmates.ghidra.intellij.ide.installation.GhidraPlatform
import kotlin.test.Test
import kotlin.test.assertEquals
import java.nio.file.Path

class PyGhidraGuiCommandBuilderTest {

    @Test
    fun `builds PyGhidra GUI command for script project`() {
        val command = PyGhidraGuiCommandBuilder.gui(
            ghidraInstallationPath = Path.of("/opt/ghidra"),
            workingDirectory = Path.of("/work/scripts"),
            extensionProjectRoot = null,
            platform = GhidraPlatform.LINUX,
        )

        assertEquals(
            Path.of("/opt/ghidra/support/pyghidraRun"),
            command.executable,
        )
        assertEquals(
            listOf("--console"),
            command.arguments,
        )
        assertEquals(
            Path.of("/work/scripts"),
            command.workingDirectory,
        )
    }

    @Test
    fun `builds PyGhidra GUI command for Gradle extension project`() {
        val command = PyGhidraGuiCommandBuilder.gui(
            ghidraInstallationPath = Path.of("/opt/ghidra"),
            workingDirectory = Path.of("/work/ExampleExtension"),
            extensionProjectRoot = Path.of("/work/ExampleExtension"),
            platform = GhidraPlatform.LINUX,
        )

        assertEquals(
            listOf(
                "--console",
                "-D",
                "binaryPath=build/classes/java/main:build/resources/main",
                "-D",
                "ghidra.external.modules=/work/ExampleExtension",
            ),
            command.arguments,
        )
    }
}