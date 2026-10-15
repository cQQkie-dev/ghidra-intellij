package com.codingmates.ghidra.intellij.ide.runConfiguration

import kotlin.test.Test
import kotlin.test.assertEquals
import org.jdom.Element

class PyGhidraJavaDebugAttachConfigurationTest {

    @Test
    fun `remote attach configuration uses PyGhidra JDWP endpoint`() {
        val configuration =
            PyGhidraJavaDebugAttachConfigurator.remoteAttachConfigurationElement()

        val options = configuration.getChildren("option")
            .associate {
                it.getAttributeValue("name") to it.getAttributeValue("value")
            }

        assertEquals("Remote", configuration.getAttributeValue("type"))
        assertEquals("true", options["USE_SOCKET_TRANSPORT"])
        assertEquals("false", options["SERVER_MODE"])
        assertEquals("127.0.0.1", options["HOST"])
        assertEquals("18001", options["PORT"])
        assertEquals("false", options["AUTO_RESTART"])
    }
}