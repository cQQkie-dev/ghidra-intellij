package com.codingmates.ghidra.intellij.ide.script

import java.nio.file.Files
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class GhidraScriptGeneratorTest {

    @Test
    fun `creates Java Ghidra script with metadata`() {
        val scriptsDirectory = Files.createTempDirectory("ghidra-scripts")

        val scriptPath = GhidraScriptGenerator.create(
            scriptsDirectory,
            GhidraScriptSpec(
                name = "ExampleScript",
                language = GhidraScriptLanguage.JAVA,
                author = "Example Author",
                category = "Examples",
                description = "Demonstrates Ghidra script generation.",
            ),
        )

        assertEquals(
            scriptsDirectory.resolve("ExampleScript.java"),
            scriptPath,
        )
        assertTrue(scriptPath.exists())

        assertEquals(
            """
            //Demonstrates Ghidra script generation.
            //@author Example Author
            //@category Examples
            //@keybinding
            //@menupath
            //@toolbar
            //@runtime Java

            import ghidra.app.script.GhidraScript;

            public class ExampleScript extends GhidraScript {
                @Override
                protected void run() throws Exception {
                    // TODO: Add script code here
                }
            }

            """.trimIndent(),
            scriptPath.readText(),
        )
    }

    @Test
    fun `creates Python Ghidra script with metadata`() {
        val scriptsDirectory = Files.createTempDirectory("ghidra-scripts")

        val scriptPath = GhidraScriptGenerator.create(
            scriptsDirectory,
            GhidraScriptSpec(
                name = "ExampleScript",
                language = GhidraScriptLanguage.PYGHIDRA,
                author = "Example Author",
                category = "Examples",
                description = "Demonstrates Python script generation.",
            ),
        )

        assertEquals(
            scriptsDirectory.resolve("ExampleScript.py"),
            scriptPath,
        )

        assertEquals(
            """
            #Demonstrates Python script generation.
            #@author Example Author
            #@category Examples
            #@keybinding
            #@menupath
            #@toolbar
            #@runtime PyGhidra

            # TODO: Add script code here

            """.trimIndent(),
            scriptPath.readText(),
        )
    }

    @Test
    fun `rejects invalid script names`() {
        val exception = assertFailsWith<GhidraScriptGenerationException> {
            GhidraScriptGenerator.render(
                GhidraScriptSpec(
                    name = "123 invalid script",
                    language = GhidraScriptLanguage.JAVA,
                ),
            )
        }

        assertTrue(exception.message!!.contains("Invalid Ghidra script name"))
    }

    @Test
    fun `does not overwrite existing script`() {
        val scriptsDirectory = Files.createTempDirectory("ghidra-scripts")

        GhidraScriptGenerator.create(
            scriptsDirectory,
            GhidraScriptSpec(
                name = "ExampleScript",
                language = GhidraScriptLanguage.JAVA,
            ),
        )

        assertFailsWith<GhidraScriptGenerationException> {
            GhidraScriptGenerator.create(
                scriptsDirectory,
                GhidraScriptSpec(
                    name = "ExampleScript",
                    language = GhidraScriptLanguage.JAVA,
                ),
            )
        }
    }
}