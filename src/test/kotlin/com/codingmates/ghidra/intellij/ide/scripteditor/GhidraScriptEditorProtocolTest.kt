package com.codingmates.ghidra.intellij.ide.scripteditor

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class GhidraScriptEditorProtocolTest {

    @Test
    fun `parses open request path`() {
        assertEquals(
            Path.of("/example/scripts/TestScript.java"),
            GhidraScriptEditorProtocol.requestedPath(
                "open_/example/scripts/TestScript.java"
            ),
        )
    }

    @Test
    fun `rejects unsupported command`() {
        assertNull(
            GhidraScriptEditorProtocol.requestedPath(
                "close_/example/scripts/TestScript.java"
            )
        )
    }

    @Test
    fun `rejects empty open request`() {
        assertNull(
            GhidraScriptEditorProtocol.requestedPath("open_")
        )
    }
}