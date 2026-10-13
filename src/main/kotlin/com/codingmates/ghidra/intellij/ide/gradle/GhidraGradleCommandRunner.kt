package com.codingmates.ghidra.intellij.ide.gradle

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.CapturingProcessHandler
import com.intellij.execution.process.ProcessOutput

object GhidraGradleCommandRunner {

    fun run(command: GhidraGradleCommand): ProcessOutput =
        CapturingProcessHandler(createCommandLine(command)).runProcess()

    private fun createCommandLine(command: GhidraGradleCommand): GeneralCommandLine {
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
    }
}