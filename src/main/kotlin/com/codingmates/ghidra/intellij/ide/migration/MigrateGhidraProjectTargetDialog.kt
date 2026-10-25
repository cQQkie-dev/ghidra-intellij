package com.codingmates.ghidra.intellij.ide.migration

import com.codingmates.ghidra.intellij.ide.installation.GhidraInstallation
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.panel
import java.awt.Dimension
import java.nio.file.Path
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

        targetInstallationComboBox.addActionListener {
            acknowledgementCheckBox.isSelected = false
            updatePlan()
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
            "\nThis workflow updates only the local Ghidra target, Ghidra facet, " +
                "and Processor/SLEIGH Ant target where applicable. It does not migrate " +
                "Ghidra APIs, source code, dependencies, SDKs, Gradle JVM settings, " +
                "or Gradle Wrapper versions."
        )
    }
}