package com.codingmates.ghidra.intellij.ide.migration

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GhidraGradleVersionTest {

    @Test
    fun `compares Gradle versions with different component counts`() {
        val gradle85 = GhidraGradleVersion.parseOrNull("8.5")!!
        val gradle850 = GhidraGradleVersion.parseOrNull("8.5.0")!!
        val gradle91 = GhidraGradleVersion.parseOrNull("9.1")!!
        val gradle960 = GhidraGradleVersion.parseOrNull("9.6.0")!!

        assertEquals(0, gradle85.compareTo(gradle850))
        assertTrue(gradle85 < gradle91)
        assertTrue(gradle91 < gradle960)
    }

    @Test
    fun `rejects missing or non numeric Gradle versions`() {
        assertNull(GhidraGradleVersion.parseOrNull(null))
        assertNull(GhidraGradleVersion.parseOrNull(""))
        assertNull(GhidraGradleVersion.parseOrNull("9.1-rc-1"))
        assertNull(GhidraGradleVersion.parseOrNull("Gradle 9.1"))
    }
}