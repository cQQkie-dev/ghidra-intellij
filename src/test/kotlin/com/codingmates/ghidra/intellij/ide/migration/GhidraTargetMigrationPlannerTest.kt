package com.codingmates.ghidra.intellij.ide.migration

import com.codingmates.ghidra.intellij.ide.installation.GhidraInstallation
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GhidraTargetMigrationPlannerTest {

    @Test
    fun `reports coherent project with compatible Gradle Wrapper`() {
        val projectRoot = Files.createTempDirectory("ghidra-migration-project")
        val selectedInstallation = installation(
            path = Path.of("/example/ghidra-12.1.4"),
            version = "12.1.4",
            javaVersion = 21,
            gradleMinimum = "8.5",
        )

        val plan = GhidraTargetMigrationPlanner.plan(
            snapshot = GhidraProjectTargetSnapshot(
                projectRoot = projectRoot,
                localInstallationPath = selectedInstallation.configuredPath,
                facetInstallationPath = selectedInstallation.configuredPath,
                wrapper = wrapperInfo(
                    projectRoot = projectRoot,
                    version = "9.6.0",
                ),
                environmentOverride = null,
                userGradlePropertiesOverride = null,
            ),
            selectedInstallation = selectedInstallation,
        )

        assertTrue(plan.issues.isEmpty())
        assertFalse(plan.requiresAcknowledgement)
    }

    @Test
    fun `reports local and facet targets that differ from selected target`() {
        val projectRoot = Files.createTempDirectory("ghidra-migration-project")
        val oldInstallation = Path.of("/example/ghidra-12.1.4")
        val selectedInstallation = installation(
            path = Path.of("/example/ghidra-12.2"),
            version = "12.2",
            javaVersion = 25,
            gradleMinimum = "9.1",
        )

        val plan = GhidraTargetMigrationPlanner.plan(
            snapshot = GhidraProjectTargetSnapshot(
                projectRoot = projectRoot,
                localInstallationPath = oldInstallation,
                facetInstallationPath = oldInstallation,
                wrapper = wrapperInfo(
                    projectRoot = projectRoot,
                    version = "9.6",
                ),
                environmentOverride = null,
                userGradlePropertiesOverride = null,
            ),
            selectedInstallation = selectedInstallation,
        )

        assertEquals(
            setOf(
                GhidraTargetMigrationIssueCode.LOCAL_TARGET_DIFFERS_FROM_SELECTED,
                GhidraTargetMigrationIssueCode.FACET_TARGET_DIFFERS_FROM_SELECTED,
            ),
            plan.issues.map(GhidraTargetMigrationIssue::code).toSet(),
        )
    }

    @Test
    fun `reports local and facet mismatch`() {
        val projectRoot = Files.createTempDirectory("ghidra-migration-project")
        val selectedInstallation = installation(
            path = Path.of("/example/ghidra-12.2"),
            version = "12.2",
            javaVersion = 25,
            gradleMinimum = "9.1",
        )

        val plan = GhidraTargetMigrationPlanner.plan(
            snapshot = GhidraProjectTargetSnapshot(
                projectRoot = projectRoot,
                localInstallationPath = Path.of("/example/ghidra-12.1.4"),
                facetInstallationPath = selectedInstallation.configuredPath,
                wrapper = wrapperInfo(
                    projectRoot = projectRoot,
                    version = "9.6",
                ),
                environmentOverride = null,
                userGradlePropertiesOverride = null,
            ),
            selectedInstallation = selectedInstallation,
        )

        assertTrue(
            plan.issues.any {
                it.code ==
                    GhidraTargetMigrationIssueCode.LOCAL_AND_FACET_TARGET_DIFFER &&
                    it.severity == GhidraTargetMigrationIssueSeverity.WARNING
            }
        )
    }

    @Test
    fun `reports unsupported Gradle Wrapper version`() {
        val projectRoot = Files.createTempDirectory("ghidra-migration-project")
        val selectedInstallation = installation(
            path = Path.of("/example/ghidra-12.2"),
            version = "12.2",
            javaVersion = 25,
            gradleMinimum = "9.1",
        )

        val plan = GhidraTargetMigrationPlanner.plan(
            snapshot = GhidraProjectTargetSnapshot(
                projectRoot = projectRoot,
                localInstallationPath = null,
                facetInstallationPath = null,
                wrapper = wrapperInfo(
                    projectRoot = projectRoot,
                    version = "8.13",
                ),
                environmentOverride = null,
                userGradlePropertiesOverride = null,
            ),
            selectedInstallation = selectedInstallation,
        )

        assertTrue(
            plan.issues.any {
                it.code ==
                    GhidraTargetMigrationIssueCode.WRAPPER_GRADLE_TOO_OLD
            }
        )
    }

    @Test
    fun `reports missing Gradle Wrapper`() {
        val projectRoot = Files.createTempDirectory("ghidra-migration-project")
        val selectedInstallation = installation(
            path = Path.of("/example/ghidra-12.2"),
            version = "12.2",
            javaVersion = 25,
            gradleMinimum = "9.1",
        )

        val plan = GhidraTargetMigrationPlanner.plan(
            snapshot = GhidraProjectTargetSnapshot(
                projectRoot = projectRoot,
                localInstallationPath = null,
                facetInstallationPath = null,
                wrapper = null,
                environmentOverride = null,
                userGradlePropertiesOverride = null,
            ),
            selectedInstallation = selectedInstallation,
        )

        assertTrue(
            plan.issues.any {
                it.code ==
                    GhidraTargetMigrationIssueCode.WRAPPER_NOT_FOUND
            }
        )
    }

    @Test
    fun `global environment override requires acknowledgement`() {
        val projectRoot = Files.createTempDirectory("ghidra-migration-project")
        val selectedInstallation = installation(
            path = Path.of("/example/ghidra-12.2"),
            version = "12.2",
            javaVersion = 25,
            gradleMinimum = "9.1",
        )

        val environmentOverride = GhidraTargetOverride(
            source = GhidraTargetOverrideSource.ENVIRONMENT,
            configuredValue = "/example/ghidra-12.1.4",
            installationPath = Path.of("/example/ghidra-12.1.4"),
            propertiesFile = null,
        )

        val plan = GhidraTargetMigrationPlanner.plan(
            snapshot = GhidraProjectTargetSnapshot(
                projectRoot = projectRoot,
                localInstallationPath = null,
                facetInstallationPath = null,
                wrapper = wrapperInfo(
                    projectRoot = projectRoot,
                    version = "9.6",
                ),
                environmentOverride = environmentOverride,
                userGradlePropertiesOverride = null,
            ),
            selectedInstallation = selectedInstallation,
        )

        assertTrue(plan.requiresAcknowledgement)
        assertTrue(
            plan.issues.any {
                it.code ==
                    GhidraTargetMigrationIssueCode.ENVIRONMENT_TARGET_OVERRIDE &&
                    it.severity ==
                    GhidraTargetMigrationIssueSeverity.ACKNOWLEDGEMENT_REQUIRED
            }
        )
    }

    @Test
    fun `global user Gradle property requires acknowledgement even when matching target`() {
        val projectRoot = Files.createTempDirectory("ghidra-migration-project")
        val selectedInstallation = installation(
            path = Path.of("/example/ghidra-12.2"),
            version = "12.2",
            javaVersion = 25,
            gradleMinimum = "9.1",
        )

        val userOverride = GhidraTargetOverride(
            source = GhidraTargetOverrideSource.USER_GRADLE_PROPERTIES,
            configuredValue = "/example/ghidra-12.2",
            installationPath = selectedInstallation.configuredPath,
            propertiesFile = Path.of("/example/.gradle/gradle.properties"),
        )

        val plan = GhidraTargetMigrationPlanner.plan(
            snapshot = GhidraProjectTargetSnapshot(
                projectRoot = projectRoot,
                localInstallationPath = null,
                facetInstallationPath = null,
                wrapper = wrapperInfo(
                    projectRoot = projectRoot,
                    version = "9.6",
                ),
                environmentOverride = null,
                userGradlePropertiesOverride = userOverride,
            ),
            selectedInstallation = selectedInstallation,
        )

        assertTrue(plan.requiresAcknowledgement)
        assertTrue(
            plan.issues.any {
                it.code ==
                    GhidraTargetMigrationIssueCode.USER_GRADLE_PROPERTIES_TARGET_OVERRIDE
            }
        )
    }

    @Test
    fun `reports incompatible Project SDK Gradle JVM and explicit launcher JRE`() {
        val projectRoot = Files.createTempDirectory("ghidra-migration-project")
        val selectedInstallation = installation(
            path = Path.of("/example/ghidra-12.2"),
            version = "12.2",
            javaVersion = 25,
            gradleMinimum = "9.1",
        )

        val plan = GhidraTargetMigrationPlanner.plan(
            snapshot = GhidraProjectTargetSnapshot(
                projectRoot = projectRoot,
                localInstallationPath = selectedInstallation.configuredPath,
                facetInstallationPath = selectedInstallation.configuredPath,
                wrapper = wrapperInfo(
                    projectRoot = projectRoot,
                    version = "9.6",
                ),
                environmentOverride = null,
                userGradlePropertiesOverride = null,
                projectSdk = javaRuntime(
                    label = "Project SDK",
                    featureVersion = 21,
                ),
                explicitLauncherJre = javaRuntime(
                    label = "Ghidra Launcher JRE",
                    featureVersion = 21,
                ),
                gradleJvm = javaRuntime(
                    label = "Gradle JVM",
                    featureVersion = 21,
                ),
            ),
            selectedInstallation = selectedInstallation,
        )

        assertEquals(
            setOf(
                GhidraTargetMigrationIssueCode.PROJECT_SDK_INCOMPATIBLE,
                GhidraTargetMigrationIssueCode
                    .EXPLICIT_LAUNCHER_JRE_INCOMPATIBLE,
                GhidraTargetMigrationIssueCode.GRADLE_JVM_INCOMPATIBLE,
            ),
            plan.issues
                .map(GhidraTargetMigrationIssue::code)
                .filter { code ->
                    code in setOf(
                        GhidraTargetMigrationIssueCode.PROJECT_SDK_INCOMPATIBLE,
                        GhidraTargetMigrationIssueCode
                            .EXPLICIT_LAUNCHER_JRE_INCOMPATIBLE,
                        GhidraTargetMigrationIssueCode.GRADLE_JVM_INCOMPATIBLE,
                    )
                }
                .toSet(),
        )
    }

    @Test
    fun `accepts Java 25 runtimes for Ghidra 12 2`() {
        val projectRoot = Files.createTempDirectory("ghidra-migration-project")
        val selectedInstallation = installation(
            path = Path.of("/example/ghidra-12.2"),
            version = "12.2",
            javaVersion = 25,
            gradleMinimum = "9.1",
        )

        val plan = GhidraTargetMigrationPlanner.plan(
            snapshot = GhidraProjectTargetSnapshot(
                projectRoot = projectRoot,
                localInstallationPath = selectedInstallation.configuredPath,
                facetInstallationPath = selectedInstallation.configuredPath,
                wrapper = wrapperInfo(
                    projectRoot = projectRoot,
                    version = "9.6",
                ),
                environmentOverride = null,
                userGradlePropertiesOverride = null,
                projectSdk = javaRuntime(
                    label = "Project SDK",
                    featureVersion = 25,
                ),
                explicitLauncherJre = javaRuntime(
                    label = "Ghidra Launcher JRE",
                    featureVersion = 25,
                ),
                gradleJvm = javaRuntime(
                    label = "Gradle JVM",
                    featureVersion = 25,
                ),
            ),
            selectedInstallation = selectedInstallation,
        )

        assertFalse(
            plan.issues.any { issue ->
                issue.code in setOf(
                    GhidraTargetMigrationIssueCode.PROJECT_SDK_INCOMPATIBLE,
                    GhidraTargetMigrationIssueCode
                        .EXPLICIT_LAUNCHER_JRE_INCOMPATIBLE,
                    GhidraTargetMigrationIssueCode.GRADLE_JVM_INCOMPATIBLE,
                )
            }
        )
    }

    private fun javaRuntime(
        label: String,
        featureVersion: Int?,
    ): GhidraJavaRuntimeInfo =
        GhidraJavaRuntimeInfo(
            label = label,
            featureVersion = featureVersion,
            homePath = null,
        )

    private fun installation(
        path: Path,
        version: String,
        javaVersion: Int,
        gradleMinimum: String,
    ): GhidraInstallation =
        GhidraInstallation(
            configuredPath = path,
            canonicalPath = path,
            version = version,
            releaseName = "PUBLIC",
            javaCompilerVersion = javaVersion,
            javaMinimumVersion = javaVersion,
            javaMaximumVersion = null,
            gradleMinimumVersion = gradleMinimum,
            gradleMaximumVersion = null,
        )

    private fun wrapperInfo(
        projectRoot: Path,
        version: String,
    ): GhidraGradleWrapperInfo =
        GhidraGradleWrapperInfo(
            propertiesFile =
                projectRoot.resolve("gradle/wrapper/gradle-wrapper.properties"),
            distributionUrl =
                "https://services.gradle.org/distributions/gradle-$version-bin.zip",
            version = GhidraGradleVersion.parseOrNull(version),
        )
}