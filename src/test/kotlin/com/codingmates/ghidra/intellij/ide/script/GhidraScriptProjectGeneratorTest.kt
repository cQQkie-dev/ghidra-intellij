package com.codingmates.ghidra.intellij.ide.script

import java.nio.file.Files
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class GhidraScriptProjectGeneratorTest {

    @Test
    fun `creates Gradle-backed Ghidra script project`() {
        val parentDirectory = Files.createTempDirectory("ghidra-script-projects")

        val generated = GhidraScriptProjectGenerator.create(
            GhidraScriptProjectSpec(
                name = "ExampleScripts",
                projectParentDirectory = parentDirectory,
            )
        )

        assertEquals(
            parentDirectory.resolve("ExampleScripts"),
            generated.projectDirectory,
        )
        assertTrue(generated.buildFile.exists())
        assertTrue(generated.settingsFile.exists())
        assertTrue(generated.scriptDirectory.exists())

        assertTrue(
            generated.buildFile.readText().contains(
                "support/buildExtension.gradle"
            )
        )
        assertTrue(
            generated.buildFile.readText().contains(
                """srcDir "ghidra_scripts""""
            )
        )
        assertEquals(
            """rootProject.name = "ExampleScripts"""" + "\n",
            generated.settingsFile.readText(),
        )
    }

    @Test
    fun `rejects invalid project name`() {
        val exception =
            assertFailsWith<GhidraScriptProjectGenerationException> {
                GhidraScriptProjectGenerator.create(
                    GhidraScriptProjectSpec(
                        name = "123 invalid project",
                        projectParentDirectory =
                            Files.createTempDirectory("ghidra-script-projects"),
                    )
                )
            }

        assertTrue(exception.message!!.contains("Invalid Ghidra script project name"))
    }

    @Test
    fun `does not overwrite existing project directory`() {
        val parentDirectory = Files.createTempDirectory("ghidra-script-projects")
        Files.createDirectories(parentDirectory.resolve("ExampleScripts"))

        assertFailsWith<GhidraScriptProjectGenerationException> {
            GhidraScriptProjectGenerator.create(
                GhidraScriptProjectSpec(
                    name = "ExampleScripts",
                    projectParentDirectory = parentDirectory,
                )
            )
        }
    }
}