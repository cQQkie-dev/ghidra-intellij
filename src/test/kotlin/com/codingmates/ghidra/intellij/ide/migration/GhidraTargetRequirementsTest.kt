package com.codingmates.ghidra.intellij.ide.migration

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GhidraTargetRequirementsTest {

    @Test
    fun `Ghidra 12 1 requirements accept Java 21 and Gradle 9 6`() {
        val requirements = GhidraTargetRequirements(
            javaCompilerVersion = 21,
            javaMinimumVersion = 21,
            javaMaximumVersion = null,
            gradleMinimumVersion = GhidraGradleVersion.parseOrNull("8.5"),
            gradleMaximumVersion = null,
        )

        assertEquals(21, requirements.requiredJavaVersion)
        assertTrue(requirements.isJavaCompatible(21))
        assertTrue(requirements.isJavaCompatible(25))
        assertFalse(requirements.isJavaCompatible(17))

        assertTrue(
            requirements.isGradleCompatible(
                GhidraGradleVersion.parseOrNull("9.6.0")
            )
        )
        assertFalse(
            requirements.isGradleCompatible(
                GhidraGradleVersion.parseOrNull("8.4")
            )
        )
    }

    @Test
    fun `Ghidra 12 2 requirements require Java 25 and Gradle 9 1`() {
        val requirements = GhidraTargetRequirements(
            javaCompilerVersion = 25,
            javaMinimumVersion = 25,
            javaMaximumVersion = null,
            gradleMinimumVersion = GhidraGradleVersion.parseOrNull("9.1"),
            gradleMaximumVersion = null,
        )

        assertEquals(25, requirements.requiredJavaVersion)
        assertFalse(requirements.isJavaCompatible(21))
        assertTrue(requirements.isJavaCompatible(25))

        assertFalse(
            requirements.isGradleCompatible(
                GhidraGradleVersion.parseOrNull("8.13")
            )
        )
        assertTrue(
            requirements.isGradleCompatible(
                GhidraGradleVersion.parseOrNull("9.1")
            )
        )
        assertTrue(
            requirements.isGradleCompatible(
                GhidraGradleVersion.parseOrNull("9.6")
            )
        )
    }

    @Test
    fun `Java and Gradle maximum versions are enforced when supplied`() {
        val requirements = GhidraTargetRequirements(
            javaCompilerVersion = 21,
            javaMinimumVersion = 21,
            javaMaximumVersion = 25,
            gradleMinimumVersion = GhidraGradleVersion.parseOrNull("8.5"),
            gradleMaximumVersion = GhidraGradleVersion.parseOrNull("9.6"),
        )

        assertTrue(requirements.isJavaCompatible(25))
        assertFalse(requirements.isJavaCompatible(26))

        assertTrue(
            requirements.isGradleCompatible(
                GhidraGradleVersion.parseOrNull("9.6")
            )
        )
        assertFalse(
            requirements.isGradleCompatible(
                GhidraGradleVersion.parseOrNull("9.7")
            )
        )
    }

    @Test
    fun `unknown Java or Gradle versions are incompatible`() {
        val requirements = GhidraTargetRequirements(
            javaCompilerVersion = 25,
            javaMinimumVersion = 25,
            javaMaximumVersion = null,
            gradleMinimumVersion = GhidraGradleVersion.parseOrNull("9.1"),
            gradleMaximumVersion = null,
        )

        assertFalse(requirements.isJavaCompatible(null))
        assertFalse(requirements.isGradleCompatible(null))
    }
}