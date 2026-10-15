package com.codingmates.ghidra.intellij.ide.runConfiguration

import com.codingmates.ghidra.intellij.ide.project.GhidraProjectKind
import com.codingmates.ghidra.intellij.ide.project.GhidraProjectLayoutDiscovery
import com.intellij.execution.RunManager
import com.intellij.openapi.project.Project
import java.nio.file.Path

data class PyGhidraPythonDebugPreparationResult(
    val launcherName: String,
    val launcherCreated: Boolean,
    val debugServerName: String,
    val debugServerCreated: Boolean,
    val pydevHelperDirectory: Path,
)

object PyGhidraPythonDebugConfigurator {

    fun ensure(project: Project): PyGhidraPythonDebugPreparationResult {
        val projectRoot = project.basePath
            ?.let(Path::of)
            ?: error("The project does not have a base directory.")

        val projectKind = GhidraProjectLayoutDiscovery
            .discover(projectRoot)
            .kind

        require(
            projectKind == GhidraProjectKind.EXTENSION ||
                projectKind == GhidraProjectKind.SCRIPT
        ) {
            "The current project is not a recognized Ghidra Script or Extension project."
        }

        val helperDirectory =
            PyGhidraPythonDebugSupport.pydevHelperDirectory()

        val launcher = PyGhidraGuiLauncherConfigurator.ensure(project)

        val runManager = RunManager.getInstance(project)

        val launcherSettings = runManager.allSettings.firstOrNull { settings ->
            settings.type.id == "PyGhidraGuiConfiguration" &&
                settings.name == launcher.name
        } ?: error(
            "Failed to locate the PyGhidra GUI launcher '${launcher.name}'."
        )

        val launcherConfiguration =
            launcherSettings.configuration as? PyGhidraGuiConfiguration
                ?: error(
                    "The PyGhidra GUI launcher '${launcher.name}' has an unexpected type."
                )

        launcherConfiguration.setEnvs(
            PyGhidraPythonDebugSupport.environmentWithPydevHelper(
                environment = launcherConfiguration.getEnvs(),
                helperDirectory = helperDirectory,
            )
        )

        val debugServer =
            PyGhidraPythonDebugServerConfigurator.ensure(project)

        return PyGhidraPythonDebugPreparationResult(
            launcherName = launcher.name,
            launcherCreated = launcher.created,
            debugServerName = debugServer.name,
            debugServerCreated = debugServer.created,
            pydevHelperDirectory = helperDirectory,
        )
    }
}