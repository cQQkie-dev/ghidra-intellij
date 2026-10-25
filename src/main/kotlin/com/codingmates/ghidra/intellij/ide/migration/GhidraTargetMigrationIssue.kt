package com.codingmates.ghidra.intellij.ide.migration

/**
 * Severity used by the target-migration dialog.
 *
 * [ACKNOWLEDGEMENT_REQUIRED] means that applying local/facet changes is permitted, but normal
 * Gradle sync/build may still resolve another Ghidra installation unless the user explicitly
 * acknowledges the global override.
 */
enum class GhidraTargetMigrationIssueSeverity {
    INFORMATION,
    WARNING,
    ACKNOWLEDGEMENT_REQUIRED,
}

/**
 * Stable identifiers for target migration diagnostics.
 */
enum class GhidraTargetMigrationIssueCode {
    LOCAL_TARGET_DIFFERS_FROM_SELECTED,
    FACET_TARGET_DIFFERS_FROM_SELECTED,
    LOCAL_AND_FACET_TARGET_DIFFER,

    PROJECT_SDK_UNAVAILABLE,
    PROJECT_SDK_INCOMPATIBLE,

    EXPLICIT_LAUNCHER_JRE_UNAVAILABLE,
    EXPLICIT_LAUNCHER_JRE_INCOMPATIBLE,

    GRADLE_JVM_UNAVAILABLE,
    GRADLE_JVM_INCOMPATIBLE,

    WRAPPER_NOT_FOUND,
    WRAPPER_VERSION_UNAVAILABLE,
    WRAPPER_GRADLE_TOO_OLD,
    WRAPPER_GRADLE_TOO_NEW,

    ENVIRONMENT_TARGET_OVERRIDE,
    USER_GRADLE_PROPERTIES_TARGET_OVERRIDE,
}

/**
 * One diagnostic emitted while planning a Ghidra project target migration.
 */
data class GhidraTargetMigrationIssue(
    val code: GhidraTargetMigrationIssueCode,
    val severity: GhidraTargetMigrationIssueSeverity,
    val message: String,
)