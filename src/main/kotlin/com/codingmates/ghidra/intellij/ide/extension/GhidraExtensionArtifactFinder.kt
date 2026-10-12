package com.codingmates.ghidra.intellij.ide.extension

import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.extension
import kotlin.io.path.isRegularFile
import kotlin.streams.toList

object GhidraExtensionArtifactFinder {

    /**
     * Finds the newest extension ZIP directly inside the project's `dist` directory.
     *
     * The official Ghidra `buildExtension.gradle` task writes distributable extension archives
     * to `<project>/dist/`.
     */
    fun findNewest(projectRoot: Path): Path? {
        val distributionDirectory =
            projectRoot.toAbsolutePath().normalize().resolve("dist")

        if (!Files.isDirectory(distributionDirectory)) {
            return null
        }

        return try {
            Files.list(distributionDirectory).use { entries ->
                entries
                    .filter { it.isRegularFile() && it.extension.equals("zip", ignoreCase = true) }
                    .toList()
                    .maxByOrNull { archive ->
                        Files.getLastModifiedTime(archive).toMillis()
                    }
            }
        }
        catch (_: IOException) {
            null
        }
    }
}