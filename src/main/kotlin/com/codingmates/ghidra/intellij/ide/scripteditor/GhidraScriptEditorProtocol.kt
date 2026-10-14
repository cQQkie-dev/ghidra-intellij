package com.codingmates.ghidra.intellij.ide.scripteditor

import java.nio.file.Path

object GhidraScriptEditorProtocol {

    private const val OPEN_PREFIX = "open_"

    /**
     * Parses the Eclipse GhidraDev-compatible request format:
     *
     * open_/absolute/path/to/script.java
     */
    fun requestedPath(line: String): Path? {
        if (!line.startsWith(OPEN_PREFIX)) {
            return null
        }

        val pathString = line.removePrefix(OPEN_PREFIX)
            .trim()
            .takeIf(String::isNotEmpty)
            ?: return null

        return runCatching {
            Path.of(pathString)
                .toAbsolutePath()
                .normalize()
        }.getOrNull()
    }
}