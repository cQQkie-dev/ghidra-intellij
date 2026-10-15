package com.codingmates.ghidra.intellij.ide.runConfiguration

import com.codingmates.ghidra.intellij.ide.facet.GhidraFacet
import com.intellij.execution.ExecutionException
import com.intellij.execution.configurations.CommandLineState
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.KillableProcessHandler
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.runners.ExecutionEnvironment
import java.nio.file.Path

class PyGhidraGuiCommandLineState(
    environment: ExecutionEnvironment,
    private val configuration: PyGhidraGuiConfiguration,
) : CommandLineState(environment) {

    override fun startProcess(): ProcessHandler =
        KillableProcessHandler(createCommandLine())

    private fun createCommandLine(): GeneralCommandLine {
        configuration.checkConfiguration()

        val project = configuration.project

        val facet = GhidraFacet.findAnyInProject(project)
            ?: throw ExecutionException(
                "No Ghidra facet is configured for this project."
            )

        val workingDirectory = configuration.getWorkingDirectory()
            ?.takeIf(String::isNotBlank)
            ?.let(Path::of)
            ?: throw ExecutionException(
                "PyGhidra GUI requires a working directory."
            )

        val extensionProjectRoot =
            if (configuration.loadsCurrentExtensionProject()) {
                project.basePath
                    ?.let(Path::of)
                    ?.toAbsolutePath()
                    ?.normalize()
                    ?: throw ExecutionException(
                        "The project does not have a base directory."
                    )
            }
            else {
                null
            }

        val command = PyGhidraGuiCommandBuilder.gui(
            ghidraInstallationPath = Path.of(facet.installationPath),
            workingDirectory = workingDirectory,
            extensionProjectRoot = extensionProjectRoot,
        )

        val environment = LinkedHashMap(configuration.getEnvs())

        // The selected Ghidra facet is authoritative for this launcher.
        environment["GHIDRA_INSTALL_DIR"] = facet.installationPath

        return GeneralCommandLine(command.executable.toString())
            .withParameters(command.arguments)
            .withWorkingDirectory(command.workingDirectory)
            .withEnvironment(environment)
            .withParentEnvironmentType(
                if (configuration.isPassParentEnvs()) {
                    GeneralCommandLine.ParentEnvironmentType.CONSOLE
                }
                else {
                    GeneralCommandLine.ParentEnvironmentType.NONE
                }
            )
    }
}