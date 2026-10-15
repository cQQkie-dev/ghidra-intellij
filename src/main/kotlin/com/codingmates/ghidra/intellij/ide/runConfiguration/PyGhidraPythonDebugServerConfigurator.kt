package com.codingmates.ghidra.intellij.ide.runConfiguration

import com.intellij.execution.RunManager
import com.intellij.execution.configurations.ConfigurationTypeUtil
import com.intellij.openapi.project.Project
import org.jdom.Element

data class PyGhidraPythonDebugServerResult(
    val name: String,
    val created: Boolean,
)

object PyGhidraPythonDebugServerConfigurator {

    const val CONFIGURATION_TYPE_ID = "PyRemoteDebugConfigurationType"
    const val HOST = "localhost"
    const val PORT = "5678"

    fun ensure(project: Project): PyGhidraPythonDebugServerResult {
        val runManager = RunManager.getInstance(project)
        val configurationName = "${project.name} PyGhidra Python Debug Server"

        val existingConfiguration = runManager.allSettings.firstOrNull { settings ->
            settings.type.id == CONFIGURATION_TYPE_ID &&
                settings.name == configurationName
        }

        if (existingConfiguration != null) {
            runManager.selectedConfiguration = existingConfiguration

            return PyGhidraPythonDebugServerResult(
                name = existingConfiguration.name,
                created = false,
            )
        }

        val configurationType = ConfigurationTypeUtil.findConfigurationType(
            CONFIGURATION_TYPE_ID,
        ) ?: error(
            "IntelliJ Python Debug Server support is unavailable. " +
                "Install and enable the Python and Python DAP Debugger plug-ins."
        )

        val factory = configurationType.configurationFactories
            .singleOrNull()
            ?: error(
                "IntelliJ Python Debug Server configuration factory is unavailable."
            )

        val settings = runManager.createConfiguration(
            configurationName,
            factory,
        )

        settings.configuration.readExternal(
            pythonDebugServerConfigurationElement()
        )

        runManager.addConfiguration(settings)
        runManager.selectedConfiguration = settings

        return PyGhidraPythonDebugServerResult(
            name = settings.name,
            created = true,
        )
    }

    internal fun pythonDebugServerConfigurationElement(): Element =
        Element("configuration").apply {
            setAttribute("type", CONFIGURATION_TYPE_ID)

            addOption("PORT", PORT)
            addOption("HOST", HOST)

            addContent(
                Element("PathMappingSettings").apply {
                    addContent(
                        Element("option")
                            .setAttribute("name", "pathMappings")
                            .addContent(Element("list")),
                    )
                },
            )

            addOption("REDIRECT_OUTPUT", "true")
            addOption("SUSPEND_AFTER_CONNECT", "true")
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