package com.codingmates.ghidra.intellij.ide.runConfiguration

import kotlin.test.Test
import kotlin.test.assertEquals

class PyGhidraPythonDebugServerConfiguratorTest {

    @Test
    fun `creates a localhost Python debug server configuration`() {
        val configuration =
            PyGhidraPythonDebugServerConfigurator
                .pythonDebugServerConfigurationElement()

        val options = configuration.getChildren("option")
            .associate {
                it.getAttributeValue("name") to it.getAttributeValue("value")
            }

        assertEquals(
            "PyRemoteDebugConfigurationType",
            configuration.getAttributeValue("type"),
        )
        assertEquals("5678", options["PORT"])
        assertEquals("localhost", options["HOST"])
        assertEquals("true", options["REDIRECT_OUTPUT"])
        assertEquals("true", options["SUSPEND_AFTER_CONNECT"])

        val mappings = configuration.getChild("PathMappingSettings")
        assertEquals(
            "pathMappings",
            mappings.getChild("option").getAttributeValue("name"),
        )
    }
}