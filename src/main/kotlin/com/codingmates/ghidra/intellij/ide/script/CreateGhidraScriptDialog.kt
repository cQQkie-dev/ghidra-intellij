package com.codingmates.ghidra.intellij.ide.script

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.panel
import java.nio.file.Path
import javax.swing.JComboBox
import javax.swing.JComponent
import javax.swing.JTextField

class CreateGhidraScriptDialog(
    project: Project,
    private val defaultScriptDirectory: Path,
) : DialogWrapper(project, true) {

    private val scriptDirectoryField = JTextField(defaultScriptDirectory.toString())
    private val scriptNameField = JTextField()
    private val languageComboBox = JComboBox(GhidraScriptLanguage.entries.toTypedArray())
    private val authorField = JTextField()
    private val categoryField = JTextField()
    private val descriptionField = JBTextArea(5, 40)

    init {
        title = "Create Ghidra Script"
        isResizable = true
        init()
    }

    fun scriptDirectory(): Path = Path.of(scriptDirectoryField.text.trim())

    fun scriptSpec(): GhidraScriptSpec =
        GhidraScriptSpec(
            name = scriptNameField.text.trim(),
            language = languageComboBox.selectedItem as GhidraScriptLanguage,
            author = authorField.text.trim(),
            category = categoryField.text.trim(),
            description = descriptionField.text.trim(),
        )

    override fun createCenterPanel(): JComponent = panel {
        row("Script directory:") {
            cell(scriptDirectoryField)
                .align(AlignX.FILL)
        }

        row("Script name:") {
            cell(scriptNameField)
                .align(AlignX.FILL)
        }

        row("Language:") {
            cell(languageComboBox)
        }

        row("Author:") {
            cell(authorField)
                .align(AlignX.FILL)
        }

        row("Category:") {
            cell(categoryField)
                .align(AlignX.FILL)
        }

        row("Description:") {
            cell(descriptionField)
                .align(AlignX.FILL)
        }
    }

    override fun doValidate(): ValidationInfo? {
        if (scriptDirectoryField.text.isBlank()) {
            return ValidationInfo("Script directory must be specified.", scriptDirectoryField)
        }

        if (scriptNameField.text.isBlank()) {
            return ValidationInfo("Script name must be specified.", scriptNameField)
        }

        return try {
            GhidraScriptGenerator.render(scriptSpec())
            null
        }
        catch (e: GhidraScriptGenerationException) {
            ValidationInfo(
                e.message ?: "Invalid Ghidra script configuration.",
                scriptNameField,
            )
        }
        catch (e: Exception) {
            ValidationInfo(
                "Invalid script directory.",
                scriptDirectoryField,
            )
        }
    }
}