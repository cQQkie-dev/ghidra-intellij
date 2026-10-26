package com.codingmates.ghidra.intellij.ide.migration

import com.codingmates.ghidra.intellij.ide.installation.GhidraInstallation
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.panel
import java.awt.Dimension
import java.nio.file.Path
import javax.swing.DefaultComboBoxModel
import javax.swing.JCheckBox
import javax.swing.JComboBox
import javax.swing.JComponent
import javax.swing.JScrollPane
import javax.swing.JTextArea

/**
 * Selects a registered Ghidra target and presents the Stage-1 migration diagnostics.
 *
 * This dialog reports configuration incompatibilities but intentionally does not yet modify
 * Project SDK, linked Gradle JVM, launcher JRE, wrapper version, or Gradle sync settings.
 */
class MigrateGhidraProjectTargetDialog(
    project: Project,
    private val targetInstallations: List<GhidraInstallation>,
    defaultInstallation: Path?,
    private val snapshot: GhidraProjectTargetSnapshot,
) : DialogWrapper(project, true) {

    private val installationByPath =
        targetInstallations.associateBy { installation ->
            installation.configuredPath.toString()
        }

    private val targetInstallationComboBox =
        JComboBox(installationByPath.keys.toTypedArray())

    private val diagnosticsArea = JTextArea().apply {
        isEditable = false
        isFocusable = false
        lineWrap = true
        wrapStyleWord = true
        rows = 15
        preferredSize = Dimension(680, 300)
    }

    private val acknowledgementCheckBox = JCheckBox(
        "I understand that the global override may prevent normal Gradle sync " +
            "from using the selected project target."
    )

    private val compatibleSdkComboBox = JComboBox<GhidraCompatibleSdk>()

    private val updateProjectSdkCheckBox = JCheckBox(
        "Set Project SDK to selected compatible Java SDK"
    )

    private val updateGradleJvmCheckBox = JCheckBox(
        "Set linked Gradle JVM to selected compatible Java SDK"
    )

    private val updateLauncherJreCheckBox = JCheckBox(
        "Set explicit Ghidra Launcher JRE to selected compatible Java SDK"
    )

    private val reloadGradleCheckBox = JCheckBox(
        "Reload Gradle after applying the migration"
    )

    private var currentPlan: GhidraTargetMigrationPlan =
        planForSelectedInstallation()

    init {
        title = "Migrate Ghidra Project Target"

        defaultInstallation
            ?.toAbsolutePath()
            ?.normalize()
            ?.toString()
            ?.takeIf { it in installationByPath }
            ?.let { defaultPath ->
                targetInstallationComboBox.selectedItem = defaultPath
            }

        updatePlan()
        updateCompatibleSdkOptions()
        updateGradleReloadOption()

        updateGradleJvmCheckBox.addActionListener {
            updateGradleReloadOption()
        }

        targetInstallationComboBox.addActionListener {
            acknowledgementCheckBox.isSelected = false
            updatePlan()
            updateCompatibleSdkOptions()
            updateGradleReloadOption()
        }

        init()
        initValidation()
    }

    fun selectedInstallation(): GhidraInstallation =
        installationByPath[
            targetInstallationComboBox.selectedItem as String
        ] ?: error("No Ghidra target installation is selected.")

    fun selectedPlan(): GhidraTargetMigrationPlan =
        currentPlan

    fun selectedCompatibleSdk(): GhidraCompatibleSdk? =
        compatibleSdkComboBox.selectedItem as? GhidraCompatibleSdk

    fun shouldUpdateProjectSdk(): Boolean =
        updateProjectSdkCheckBox.isSelected

    fun shouldUpdateGradleJvm(): Boolean =
        updateGradleJvmCheckBox.isSelected

    fun shouldUpdateLauncherJre(): Boolean =
        updateLauncherJreCheckBox.isSelected

    fun shouldReloadGradle(): Boolean =
        reloadGradleCheckBox.isSelected

    private fun planForSelectedInstallation(): GhidraTargetMigrationPlan =
        GhidraTargetMigrationPlanner.plan(
            snapshot = snapshot,
            selectedInstallation = installationByPath[
                targetInstallationComboBox.selectedItem as String
            ] ?: error("No Ghidra target installation is selected."),
        )

    private fun updatePlan() {
        currentPlan = planForSelectedInstallation()
        diagnosticsArea.text = formatPlan(currentPlan)

        val requiresAcknowledgement = currentPlan.requiresAcknowledgement
        acknowledgementCheckBox.isVisible = requiresAcknowledgement

        if (!requiresAcknowledgement) {
            acknowledgementCheckBox.isSelected = false
        }
    }

    override fun createCenterPanel(): JComponent = panel {
        row("Target Ghidra installation:") {
            cell(targetInstallationComboBox)
                .align(AlignX.FILL)
        }

        row {
            cell(JScrollPane(diagnosticsArea))
                .align(AlignX.FILL)
        }

        row {
            cell(acknowledgementCheckBox)
                .align(AlignX.FILL)
        }

        group("Optional Java Configuration Updates") {
            row("Compatible Java SDK:") {
                cell(compatibleSdkComboBox)
                    .align(AlignX.FILL)
            }

            row {
                cell(updateProjectSdkCheckBox)
                    .align(AlignX.FILL)
            }

            row {
                cell(updateGradleJvmCheckBox)
                    .align(AlignX.FILL)
            }

            row {
                cell(updateLauncherJreCheckBox)
                    .align(AlignX.FILL)
            }

            row {
                cell(reloadGradleCheckBox)
                    .align(AlignX.FILL)
            }
        }
    }

    private fun updateCompatibleSdkOptions() {
        val compatibleSdks = GhidraCompatibleSdkDiscovery.discover(
            currentPlan.requirements
        )

        compatibleSdkComboBox.model = DefaultComboBoxModel(
            compatibleSdks.toTypedArray()
        )

        val hasCompatibleSdk = compatibleSdks.isNotEmpty()

        compatibleSdkComboBox.isEnabled = hasCompatibleSdk

        updateProjectSdkCheckBox.isVisible =
            hasCompatibleSdk &&
                hasIssue(
                    GhidraTargetMigrationIssueCode.PROJECT_SDK_UNAVAILABLE,
                    GhidraTargetMigrationIssueCode.PROJECT_SDK_INCOMPATIBLE,
                )

        updateGradleJvmCheckBox.isVisible =
            hasCompatibleSdk &&
                hasCompatibleGradleWrapper() &&
                hasIssue(
                    GhidraTargetMigrationIssueCode.GRADLE_JVM_UNAVAILABLE,
                    GhidraTargetMigrationIssueCode.GRADLE_JVM_INCOMPATIBLE,
                )

        updateLauncherJreCheckBox.isVisible =
            hasCompatibleSdk &&
                hasIssue(
                    GhidraTargetMigrationIssueCode
                        .EXPLICIT_LAUNCHER_JRE_UNAVAILABLE,
                    GhidraTargetMigrationIssueCode
                        .EXPLICIT_LAUNCHER_JRE_INCOMPATIBLE,
                )

        if (!hasCompatibleSdk) {
            updateProjectSdkCheckBox.isSelected = false
            updateGradleJvmCheckBox.isSelected = false
            updateLauncherJreCheckBox.isSelected = false
        }
        else {
            updateProjectSdkCheckBox.isSelected =
                updateProjectSdkCheckBox.isVisible

            updateGradleJvmCheckBox.isSelected =
                updateGradleJvmCheckBox.isVisible

            updateLauncherJreCheckBox.isSelected =
                updateLauncherJreCheckBox.isVisible
        }
    }

    /**
     * Gradle versions below the selected Ghidra minimum may not support the target Java version.
     * For example, Gradle 8.13 cannot run on Java 25. Do not offer a Gradle JVM upgrade until the
     * project's Wrapper is within the selected Ghidra Gradle compatibility range.
     */
    private fun hasCompatibleGradleWrapper(): Boolean =
        currentPlan.issues.none { issue ->
            issue.code in setOf(
                GhidraTargetMigrationIssueCode.WRAPPER_NOT_FOUND,
                GhidraTargetMigrationIssueCode.WRAPPER_VERSION_UNAVAILABLE,
                GhidraTargetMigrationIssueCode.WRAPPER_GRADLE_TOO_OLD,
                GhidraTargetMigrationIssueCode.WRAPPER_GRADLE_TOO_NEW,
            )
        }

    private fun updateGradleReloadOption() {
        val canReload = GhidraGradleReloadEligibility.canReload(
            plan = currentPlan,
            willUpdateGradleJvm = updateGradleJvmCheckBox.isSelected,
        )

        reloadGradleCheckBox.isVisible = canReload
        reloadGradleCheckBox.isEnabled = canReload

        if (canReload) {
            reloadGradleCheckBox.isSelected = true
        }
        else {
            reloadGradleCheckBox.isSelected = false
        }
    }

    private fun hasIssue(
        vararg issueCodes: GhidraTargetMigrationIssueCode,
    ): Boolean =
        currentPlan.issues.any { issue ->
            issue.code in issueCodes
        }

    override fun doValidate(): ValidationInfo? {
        if (
            currentPlan.requiresAcknowledgement &&
            !acknowledgementCheckBox.isSelected
        ) {
            return ValidationInfo(
                "Acknowledge the global target override before migrating.",
                acknowledgementCheckBox,
            )
        }

        if (
            (updateProjectSdkCheckBox.isSelected ||
                updateGradleJvmCheckBox.isSelected ||
                updateLauncherJreCheckBox.isSelected) &&
            selectedCompatibleSdk() == null
        ) {
            return ValidationInfo(
                "Select a compatible Java SDK before applying Java configuration updates.",
                compatibleSdkComboBox,
            )
        }

        return null
    }

    private fun formatPlan(
        plan: GhidraTargetMigrationPlan,
    ): String = buildString {
        append(
            "Selected target: Ghidra ${plan.selectedGhidraVersion} " +
                "${plan.selectedGhidraReleaseName}\n\n"
        )

        append("Target requirements:\n")

        plan.requirements.requiredJavaVersion?.let { javaVersion ->
            append("• Java $javaVersion or newer\n")
        }

        plan.requirements.javaMaximumVersion?.let { javaVersion ->
            append("• Java $javaVersion or older\n")
        }

        plan.requirements.gradleMinimumVersion?.let { gradleVersion ->
            append("• Gradle $gradleVersion or newer\n")
        }

        plan.requirements.gradleMaximumVersion?.let { gradleVersion ->
            append("• Gradle $gradleVersion or older\n")
        }

        if (plan.issues.isEmpty()) {
            append("\nNo target consistency issues were detected.")
            return@buildString
        }

        append("\nDetected configuration:\n")

        plan.issues.forEach { issue ->
            val label = when (issue.severity) {
                GhidraTargetMigrationIssueSeverity.INFORMATION -> "Info"
                GhidraTargetMigrationIssueSeverity.WARNING -> "Warning"
                GhidraTargetMigrationIssueSeverity.ACKNOWLEDGEMENT_REQUIRED ->
                    "Acknowledgement required"
            }

            append("• [$label] ${issue.message}\n")
        }

        append(
            "\nThis workflow updates the local Ghidra target, Ghidra facet, " +
                "Processor/SLEIGH Ant target where applicable, and explicitly selected " +
                "Java SDK settings. It does not migrate Ghidra APIs, source code, " +
                "dependencies, Gradle Wrapper versions, or Gradle build logic."
        )
    }
}