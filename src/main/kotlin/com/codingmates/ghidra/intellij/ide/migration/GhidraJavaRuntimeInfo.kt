package com.codingmates.ghidra.intellij.ide.migration

import java.nio.file.Path

/**
 * Java runtime information collected from an IntelliJ SDK, launcher JRE, or Gradle JVM.
 *
 * This remains IntelliJ-independent. IntelliJ integration code resolves SDKs and converts them
 * into this compact representation before passing them to the migration planner.
 */
data class GhidraJavaRuntimeInfo(
    val label: String,
    val featureVersion: Int?,
    val homePath: Path?,
)