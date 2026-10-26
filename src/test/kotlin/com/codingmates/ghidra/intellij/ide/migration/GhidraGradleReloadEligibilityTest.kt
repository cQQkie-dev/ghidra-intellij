package com.codingmates.ghidra.intellij.ide.migration

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GhidraGradleReloadEligibilityTest {

    @Test
    fun `allows reload when no known target tooling issue exists`() {
        assertTrue(
            GhidraGradleReloadEligibility.canReload(
                plan = planWithIssues(),
                willUpdateGradleJvm = false,
            )
        )
    }

    @Test
    fun `blocks reload for incompatible Gradle Wrapper`() {
        assertFalse(
            GhidraGradleReloadEligibility.canReload(
                plan = planWithIssues(
                    issue(
                        GhidraTargetMigrationIssueCode.WRAPPER_GRADLE_TOO_OLD,
                        GhidraTargetMigrationIssueSeverity.WARNING,
                    )
                ),
                willUpdateGradleJvm = true,
            )
        )
    }

    @Test
    fun `requires Gradle JVM update before reload`() {
        val plan = planWithIssues(
            issue(
                GhidraTargetMigrationIssueCode.GRADLE_JVM_INCOMPATIBLE,
                GhidraTargetMigrationIssueSeverity.WARNING,
            )
        )

        assertFalse(
            GhidraGradleReloadEligibility.canReload(
                plan = plan,
                willUpdateGradleJvm = false,
            )
        )

        assertTrue(
            GhidraGradleReloadEligibility.canReload(
                plan = plan,
                willUpdateGradleJvm = true,
            )
        )
    }

    @Test
    fun `blocks reload when global override acknowledgement is required`() {
        assertFalse(
            GhidraGradleReloadEligibility.canReload(
                plan = planWithIssues(
                    issue(
                        GhidraTargetMigrationIssueCode.ENVIRONMENT_TARGET_OVERRIDE,
                        GhidraTargetMigrationIssueSeverity.ACKNOWLEDGEMENT_REQUIRED,
                    )
                ),
                willUpdateGradleJvm = true,
            )
        )
    }

    private fun planWithIssues(
        vararg issues: GhidraTargetMigrationIssue,
    ): GhidraTargetMigrationPlan =
        GhidraTargetMigrationPlan(
            selectedInstallationPath = Path.of("/example/ghidra"),
            selectedGhidraVersion = "12.2",
            selectedGhidraReleaseName = "PUBLIC",
            requirements = GhidraTargetRequirements(
                javaCompilerVersion = 25,
                javaMinimumVersion = 25,
                javaMaximumVersion = null,
                gradleMinimumVersion = GhidraGradleVersion.parseOrNull("9.1"),
                gradleMaximumVersion = null,
            ),
            issues = issues.toList(),
        )

    private fun issue(
        code: GhidraTargetMigrationIssueCode,
        severity: GhidraTargetMigrationIssueSeverity,
    ): GhidraTargetMigrationIssue =
        GhidraTargetMigrationIssue(
            code = code,
            severity = severity,
            message = "Test issue: $code",
        )
}