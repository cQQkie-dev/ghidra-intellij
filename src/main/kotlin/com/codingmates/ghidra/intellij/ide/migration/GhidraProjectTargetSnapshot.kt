package com.codingmates.ghidra.intellij.ide.migration

import java.nio.file.Path

/**
 * Pure snapshot of the Ghidra target-related state currently known for one project.
 *
 * IntelliJ-specific code will later collect Project SDK, launcher JRE, and linked Gradle JVM
 * information and extend this snapshot. This first version intentionally contains only filesystem
 * and already-persisted project state.
 */
data class GhidraProjectTargetSnapshot(
    val projectRoot: Path,
    val localInstallationPath: Path?,
    val facetInstallationPath: Path?,
    val wrapper: GhidraGradleWrapperInfo?,
    val environmentOverride: GhidraTargetOverride?,
    val userGradlePropertiesOverride: GhidraTargetOverride?,
    val projectSdk: GhidraJavaRuntimeInfo? = null,
    val explicitLauncherJre: GhidraJavaRuntimeInfo? = null,
    val gradleJvm: GhidraJavaRuntimeInfo? = null,
)