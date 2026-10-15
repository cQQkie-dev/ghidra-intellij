package com.codingmates.ghidra.intellij.ide.runConfiguration

import java.io.File
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals

class PyGhidraPythonDebugSupportTest {

    @Test
    fun `prepends PyDev helper to Python path`() {
        val helperDirectory = Path.of("/opt/idea/python-ce/helpers/pydev")

        val environment = PyGhidraPythonDebugSupport.environmentWithPydevHelper(
            environment = mapOf(
                "CUSTOM_SETTING" to "value",
                "PYTHONPATH" to listOf(
                    "/existing/python/path",
                    "/another/python/path",
                ).joinToString(File.pathSeparator),
            ),
            helperDirectory = helperDirectory,
        )

        assertEquals("value", environment["CUSTOM_SETTING"])
        assertEquals(
            listOf(
                "/opt/idea/python-ce/helpers/pydev",
                "/existing/python/path",
                "/another/python/path",
            ).joinToString(File.pathSeparator),
            environment["PYTHONPATH"],
        )
    }

    @Test
    fun `does not duplicate PyDev helper in Python path`() {
        val helperDirectory = Path.of("/opt/idea/python-ce/helpers/pydev")

        val environment = PyGhidraPythonDebugSupport.environmentWithPydevHelper(
            environment = mapOf(
                "PYTHONPATH" to listOf(
                    "/existing/python/path",
                    "/opt/idea/python-ce/helpers/pydev",
                ).joinToString(File.pathSeparator),
            ),
            helperDirectory = helperDirectory,
        )

        assertEquals(
            listOf(
                "/opt/idea/python-ce/helpers/pydev",
                "/existing/python/path",
            ).joinToString(File.pathSeparator),
            environment["PYTHONPATH"],
        )
    }
}