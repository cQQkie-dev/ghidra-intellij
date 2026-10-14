package com.codingmates.ghidra.intellij.ide.extension

import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.panel
import java.nio.file.Path
import javax.swing.JComboBox
import javax.swing.JComponent
import javax.swing.JTextField
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener

class ImportGhidraExtensionSourceDialog(
    project: Project?,
    targetInstallations: List<Path>,
    defaultInstallation: Path?,
) : DialogWrapper(project, true) {

    private val sourceDirectoryField = JTextField()
    private val targetInstallationComboBox =
        JComboBox(targetInstallations.map(Path::toString).toTypedArray())

    init {
        title = "Import Ghidra Extension Source"

        defaultInstallation?.toString()?.let { defaultPath ->
            targetInstallationComboBox.selectedItem = defaultPath
        }

        init()
        initValidation()

        sourceDirectoryField.document.addDocumentListener(
            object : DocumentListener {
                override fun insertUpdate(event: DocumentEvent) = revalidateDialog()
                override fun removeUpdate(event: DocumentEvent) = revalidateDialog()
                override fun changedUpdate(event: DocumentEvent) = revalidateDialog()
            }
        )
    }

    fun sourceDirectory(): Path =
        Path.of(sourceDirectoryField.text.trim())

    fun targetInstallationPath(): Path =
        Path.of(targetInstallationComboBox.selectedItem as String)

    private fun revalidateDialog() {
        initValidation()
    }

    override fun createCenterPanel(): JComponent = panel {
        row("Extension source directory:") {
            cell(sourceDirectoryField)
                .align(AlignX.FILL)

            button("Browse...") {
                val descriptor =
                    FileChooserDescriptorFactory.createSingleFolderDescriptor()

                descriptor.title = "Select Existing Ghidra Extension Source Directory"

                FileChooser.chooseFile(descriptor, null, null)?.let {
                    sourceDirectoryField.text = it.path
                }
            }
        }

        row("Target Ghidra installation:") {
            cell(targetInstallationComboBox)
                .align(AlignX.FILL)
        }
    }

    override fun doValidate(): ValidationInfo? {
        if (sourceDirectoryField.text.isBlank()) {
            return ValidationInfo(
                "Extension source directory must be specified.",
                sourceDirectoryField,
            )
        }

        return try {
            GhidraExtensionSourceImporter.inspect(sourceDirectory())
            null
        }
        catch (e: GhidraExtensionSourceImportException) {
            ValidationInfo(
                e.message ?: "Invalid Ghidra extension source directory.",
                sourceDirectoryField,
            )
        }
        catch (_: Exception) {
            ValidationInfo(
                "Invalid extension source directory.",
                sourceDirectoryField,
            )
        }
    }
}