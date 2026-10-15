package com.codingmates.ghidra.intellij.ide.runConfiguration

import com.codingmates.ghidra.intellij.ide.project.GhidraProjectKind
import com.codingmates.ghidra.intellij.ide.project.GhidraProjectLayoutDiscovery
import com.intellij.execution.RunManager
import com.intellij.execution.configurations.ConfigurationTypeUtil
import com.intellij.openapi.project.Project
import java.nio.file.Path

data class PyGhidraGuiLauncherResult(
    val name: String,
    val created: Boolean,
)

object PyGhidraGuiLauncherConfigurator {

    private const val CONFIGURATION_TYPE_ID = "PyGhidraGuiConfiguration"

    fun ensure(project: Project): PyGhidraGuiLauncherResult {
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

        val runManager = RunManager.getInstance(project)
        val configurationName = "${project.name} PyGhidra"

        val existingConfiguration = runManager.allSettings.firstOrNull { settings ->
            settings.type.id == CONFIGURATION_TYPE_ID &&
                settings.name == configurationName
        }

        if (existingConfiguration != null) {
            runManager.selectedConfiguration = existingConfiguration

            return PyGhidraGuiLauncherResult(
                name = existingConfiguration.name,
                created = false,
            )
        }

        val configurationType = ConfigurationTypeUtil.findConfigurationType(
            PyGhidraGuiConfigurationType::class.java,
        )

        val factory = configurationType.configurationFactories.single()

        val settings = runManager.createConfiguration(
            configurationName,
            factory,
        )

        val configuration =
            settings.configuration as PyGhidraGuiConfiguration

        configuration.setWorkingDirectory(projectRoot.toString())
        configuration.setLoadCurrentExtensionProject(
            projectKind == GhidraProjectKind.EXTENSION
        )

        runManager.addConfiguration(settings)
        runManager.selectedConfiguration = settings

        return PyGhidraGuiLauncherResult(
            name = settings.name,
            created = true,
        )
    }
}