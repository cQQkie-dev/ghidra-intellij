package com.codingmates.ghidra.intellij.ide.gradle

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class GhidraGradleExecutableVersionInspectorTest {

    @Test
    fun `parses Gradle version from standard version output`() {
        val output =
            """
            ------------------------------------------------------------
            Gradle 9.8.2
            ------------------------------------------------------------

            Build time:    2026-10-20 14:56:01 UTC
            """.trimIndent()

        assertEquals(
            "9.8.2",
            GhidraGradleExecutableVersionInspector
                .parseVersion(output)
                .toString(),
        )
    }

    @Test
    fun `returns null when output contains no Gradle version`() {
        assertNull(
            GhidraGradleExecutableVersionInspector
                .parseVersion("unexpected output")
        )
    }
}