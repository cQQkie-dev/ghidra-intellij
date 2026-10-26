package com.codingmates.ghidra.intellij.ide.migration

import com.codingmates.ghidra.intellij.ide.installation.GhidraInstallation
import java.nio.file.Path

/**
 * Pure migration planner for changing a project's selected Ghidra runtime target.
 *
 * This class only evaluates state. It never writes files, changes facets, invokes Gradle, or
 * accesses IntelliJ Platform services.
 */
object GhidraTargetMigrationPlanner {

    fun plan(
        snapshot: GhidraProjectTargetSnapshot,
        selectedInstallation: GhidraInstallation,
    ): GhidraTargetMigrationPlan {
        val selectedPath = selectedInstallation.configuredPath
            .toAbsolutePath()
            .normalize()

        val requirements = GhidraTargetRequirements.from(selectedInstallation)

        val issues = buildList {
            addLocalTargetIssues(
                localInstallationPath = snapshot.localInstallationPath,
                selectedInstallationPath = selectedPath,
            )

            addFacetTargetIssues(
                facetInstallationPath = snapshot.facetInstallationPath,
                selectedInstallationPath = selectedPath,
            )

            addLocalFacetConsistencyIssue(
                localInstallationPath = snapshot.localInstallationPath,
                facetInstallationPath = snapshot.facetInstallationPath,
            )

            snapshot.projectSdk?.let { projectSdk ->
                addJavaRuntimeIssues(
                    runtime = projectSdk,
                    requirements = requirements,
                    unavailableCode =
                        GhidraTargetMigrationIssueCode.PROJECT_SDK_UNAVAILABLE,
                    incompatibleCode =
                        GhidraTargetMigrationIssueCode.PROJECT_SDK_INCOMPATIBLE,
                    unavailableMessage =
                        "No Project SDK is configured. Configure a Java SDK compatible with " +
                            "the selected Ghidra target.",
                )
            }

            snapshot.explicitLauncherJre?.let { launcherJre ->
                addJavaRuntimeIssues(
                    runtime = launcherJre,
                    requirements = requirements,
                    unavailableCode =
                        GhidraTargetMigrationIssueCode
                            .EXPLICIT_LAUNCHER_JRE_UNAVAILABLE,
                    incompatibleCode =
                        GhidraTargetMigrationIssueCode
                            .EXPLICIT_LAUNCHER_JRE_INCOMPATIBLE,
                    unavailableMessage =
                        "The Ghidra Launcher has an explicit JRE, but its Java version " +
                            "could not be determined.",
                )
            }

            snapshot.gradleJvm?.let { gradleJvm ->
                addJavaRuntimeIssues(
                    runtime = gradleJvm,
                    requirements = requirements,
                    unavailableCode =
                        GhidraTargetMigrationIssueCode.GRADLE_JVM_UNAVAILABLE,
                    incompatibleCode =
                        GhidraTargetMigrationIssueCode.GRADLE_JVM_INCOMPATIBLE,
                    unavailableMessage =
                        "The linked Gradle JVM could not be determined. Configure a Gradle JVM " +
                            "compatible with the selected Ghidra target.",
                )
            }

            addWrapperIssues(
                wrapper = snapshot.wrapper,
                requirements = requirements,
            )

            addGlobalOverrideIssue(
                override = snapshot.environmentOverride,
                selectedInstallationPath = selectedPath,
            )

            addGlobalOverrideIssue(
                override = snapshot.userGradlePropertiesOverride,
                selectedInstallationPath = selectedPath,
            )
        }

        return GhidraTargetMigrationPlan(
            selectedInstallationPath = selectedPath,
            selectedGhidraVersion = selectedInstallation.version,
            selectedGhidraReleaseName = selectedInstallation.releaseName,
            requirements = requirements,
            issues = issues,
        )
    }

    private fun MutableList<GhidraTargetMigrationIssue>.addLocalTargetIssues(
        localInstallationPath: Path?,
        selectedInstallationPath: Path,
    ) {
        val normalizedLocalPath = localInstallationPath?.normalizePath() ?: return

        if (normalizedLocalPath != selectedInstallationPath) {
            add(
                GhidraTargetMigrationIssue(
                    code = GhidraTargetMigrationIssueCode.LOCAL_TARGET_DIFFERS_FROM_SELECTED,
                    severity = GhidraTargetMigrationIssueSeverity.INFORMATION,
                    message =
                        "Project-local GHIDRA_INSTALL_DIR is $normalizedLocalPath, " +
                            "but the selected target is $selectedInstallationPath.",
                )
            )
        }
    }

    private fun MutableList<GhidraTargetMigrationIssue>.addFacetTargetIssues(
        facetInstallationPath: Path?,
        selectedInstallationPath: Path,
    ) {
        val normalizedFacetPath = facetInstallationPath?.normalizePath() ?: return

        if (normalizedFacetPath != selectedInstallationPath) {
            add(
                GhidraTargetMigrationIssue(
                    code = GhidraTargetMigrationIssueCode.FACET_TARGET_DIFFERS_FROM_SELECTED,
                    severity = GhidraTargetMigrationIssueSeverity.INFORMATION,
                    message =
                        "The Ghidra facet targets $normalizedFacetPath, " +
                            "but the selected target is $selectedInstallationPath.",
                )
            )
        }
    }

    private fun MutableList<GhidraTargetMigrationIssue>.addLocalFacetConsistencyIssue(
        localInstallationPath: Path?,
        facetInstallationPath: Path?,
    ) {
        val normalizedLocalPath = localInstallationPath?.normalizePath() ?: return
        val normalizedFacetPath = facetInstallationPath?.normalizePath() ?: return

        if (normalizedLocalPath != normalizedFacetPath) {
            add(
                GhidraTargetMigrationIssue(
                    code = GhidraTargetMigrationIssueCode.LOCAL_AND_FACET_TARGET_DIFFER,
                    severity = GhidraTargetMigrationIssueSeverity.WARNING,
                    message =
                        "Project-local GHIDRA_INSTALL_DIR ($normalizedLocalPath) and " +
                            "the Ghidra facet target ($normalizedFacetPath) differ.",
                )
            )
        }
    }

    private fun MutableList<GhidraTargetMigrationIssue>.addJavaRuntimeIssues(
        runtime: GhidraJavaRuntimeInfo?,
        requirements: GhidraTargetRequirements,
        unavailableCode: GhidraTargetMigrationIssueCode,
        incompatibleCode: GhidraTargetMigrationIssueCode,
        unavailableMessage: String,
    ) {
        val requiredJavaVersion = requirements.requiredJavaVersion
        val maximumJavaVersion = requirements.javaMaximumVersion

        if (requiredJavaVersion == null && maximumJavaVersion == null) {
            return
        }

        if (runtime?.featureVersion == null) {
            add(
                GhidraTargetMigrationIssue(
                    code = unavailableCode,
                    severity = GhidraTargetMigrationIssueSeverity.WARNING,
                    message = unavailableMessage,
                )
            )
            return
        }

        val featureVersion = runtime.featureVersion

        if (!requirements.isJavaCompatible(featureVersion)) {
            val requiredRange = buildString {
                requiredJavaVersion?.let {
                    append("Java $it or newer")
                }

                maximumJavaVersion?.let {
                    if (isNotEmpty()) {
                        append(", ")
                    }
                    append("Java $it or older")
                }
            }

            add(
                GhidraTargetMigrationIssue(
                    code = incompatibleCode,
                    severity = GhidraTargetMigrationIssueSeverity.WARNING,
                    message =
                        "${runtime.label} uses Java $featureVersion, but the selected " +
                            "Ghidra target requires $requiredRange.",
                )
            )
        }
    }

    private fun MutableList<GhidraTargetMigrationIssue>.addWrapperIssues(
        wrapper: GhidraGradleWrapperInfo?,
        requirements: GhidraTargetRequirements,
    ) {
        if (wrapper == null) {
            add(
                GhidraTargetMigrationIssue(
                    code = GhidraTargetMigrationIssueCode.WRAPPER_NOT_FOUND,
                    severity = GhidraTargetMigrationIssueSeverity.INFORMATION,
                    message =
                        "No Gradle Wrapper was found. Generate or configure a compatible " +
                            "Gradle distribution before building the project.",
                )
            )
            return
        }

        val wrapperVersion = wrapper.version
        if (wrapperVersion == null) {
            add(
                GhidraTargetMigrationIssue(
                    code = GhidraTargetMigrationIssueCode.WRAPPER_VERSION_UNAVAILABLE,
                    severity = GhidraTargetMigrationIssueSeverity.WARNING,
                    message =
                        "The Gradle Wrapper distribution version could not be determined from " +
                            "${wrapper.propertiesFile}.",
                )
            )
            return
        }

        val minimumVersion = requirements.gradleMinimumVersion
        if (minimumVersion != null && wrapperVersion < minimumVersion) {
            add(
                GhidraTargetMigrationIssue(
                    code = GhidraTargetMigrationIssueCode.WRAPPER_GRADLE_TOO_OLD,
                    severity = GhidraTargetMigrationIssueSeverity.WARNING,
                    message =
                        "Gradle Wrapper $wrapperVersion is older than the selected Ghidra " +
                            "minimum Gradle version $minimumVersion.",
                )
            )
        }

        val maximumVersion = requirements.gradleMaximumVersion
        if (maximumVersion != null && wrapperVersion >= maximumVersion) {
            add(
                GhidraTargetMigrationIssue(
                    code = GhidraTargetMigrationIssueCode.WRAPPER_GRADLE_TOO_NEW,
                    severity = GhidraTargetMigrationIssueSeverity.WARNING,
                    message =
                        "Gradle Wrapper $wrapperVersion is at or newer than the selected " +
                            "Ghidra maximum Gradle version $maximumVersion.",
                )
            )
        }
    }

    private fun MutableList<GhidraTargetMigrationIssue>.addGlobalOverrideIssue(
        override: GhidraTargetOverride?,
        selectedInstallationPath: Path,
    ) {
        if (override == null) {
            return
        }

        val sourceDescription = when (override.source) {
            GhidraTargetOverrideSource.ENVIRONMENT ->
                "the IntelliJ process environment"

            GhidraTargetOverrideSource.USER_GRADLE_PROPERTIES ->
                override.propertiesFile?.toString()
                    ?: "Gradle user-home properties"
        }

        val selectedPathNote = if (
            override.installationPath?.normalizePath() == selectedInstallationPath
        ) {
            " It currently matches the selected target, but it can still override " +
                "other projects."
        }
        else {
            " It may cause normal Gradle sync/build operations to use a different " +
                "Ghidra installation than the selected project target."
        }

        val issueCode = when (override.source) {
            GhidraTargetOverrideSource.ENVIRONMENT ->
                GhidraTargetMigrationIssueCode.ENVIRONMENT_TARGET_OVERRIDE

            GhidraTargetOverrideSource.USER_GRADLE_PROPERTIES ->
                GhidraTargetMigrationIssueCode.USER_GRADLE_PROPERTIES_TARGET_OVERRIDE
        }

        add(
            GhidraTargetMigrationIssue(
                code = issueCode,
                severity =
                    GhidraTargetMigrationIssueSeverity.ACKNOWLEDGEMENT_REQUIRED,
                message =
                    "Global GHIDRA_INSTALL_DIR override detected in $sourceDescription: " +
                        "${override.configuredValue}.$selectedPathNote",
            )
        )
    }

    private fun Path.normalizePath(): Path =
        toAbsolutePath().normalize()
}

/**
 * Result of evaluating a requested Ghidra project target change.
 */
data class GhidraTargetMigrationPlan(
    val selectedInstallationPath: Path,
    val selectedGhidraVersion: String,
    val selectedGhidraReleaseName: String,
    val requirements: GhidraTargetRequirements,
    val issues: List<GhidraTargetMigrationIssue>,
) {
    val requiresAcknowledgement: Boolean
        get() = issues.any {
            it.severity ==
                GhidraTargetMigrationIssueSeverity.ACKNOWLEDGEMENT_REQUIRED
        }
}