package com.codingmates.ghidra.intellij.ide.gradle

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import java.io.File
import java.nio.file.Path

class GhidraGradleCommandRunnerTest {

    @Test
    fun `removes only inherited Ghidra install target from Gradle environment`() {
        val sanitizedEnvironment =
            GhidraGradleCommandRunner.sanitizedEnvironment(
                mapOf(
                    "GHIDRA_INSTALL_DIR" to "/example/ghidra/current",
                    "HOME" to "/example/home",
                    "JAVA_HOME" to "/example/java-25",
                    "PATH" to "/example/bin",
                )
            )

        assertFalse(
            sanitizedEnvironment.containsKey("GHIDRA_INSTALL_DIR")
        )

        assertEquals(
            "/example/home",
            sanitizedEnvironment["HOME"],
        )
        assertEquals(
            "/example/java-25",
            sanitizedEnvironment["JAVA_HOME"],
        )
        assertEquals(
            "/example/bin",
            sanitizedEnvironment["PATH"],
        )
    }

    @Test
    fun `keeps environment unchanged when no Ghidra target is inherited`() {
        val environment = mapOf(
            "HOME" to "/example/home",
            "PATH" to "/example/bin",
        )

        val sanitizedEnvironment =
            GhidraGradleCommandRunner.sanitizedEnvironment(environment)

        assertEquals(environment, sanitizedEnvironment)
        assertTrue(sanitizedEnvironment.containsKey("HOME"))
    }

    @Test
    fun `uses selected Java home while preserving non Ghidra environment`() {
        val sanitizedEnvironment =
            GhidraGradleCommandRunner.sanitizedEnvironment(
                parentEnvironment = mapOf(
                    "GHIDRA_INSTALL_DIR" to "/example/ghidra/current",
                    "PATH" to "/example/bin",
                    "HOME" to "/example/home",
                ),
                javaHome = Path.of("/example/java-25"),
            )

        assertFalse(
            sanitizedEnvironment.containsKey("GHIDRA_INSTALL_DIR")
        )
        assertEquals(
            "/example/java-25",
            sanitizedEnvironment["JAVA_HOME"],
        )
        assertEquals(
            "/example/java-25/bin${File.pathSeparator}/example/bin",
            sanitizedEnvironment["PATH"],
        )
        assertEquals(
            "/example/home",
            sanitizedEnvironment["HOME"],
        )
    }
}