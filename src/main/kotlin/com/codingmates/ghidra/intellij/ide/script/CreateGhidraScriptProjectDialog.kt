package com.codingmates.ghidra.intellij.ide.script

import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.panel
import java.nio.file.Files
import java.nio.file.Path
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener
import javax.swing.JComboBox
import javax.swing.JComponent
import javax.swing.JCheckBox
import javax.swing.JTextField

class CreateGhidraScriptProjectDialog(
    project: Project?,
    private val defaultParentDirectory: Path,
    targetInstallations: List<Path>,
    defaultInstallation: Path?,
) : DialogWrapper(project, true) {

    private val projectNameField = JTextField()
    private val parentDirectoryField = JTextField(defaultParentDirectory.toString())
    private val targetInstallationComboBox =
        JComboBox(targetInstallations.map(Path::toString).toTypedArray())
    private val sharedUserScriptDirectoryCheckbox = JCheckBox(
        "Use shared user script directory: ${
            Path.of(System.getProperty("user.home"), "ghidra_scripts")
        }"
    )
    

    init {
        title = "Create Ghidra Script Project"
        defaultInstallation?.toString()?.let { defaultPath ->
            targetInstallationComboBox.selectedItem = defaultPath
        }
        init()
        initValidation()

        val validationListener = object : DocumentListener {
            override fun insertUpdate(event: DocumentEvent) = revalidateDialog()
            override fun removeUpdate(event: DocumentEvent) = revalidateDialog()
            override fun changedUpdate(event: DocumentEvent) = revalidateDialog()
        }

        projectNameField.document.addDocumentListener(validationListener)
        parentDirectoryField.document.addDocumentListener(validationListener)
    }

    fun projectSpec(): GhidraScriptProjectSpec =
        GhidraScriptProjectSpec(
            name = projectNameField.text.trim(),
            projectParentDirectory = Path.of(parentDirectoryField.text.trim()),
            ghidraInstallationPath = targetInstallationPath(),
            sharedScriptDirectory =
                if (sharedUserScriptDirectoryCheckbox.isSelected) {
                    Path.of(System.getProperty("user.home"), "ghidra_scripts")
                }
                else {
                    null
                },
        )

    fun targetInstallationPath(): Path =
        Path.of(targetInstallationComboBox.selectedItem as String)

    private fun revalidateDialog() {
        initValidation()
    }

    override fun createCenterPanel(): JComponent = panel {
        row("Project name:") {
            cell(projectNameField)
                .align(AlignX.FILL)
        }

        row("Project directory:") {
            cell(parentDirectoryField)
                .align(AlignX.FILL)

            button("Browse...") {
                val descriptor =
                    FileChooserDescriptorFactory.createSingleFolderDescriptor()

                descriptor.title = "Select Ghidra Script Project Directory"

                FileChooser.chooseFile(descriptor, null, null)?.let { selectedDirectory ->
                    parentDirectoryField.text = selectedDirectory.path
                }
            }
        }

        row("Target Ghidra installation:") {
            cell(targetInstallationComboBox)
                .align(AlignX.FILL)
        }

        row {
            cell(sharedUserScriptDirectoryCheckbox)
                .align(AlignX.FILL)
        }        
    }

    override fun doValidate(): ValidationInfo? {
        if (projectNameField.text.isBlank()) {
            return ValidationInfo(
                "Project name must be specified.",
                projectNameField,
            )
        }

        if (parentDirectoryField.text.isBlank()) {
            return ValidationInfo(
                "Project directory must be specified.",
                parentDirectoryField,
            )
        }

        return try {
            val parentDirectory = Path.of(parentDirectoryField.text.trim())

            if (Files.exists(parentDirectory) && !Files.isDirectory(parentDirectory)) {
                ValidationInfo(
                    "Project directory must be a directory.",
                    parentDirectoryField,
                )
            }
            else {
                null
            }
        }
        catch (_: Exception) {
            ValidationInfo(
                "Invalid project directory.",
                parentDirectoryField,
            )
        }
    }
}