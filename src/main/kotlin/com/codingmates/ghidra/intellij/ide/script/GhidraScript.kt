package com.codingmates.ghidra.intellij.ide.script

enum class GhidraScriptLanguage(
    val extension: String,
) {
    JAVA("java"),
    PYTHON("py"),
}

data class GhidraScriptSpec(
    val name: String,
    val language: GhidraScriptLanguage,
    val author: String = "",
    val category: String = "",
    val description: String = "",
)

class GhidraScriptGenerationException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)