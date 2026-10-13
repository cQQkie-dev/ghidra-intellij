package com.codingmates.ghidra.intellij.ide.script

import java.io.IOException
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption

object GhidraScriptGenerator {

    private val validScriptName = Regex("[A-Za-z_][A-Za-z0-9_]*")

    /**
     * Creates a Ghidra Java or Python script in [scriptDirectory].
     *
     * The returned path is the newly created script. Existing files are never overwritten.
     */
    @Throws(GhidraScriptGenerationException::class)
    fun create(
        scriptDirectory: Path,
        spec: GhidraScriptSpec,
    ): Path {
        validate(spec)

        val normalizedDirectory =
            scriptDirectory.toAbsolutePath().normalize()

        val scriptPath = normalizedDirectory.resolve(
            "${spec.name}.${spec.language.extension}"
        )

        try {
            Files.createDirectories(normalizedDirectory)

            Files.writeString(
                scriptPath,
                render(spec),
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE_NEW,
            )
        }
        catch (e: IOException) {
            throw GhidraScriptGenerationException(
                "Cannot create Ghidra script: $scriptPath",
                e,
            )
        }

        return scriptPath
    }

    fun render(spec: GhidraScriptSpec): String {
        validate(spec)

        return when (spec.language) {
            GhidraScriptLanguage.JAVA -> renderJava(spec)
            GhidraScriptLanguage.PYGHIDRA,
            GhidraScriptLanguage.JYTHON,
            -> renderPython(spec)
        }
    }

    private fun validate(spec: GhidraScriptSpec) {
        if (!validScriptName.matches(spec.name)) {
            throw GhidraScriptGenerationException(
                "Invalid Ghidra script name '${spec.name}'. " +
                    "Use a letter or underscore followed by letters, digits, or underscores."
            )
        }
    }

    private fun renderJava(spec: GhidraScriptSpec): String = buildString {
        appendMetadata(this, "//", spec)
        appendLine()
        appendLine("import ghidra.app.script.GhidraScript;")
        appendLine()
        appendLine("public class ${spec.name} extends GhidraScript {")
        appendLine("    @Override")
        appendLine("    protected void run() throws Exception {")
        appendLine("        // TODO: Add script code here")
        appendLine("    }")
        appendLine("}")
    }

    private fun renderPython(spec: GhidraScriptSpec): String = buildString {
        appendMetadata(this, "#", spec)
        appendLine()
        appendLine("# TODO: Add script code here")
    }

    private fun appendMetadata(
        builder: StringBuilder,
        commentPrefix: String,
        spec: GhidraScriptSpec,
    ) {
        val description = spec.description.ifBlank {
            "TODO write a description for this script"
        }

        description
            .lineSequence()
            .filter(String::isNotBlank)
            .forEach { line ->
                builder.appendLine("$commentPrefix$line")
            }

        builder.appendLine("$commentPrefix@author ${spec.author}")
        builder.appendLine(
            "$commentPrefix@category ${spec.category.ifBlank { "_NEW_" }}"
        )
        builder.appendLine("$commentPrefix@keybinding")
        builder.appendLine("$commentPrefix@menupath")
        builder.appendLine("$commentPrefix@toolbar")
        builder.appendLine("$commentPrefix@runtime ${spec.language.runtimeName}")
    }
}