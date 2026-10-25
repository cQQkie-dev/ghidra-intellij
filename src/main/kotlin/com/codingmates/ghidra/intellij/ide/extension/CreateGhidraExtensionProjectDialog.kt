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
import javax.swing.JComboBox
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
    targetInstallations: List<Path>,
    defaultInstallation: Path?,
) : DialogWrapper(project, true) {

    private val projectNameField = JTextField()
    private val parentDirectoryField = JTextField(defaultParentDirectory.toString())
    private val targetInstallationComboBox = JComboBox(targetInstallations.map(Path::toString).toTypedArray())
    private val pluginTemplateCheckbox = JCheckBox("Plugin - extends the Ghidra user interface")
    private val analyzerTemplateCheckbox = JCheckBox("Analyzer - extends Ghidra analysis")
    private val loaderTemplateCheckbox = JCheckBox("Loader - imports a binary file format")
    private val fileSystemTemplateCheckbox = JCheckBox(
        "File System - opens a file system format for browsing or batch import"
    )
    private val exporterTemplateCheckbox = JCheckBox("Exporter - exports a Ghidra program")
    private val processorTemplateCheckbox = JCheckBox(
        "Processor/SLEIGH - defines a processor architecture and instruction language"
    )

    private val templatesPanel = JPanel().apply {
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
        add(pluginTemplateCheckbox)
        add(analyzerTemplateCheckbox)
        add(loaderTemplateCheckbox)
        add(fileSystemTemplateCheckbox)
        add(exporterTemplateCheckbox)
        add(processorTemplateCheckbox)
    }

    init {
        title = "Create Ghidra Extension Project"
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

    fun projectSpec(
        skeletonTemplatePath: Path,
    ): GhidraExtensionProjectSpec =
        GhidraExtensionProjectSpec(
            name = projectNameField.text.trim(),
            projectParentDirectory = Path.of(parentDirectoryField.text.trim()),
            skeletonTemplatePath = skeletonTemplatePath,
            ghidraInstallationPath = targetInstallationPath(),
            templates = selectedTemplates(),
        )

    fun targetInstallationPath(): Path =
        Path.of(targetInstallationComboBox.selectedItem as String)

    private fun selectedTemplates(): Set<GhidraExtensionTemplate> =
        buildSet {
            if (pluginTemplateCheckbox.isSelected) {
                add(GhidraExtensionTemplate.PLUGIN)
            }

            if (analyzerTemplateCheckbox.isSelected) {
                add(GhidraExtensionTemplate.ANALYZER)
            }

            if (loaderTemplateCheckbox.isSelected) {
                add(GhidraExtensionTemplate.LOADER)
            }

            if (fileSystemTemplateCheckbox.isSelected) {
                add(GhidraExtensionTemplate.FILE_SYSTEM)
            }

            if (exporterTemplateCheckbox.isSelected) {
                add(GhidraExtensionTemplate.EXPORTER)
            }

            if (processorTemplateCheckbox.isSelected) {
                add(GhidraExtensionTemplate.PROCESSOR)
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

        row("Target Ghidra installation:") {
            cell(targetInstallationComboBox)
                .align(AlignX.FILL)
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