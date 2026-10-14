package com.codingmates.ghidra.intellij.ide.scripteditor

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.options.ConfigurationException
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.GridLayout
import javax.swing.JCheckBox
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JTextField

class GhidraScriptEditorConfigurable : Configurable {

    private val enabledCheckbox =
        JCheckBox("Enable Script Editor listener")

    private val portField = JTextField()

    private val listenerService: GhidraScriptEditorListenerService
        get() = ApplicationManager.getApplication()
            .getService(GhidraScriptEditorListenerService::class.java)

    override fun getDisplayName(): String = "Ghidra Script Editor"

    override fun createComponent(): JComponent {
        val panel = JPanel(GridLayout(3, 1, 0, JBUI.scale(6)))
        panel.border = JBUI.Borders.empty(8)

        panel.add(enabledCheckbox)

        val portPanel = JPanel(BorderLayout(JBUI.scale(6), 0))
        portPanel.add(JBLabel("Port:"), BorderLayout.WEST)
        portPanel.add(portField, BorderLayout.CENTER)
        panel.add(portPanel)

        panel.add(
            JBLabel(
                "The listener accepts Eclipse GhidraDev-compatible open_<absolute-path> " +
                    "requests and binds only to 127.0.0.1."
            )
        )

        reset()
        return panel
    }

    override fun isModified(): Boolean {
        val settings = listenerService.settings()

        return enabledCheckbox.isSelected != settings.enabled ||
            portField.text.trim() != settings.port.toString()
    }

    @Throws(ConfigurationException::class)
    override fun apply() {
        val port = portField.text
            .trim()
            .toIntOrNull()
            ?: throw ConfigurationException(
                "Script Editor port must be an integer."
            )

        try {
            listenerService.updateSettings(
                enabled = enabledCheckbox.isSelected,
                port = port,
            )
        }
        catch (e: IllegalArgumentException) {
            throw ConfigurationException(
                e.message ?: "Invalid Script Editor port."
            )
        }
        catch (e: Exception) {
            throw ConfigurationException(
                "Failed to start Script Editor listener: ${
                    e.message ?: "unknown error."
                }"
            )
        }
    }

    override fun reset() {
        val settings = listenerService.settings()

        enabledCheckbox.isSelected = settings.enabled
        portField.text = settings.port.toString()
    }

    override fun disposeUIResources() {
    }
}