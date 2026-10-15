package com.codingmates.ghidra.intellij.ide.runConfiguration

import com.codingmates.ghidra.intellij.ide.project.GhidraProjectKind
import com.codingmates.ghidra.intellij.ide.project.GhidraProjectLayoutDiscovery
import com.intellij.execution.RunManager
import com.intellij.execution.configurations.ConfigurationTypeUtil
import com.intellij.openapi.project.Project
import org.jdom.Element
import java.nio.file.Path

data class PyGhidraJavaDebugAttachResult(
    val name: String,
    val created: Boolean,
)

/**
 * Creates or selects a built-in IntelliJ Remote JVM Debug configuration for the JDWP listener
 * started by PyGhidra when PYGHIDRA_DEBUG=1 is set.
 */
object PyGhidraJavaDebugAttachConfigurator {

    private const val REMOTE_CONFIGURATION_TYPE_ID = "Remote"
    private const val JDWP_HOST = "127.0.0.1"
    private const val JDWP_PORT = "18001"

    fun ensure(project: Project): PyGhidraJavaDebugAttachResult {
        val projectRoot = project.basePath
            ?.let(Path::of)
            ?: error("The project does not have a base directory.")

        require(
            GhidraProjectLayoutDiscovery.discover(projectRoot).kind ==
                GhidraProjectKind.EXTENSION
        ) {
            "The current project is not a recognized Ghidra extension project."
        }

        val runManager = RunManager.getInstance(project)
        val configurationName = "${project.name} PyGhidra Attach"

        val existingConfiguration = runManager.allSettings.firstOrNull { settings ->
            settings.type.id == REMOTE_CONFIGURATION_TYPE_ID &&
                settings.name == configurationName
        }

        if (existingConfiguration != null) {
            runManager.selectedConfiguration = existingConfiguration

            return PyGhidraJavaDebugAttachResult(
                name = existingConfiguration.name,
                created = false,
            )
        }

        val configurationType = ConfigurationTypeUtil.findConfigurationType(
            REMOTE_CONFIGURATION_TYPE_ID,
        ) ?: error(
            "IntelliJ Remote JVM Debug configuration type is unavailable."
        )

        val factory = configurationType.configurationFactories
            .singleOrNull()
            ?: error(
                "IntelliJ Remote JVM Debug configuration factory is unavailable."
            )

        val settings = runManager.createConfiguration(
            configurationName,
            factory,
        )

        settings.configuration.readExternal(remoteAttachConfigurationElement())

        runManager.addConfiguration(settings)
        runManager.selectedConfiguration = settings

        return PyGhidraJavaDebugAttachResult(
            name = settings.name,
            created = true,
        )
    }

    internal fun remoteAttachConfigurationElement(): Element =
        Element("configuration").apply {
            setAttribute("type", REMOTE_CONFIGURATION_TYPE_ID)

            addOption("USE_SOCKET_TRANSPORT", "true")
            addOption("SERVER_MODE", "false")
            addOption("SHMEM_ADDRESS", "")
            addOption("HOST", JDWP_HOST)
            addOption("PORT", JDWP_PORT)
            addOption("AUTO_RESTART", "false")
        }

    private fun Element.addOption(
        name: String,
        value: String,
    ) {
        addContent(
            Element("option")
                .setAttribute("name", name)
                .setAttribute("value", value),
        )
    }
}