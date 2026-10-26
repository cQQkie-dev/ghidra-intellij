package com.codingmates.ghidra.intellij.ide.migration

/**
 * Determines whether a normal IntelliJ Gradle reload can be requested without a known
 * target/tooling conflict.
 *
 * This does not guarantee that arbitrary build-script code will succeed. It prevents the known
 * Ghidra migration hazards: global target overrides, incompatible Wrapper versions, and an
 * incompatible linked Gradle JVM.
 */
object GhidraGradleReloadEligibility {

    fun canReload(
        plan: GhidraTargetMigrationPlan,
        willUpdateGradleJvm: Boolean,
    ): Boolean {
        if (plan.requiresAcknowledgement) {
            return false
        }

        val wrapperIssueCodes = setOf(
            GhidraTargetMigrationIssueCode.WRAPPER_NOT_FOUND,
            GhidraTargetMigrationIssueCode.WRAPPER_VERSION_UNAVAILABLE,
            GhidraTargetMigrationIssueCode.WRAPPER_GRADLE_TOO_OLD,
            GhidraTargetMigrationIssueCode.WRAPPER_GRADLE_TOO_NEW,
        )

        if (plan.issues.any { it.code in wrapperIssueCodes }) {
            return false
        }

        val gradleJvmIssueCodes = setOf(
            GhidraTargetMigrationIssueCode.GRADLE_JVM_UNAVAILABLE,
            GhidraTargetMigrationIssueCode.GRADLE_JVM_INCOMPATIBLE,
        )

        val hasGradleJvmIssue = plan.issues.any { issue ->
            issue.code in gradleJvmIssueCodes
        }

        return !hasGradleJvmIssue || willUpdateGradleJvm
    }
}