package com.codingmates.ghidra.intellij.ide.runConfiguration

import com.intellij.execution.configuration.EnvironmentVariablesComponent
import com.intellij.openapi.options.SettingsEditor
import com.intellij.openapi.project.Project
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.panel
import javax.swing.JCheckBox
import javax.swing.JComponent
import javax.swing.JTextField

class PyGhidraGuiConfigurationEditor(
    project: Project,
) : SettingsEditor<PyGhidraGuiConfiguration>() {

    private val workingDirectoryField = JTextField()
    private val loadExtensionProjectCheckBox = JCheckBox()
    private val environmentVariables = EnvironmentVariablesComponent(project)

    private val configurationPanel = panel {
        row("Working directory:") {
            cell(workingDirectoryField)
                .align(AlignX.FILL)
        }

        row("Load current extension project:") {
            cell(loadExtensionProjectCheckBox)
        }

        row("Environment variables:") {
            cell(environmentVariables)
                .align(AlignX.FILL)
        }

        row {
            comment(
                "GHIDRA_INSTALL_DIR is always taken from the selected Ghidra facet."
            )
        }
    }

    override fun applyEditorTo(configuration: PyGhidraGuiConfiguration) {
        configuration.setWorkingDirectory(
            workingDirectoryField.text.trim().ifBlank { null }
        )
        configuration.setLoadCurrentExtensionProject(
            loadExtensionProjectCheckBox.isSelected
        )

        environmentVariables.apply(configuration)
        configuration.checkConfiguration()
    }

    override fun createEditor(): JComponent =
        configurationPanel

    override fun resetEditorFrom(configuration: PyGhidraGuiConfiguration) {
        workingDirectoryField.text =
            configuration.getWorkingDirectory().orEmpty()

        loadExtensionProjectCheckBox.isSelected =
            configuration.loadsCurrentExtensionProject()

        environmentVariables.reset(configuration)
    }
}