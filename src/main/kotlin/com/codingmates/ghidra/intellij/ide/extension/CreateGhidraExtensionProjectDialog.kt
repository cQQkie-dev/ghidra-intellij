package com.codingmates.ghidra.intellij.ide.extension

import com.codingmates.ghidra.intellij.ide.extension.GhidraExtensionTemplate
import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.panel
import java.nio.file.Files
import java.nio.file.Path
import javax.swing.JComponent
import javax.swing.JCheckBox
import javax.swing.BoxLayout
import javax.swing.JPanel
import javax.swing.JTextField
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener

class CreateGhidraExtensionProjectDialog(
    project: Project?,
    private val defaultParentDirectory: Path,
) : DialogWrapper(project, true) {

    private val projectNameField = JTextField()
    private val parentDirectoryField = JTextField(defaultParentDirectory.toString())
    private val pluginTemplateCheckbox = JCheckBox("Plugin - extends the Ghidra user interface")
    private val analyzerTemplateCheckbox = JCheckBox("Analyzer - extends Ghidra analysis")

    private val templatesPanel = JPanel().apply {
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
        add(pluginTemplateCheckbox)
        add(analyzerTemplateCheckbox)
    }

    init {
        title = "Create Ghidra Extension Project"
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

    fun projectSpec(
        skeletonTemplatePath: Path,
        ghidraInstallationPath: Path,
    ): GhidraExtensionProjectSpec =
        GhidraExtensionProjectSpec(
            name = projectNameField.text.trim(),
            projectParentDirectory = Path.of(parentDirectoryField.text.trim()),
            skeletonTemplatePath = skeletonTemplatePath,
            ghidraInstallationPath = ghidraInstallationPath,
            templates = selectedTemplates(),
        )

    private fun selectedTemplates(): Set<GhidraExtensionTemplate> =
        buildSet {
            if (pluginTemplateCheckbox.isSelected) {
                add(GhidraExtensionTemplate.PLUGIN)
            }

            if (analyzerTemplateCheckbox.isSelected) {
                add(GhidraExtensionTemplate.ANALYZER)
            }
        }

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

                descriptor.title = "Select Ghidra Extension Project Directory"

                FileChooser.chooseFile(descriptor, null, null)?.let { selectedDirectory ->
                    parentDirectoryField.text = selectedDirectory.path
                }
            }
        }

        row("Templates:") {
            cell(templatesPanel)
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