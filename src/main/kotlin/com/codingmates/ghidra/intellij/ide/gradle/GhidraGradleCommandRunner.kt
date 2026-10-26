package com.codingmates.ghidra.intellij.ide.gradle

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.CapturingProcessHandler
import com.intellij.execution.process.ProcessOutput
import java.io.File
import java.nio.file.Path

object GhidraGradleCommandRunner {

    private const val GHIDRA_INSTALL_DIR = "GHIDRA_INSTALL_DIR"

    fun run(
        command: GhidraGradleCommand,
        javaHome: Path? = null,
    ): ProcessOutput =
        CapturingProcessHandler(
            createCommandLine(
                command = command,
                javaHome = javaHome,
            )
        ).runProcess()

    internal fun sanitizedEnvironment(
        parentEnvironment: Map<String, String>,
        javaHome: Path? = null,
    ): Map<String, String> {
        val environment = parentEnvironment
            .filterKeys { key -> key != GHIDRA_INSTALL_DIR }
            .toMutableMap()

        if (javaHome == null) {
            return environment
        }

        val normalizedJavaHome = javaHome.toAbsolutePath().normalize()
        val javaBinDirectory = normalizedJavaHome.resolve("bin")

        val pathKey = environment.keys
            .firstOrNull { key ->
                key.equals("PATH", ignoreCase = true)
            }
            ?: "PATH"

        val existingPath = environment[pathKey]

        environment["JAVA_HOME"] = normalizedJavaHome.toString()
        environment[pathKey] = listOfNotNull(
            javaBinDirectory.toString(),
            existingPath,
        ).joinToString(File.pathSeparator)

        return environment
    }

    private fun createCommandLine(
        command: GhidraGradleCommand,
        javaHome: Path?,
    ): GeneralCommandLine {
        val commandLine = if (
            System.getProperty("os.name").startsWith("Windows", true)
        ) {
            GeneralCommandLine("cmd.exe", "/c", command.executable)
        }
        else {
            GeneralCommandLine(command.executable)
        }

        return commandLine
            .withParameters(command.arguments)
            .withWorkDirectory(command.workingDirectory.toFile())
            .withParentEnvironmentType(
                GeneralCommandLine.ParentEnvironmentType.NONE
            )
            .withEnvironment(
                sanitizedEnvironment(
                    parentEnvironment = System.getenv(),
                    javaHome = javaHome,
                )
            )
    }
}