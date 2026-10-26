package com.codingmates.ghidra.intellij.ide.migration

import com.codingmates.ghidra.intellij.ide.installation.GhidraInstallation

/**
 * Java and Gradle constraints declared by a selected Ghidra runtime installation.
 *
 * This model is intentionally independent of IntelliJ Platform APIs so it can be tested without
 * an IDE fixture. IntelliJ-specific code will later provide Project SDK, launcher JRE, Gradle JVM,
 * and wrapper snapshots to the migration planner.
 */
data class GhidraTargetRequirements(
    val javaCompilerVersion: Int?,
    val javaMinimumVersion: Int?,
    val javaMaximumVersion: Int?,
    val gradleMinimumVersion: GhidraGradleVersion?,
    val gradleMaximumVersion: GhidraGradleVersion?,
) {

    /**
     * Minimum Java feature version needed to compile and run against the selected Ghidra runtime.
     *
     * `application.java.compiler` is the source/compiler requirement. `application.java.min` is
     * the runtime minimum. Both constraints apply, so the stricter value is authoritative.
     */
    val requiredJavaVersion: Int?
        get() = listOfNotNull(
            javaCompilerVersion,
            javaMinimumVersion,
        ).maxOrNull()

    fun isJavaCompatible(featureVersion: Int?): Boolean {
        if (featureVersion == null) {
            return false
        }

        val requiredVersion = requiredJavaVersion
        if (requiredVersion != null && featureVersion < requiredVersion) {
            return false
        }

        val maximumVersion = javaMaximumVersion
        if (maximumVersion != null && featureVersion > maximumVersion) {
            return false
        }

        return true
    }

    fun isGradleCompatible(version: GhidraGradleVersion?): Boolean {
        if (version == null) {
            return false
        }

        val minimumVersion = gradleMinimumVersion
        if (minimumVersion != null && version < minimumVersion) {
            return false
        }

        val maximumVersion = gradleMaximumVersion
        if (maximumVersion != null && version >= maximumVersion) {
            return false
        }

        return true
    }

    companion object {

        fun from(installation: GhidraInstallation): GhidraTargetRequirements =
            GhidraTargetRequirements(
                javaCompilerVersion = installation.javaCompilerVersion,
                javaMinimumVersion = installation.javaMinimumVersion,
                javaMaximumVersion = installation.javaMaximumVersion,
                gradleMinimumVersion =
                    GhidraGradleVersion.parseOrNull(
                        installation.gradleMinimumVersion
                    ),
                gradleMaximumVersion =
                    GhidraGradleVersion.parseOrNull(
                        installation.gradleMaximumVersion
                    ),
            )
    }
}