package com.codingmates.ghidra.intellij.ide.script

enum class GhidraScriptLanguage(
    val displayName: String,
    val extension: String,
    val runtimeName: String,
) {
    JAVA("Java", "java", "Java"),
    PYGHIDRA("PyGhidra (Python 3)", "py", "PyGhidra"),
    JYTHON("Jython (legacy Python 2)", "py", "Jython");

    override fun toString(): String = displayName
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