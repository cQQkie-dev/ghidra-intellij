package com.codingmates.ghidra.intellij.ide.gradle

import java.nio.file.Path

/**
 * A Gradle command for a Ghidra project.
 *
 * [executable] is either a project Gradle Wrapper or the `gradle` executable resolved through
 * the user's PATH. [arguments] excludes the executable itself.
 */
data class GhidraGradleCommand(
    val executable: String,
    val arguments: List<String>,
    val workingDirectory: Path,
    val usesWrapper: Boolean,
)