package com.codingmates.ghidra.intellij.ide.runConfiguration

import com.intellij.execution.RunManager
import com.intellij.execution.configurations.ConfigurationTypeUtil
import com.intellij.openapi.project.Project
import java.nio.file.Path

data class GhidraDevelopmentLauncherResult(
    val name: String,
    val created: Boolean,
)

/**
 * Creates or selects the standard GUI Ghidra development launcher for a project.
 */
object GhidraDevelopmentLauncherConfigurator {

    private const val CONFIGURATION_TYPE_ID = "GhidraLauncherConfiguration"

    fun ensure(project: Project): GhidraDevelopmentLauncherResult {
        val projectRoot = project.basePath
            ?.let(Path::of)
            ?: error("The project does not have a base directory.")

        val runManager = RunManager.getInstance(project)
        val configurationName = "${project.name} Ghidra"

        val existingConfiguration = runManager.allSettings.firstOrNull { settings ->
            settings.type.id == CONFIGURATION_TYPE_ID &&
                settings.name == configurationName
        }

        if (existingConfiguration != null) {
            runManager.selectedConfiguration = existingConfiguration

            return GhidraDevelopmentLauncherResult(
                name = existingConfiguration.name,
                created = false,
            )
        }

        val configurationType = ConfigurationTypeUtil.findConfigurationType(
            GhidraLauncherConfigurationType::class.java,
        )

        val factory = configurationType.configurationFactories.single()

        val settings = runManager.createConfiguration(
            configurationName,
            factory,
        )

        val configuration =
            settings.configuration as GhidraLauncherConfiguration

        configuration.setAlternativeJrePath(null)
        configuration.setAlternativeJrePathEnabled(false)
        configuration.setWorkingDirectory(projectRoot.toString())
        configuration.setHeadless(false)
        configuration.setArgs("")

        runManager.addConfiguration(settings)
        runManager.selectedConfiguration = settings

        return GhidraDevelopmentLauncherResult(
            name = settings.name,
            created = true,
        )
    }
}