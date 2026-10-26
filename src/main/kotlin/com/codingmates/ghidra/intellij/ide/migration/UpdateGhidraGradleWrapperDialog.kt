package com.codingmates.ghidra.intellij.ide.migration

import com.codingmates.ghidra.intellij.ide.installation.GhidraInstallation
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.panel
import javax.swing.JComboBox
import javax.swing.JComponent

/**
 * Selects the Java SDK used to update a project's Gradle Wrapper for its selected Ghidra target.
 */
class UpdateGhidraGradleWrapperDialog(
    project: Project,
    private val installation: GhidraInstallation,
    private val currentWrapper: GhidraGradleWrapperInfo?,
    private val targetGradleVersion: GhidraGradleVersion,
    compatibleSdks: List<GhidraCompatibleSdk>,
) : DialogWrapper(project, true) {

    private val compatibleSdkComboBox =
        JComboBox(compatibleSdks.toTypedArray())

    init {
        title = "Update Ghidra Gradle Wrapper"
        init()
        initValidation()
    }

    fun selectedCompatibleSdk(): GhidraCompatibleSdk? =
        compatibleSdkComboBox.selectedItem as? GhidraCompatibleSdk

    override fun createCenterPanel(): JComponent = panel {
        row {
            text(
                buildString {
                    append("<html>")
                    append(
                        "Selected Ghidra target: ${installation.version} " +
                            "${installation.releaseName}<br/>"
                    )

                    val currentVersion = currentWrapper?.version

                    if (currentVersion != null) {
                        append("Current Gradle Wrapper: $currentVersion<br/>")
                    }
                    else if (currentWrapper == null) {
                        append("Current Gradle Wrapper: not found<br/>")
                    }
                    else {
                        append("Current Gradle Wrapper: version unavailable<br/>")
                    }

                    append(
                        "Required Gradle Wrapper version: " +
                            "$targetGradleVersion<br/><br/>"
                    )
                    append(
                        "The configured external Gradle executable will update this " +
                            "project Wrapper. The selected Java SDK is used only for " +
                            "this update."
                    )
                    append("</html>")
                }
            )
        }

        row("Java SDK for Wrapper update:") {
            cell(compatibleSdkComboBox)
                .align(AlignX.FILL)
        }
    }

    override fun doValidate(): ValidationInfo? {
        if (selectedCompatibleSdk() == null) {
            return ValidationInfo(
                "Select a compatible Java SDK before updating the Gradle Wrapper.",
                compatibleSdkComboBox,
            )
        }

        return null
    }
}