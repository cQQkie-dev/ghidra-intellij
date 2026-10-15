package com.codingmates.ghidra.intellij.ide.runConfiguration

import kotlin.test.Test
import kotlin.test.assertEquals

class PyGhidraJavaDebugConfiguratorTest {

    @Test
    fun `enables PyGhidra Java debug while preserving configured environment`() {
        val environment = PyGhidraJavaDebugConfigurator.environmentWithDebugEnabled(
            mapOf(
                "CUSTOM_SETTING" to "value",
                "GHIDRA_JAVA_OPTIONS" to "-Xmx4G",
            )
        )

        assertEquals("value", environment["CUSTOM_SETTING"])
        assertEquals("-Xmx4G", environment["GHIDRA_JAVA_OPTIONS"])
        assertEquals(
            "1",
            environment[
                PyGhidraJavaDebugConfigurator
                    .PYGHIDRA_DEBUG_ENVIRONMENT_VARIABLE
            ],
        )
    }

    @Test
    fun `replaces an existing PyGhidra debug value`() {
        val environment = PyGhidraJavaDebugConfigurator.environmentWithDebugEnabled(
            mapOf(
                "PYGHIDRA_DEBUG" to "0",
            )
        )

        assertEquals("1", environment["PYGHIDRA_DEBUG"])
    }
}