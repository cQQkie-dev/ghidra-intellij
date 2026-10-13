package com.codingmates.ghidra.intellij.ide.script

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.panel
import java.awt.Dimension
import java.nio.file.Path
import java.nio.file.Files
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener
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
        initValidation()

        val validationListener = object : DocumentListener {
            override fun insertUpdate(event: DocumentEvent) = revalidateDialog()
            override fun removeUpdate(event: DocumentEvent) = revalidateDialog()
            override fun changedUpdate(event: DocumentEvent) = revalidateDialog()
        }

        scriptDirectoryField.document.addDocumentListener(validationListener)
        scriptNameField.document.addDocumentListener(validationListener)

        languageComboBox.addActionListener {
            revalidateDialog()
        }
    }

    fun scriptDirectory(): Path = Path.of(scriptDirectoryField.text.trim())

    fun scriptSpec(): GhidraScriptSpec {
        val language = languageComboBox.selectedItem as GhidraScriptLanguage

        return GhidraScriptSpec(
            name = normalizedScriptName(language),
            language = language,
            author = authorField.text.trim(),
            category = categoryField.text.trim(),
            description = descriptionField.text.trim(),
        )
    }

    private fun normalizedScriptName(
        language: GhidraScriptLanguage,
    ): String {
        val name = scriptNameField.text.trim()
        val suffix = ".${language.extension}"

        return if (name.endsWith(suffix, ignoreCase = true)) {
            name.dropLast(suffix.length)
        }
        else {
            name
        }
    }

    private fun revalidateDialog() {
        initValidation()
    }

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
            cell(
                JBScrollPane(descriptionField).apply {
                    preferredSize = Dimension(0, 100)
                }
            ).align(AlignX.FILL)
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
            val scriptDirectory = scriptDirectory()
            val spec = scriptSpec()

            GhidraScriptGenerator.render(spec)

            val targetScriptPath = scriptDirectory.resolve(
                "${spec.name}.${spec.language.extension}"
            )

            if (Files.exists(targetScriptPath)) {
                ValidationInfo(
                    "A script named '${targetScriptPath.fileName}' already exists.",
                    scriptNameField,
                )
            }
            else {
                null
            }
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