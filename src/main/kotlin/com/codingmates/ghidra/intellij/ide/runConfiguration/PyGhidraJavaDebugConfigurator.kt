package com.codingmates.ghidra.intellij.ide.runConfiguration

import com.intellij.execution.RunManager
import com.intellij.openapi.project.Project

data class PyGhidraJavaDebugPreparationResult(
    val launcherName: String,
    val launcherCreated: Boolean,
    val attachName: String,
    val attachCreated: Boolean,
    val debugEnvironmentWasEnabled: Boolean,
)

/**
 * Prepares the paired PyGhidra GUI and Remote JVM Debug configurations required to debug
 * Java extension code running in PyGhidra.
 */
object PyGhidraJavaDebugConfigurator {

    const val PYGHIDRA_DEBUG_ENVIRONMENT_VARIABLE = "PYGHIDRA_DEBUG"

    fun ensure(project: Project): PyGhidraJavaDebugPreparationResult {
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

        val currentEnvironment = launcherConfiguration.getEnvs()
        val debugEnvironmentWasEnabled =
            currentEnvironment[PYGHIDRA_DEBUG_ENVIRONMENT_VARIABLE] == "1"

        if (!debugEnvironmentWasEnabled) {
            launcherConfiguration.setEnvs(
                environmentWithDebugEnabled(currentEnvironment)
            )
        }

        val attach = PyGhidraJavaDebugAttachConfigurator.ensure(project)

        return PyGhidraJavaDebugPreparationResult(
            launcherName = launcher.name,
            launcherCreated = launcher.created,
            attachName = attach.name,
            attachCreated = attach.created,
            debugEnvironmentWasEnabled = debugEnvironmentWasEnabled,
        )
    }

    internal fun environmentWithDebugEnabled(
        environment: Map<String, String>,
    ): Map<String, String> =
        LinkedHashMap(environment).apply {
            put(PYGHIDRA_DEBUG_ENVIRONMENT_VARIABLE, "1")
        }
}