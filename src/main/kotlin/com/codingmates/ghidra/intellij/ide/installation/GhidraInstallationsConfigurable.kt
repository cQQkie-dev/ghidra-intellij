package com.codingmates.ghidra.intellij.ide.installation

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.options.ConfigurationException
import com.intellij.openapi.ui.Messages
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.FlowLayout
import java.nio.file.Path
import javax.swing.DefaultListModel
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JPanel

/**
 * Application settings page for registered Ghidra runtime installations.
 */
class GhidraInstallationsConfigurable : Configurable {

    private val installationListModel = DefaultListModel<Path>()
    private val installationList = JBList(installationListModel)
    private val defaultInstallationLabel = JBLabel()
    private var pendingDefaultPath: Path? = null

    private val registry: GhidraInstallationRegistryService
        get() = ApplicationManager.getApplication()
            .getService(GhidraInstallationRegistryService::class.java)

    override fun getDisplayName(): String = "Ghidra Installations"

    override fun createComponent(): JComponent {
        val panel = JPanel(BorderLayout(0, JBUI.scale(8)))
        panel.border = JBUI.Borders.empty(8)

        panel.add(
            JBLabel(
                "Registered Ghidra runtime installations. " +
                    "The default installation is used for newly created Ghidra projects."
            ),
            BorderLayout.NORTH,
        )

        panel.add(JBScrollPane(installationList), BorderLayout.CENTER)

        val addButton = JButton("Add...")
        addButton.addActionListener { addInstallation() }

        val removeButton = JButton("Remove")
        removeButton.addActionListener { removeSelectedInstallation() }

        val setDefaultButton = JButton("Set as Default")
        setDefaultButton.addActionListener { setSelectedInstallationAsDefault() }

        val buttons = JPanel(FlowLayout(FlowLayout.LEFT, JBUI.scale(4), 0))
        buttons.add(addButton)
        buttons.add(removeButton)
        buttons.add(setDefaultButton)

        val footer = JPanel(BorderLayout(0, JBUI.scale(6)))
        footer.add(buttons, BorderLayout.NORTH)
        footer.add(defaultInstallationLabel, BorderLayout.SOUTH)

        panel.add(footer, BorderLayout.SOUTH)

        reset()
        return panel
    }

    override fun isModified(): Boolean {
        val currentPaths = registry.configuredPaths().map(Path::toString)
        val pendingPaths = (0 until installationListModel.size)
            .map { installationListModel.getElementAt(it).toString() }

        val currentDefault = registry.defaultConfiguredPath()?.toString()
        val pendingDefault = pendingDefaultPath?.toString()

        return currentPaths != pendingPaths || currentDefault != pendingDefault
    }

    @Throws(ConfigurationException::class)
    override fun apply() {
        val configuredPaths = (0 until installationListModel.size)
            .map { installationListModel.getElementAt(it) }

        try {
            registry.replaceAll(configuredPaths, pendingDefaultPath)
        }
        catch (e: GhidraInstallationException) {
            throw ConfigurationException(e.message ?: "Failed to register Ghidra installation.")
        }
        catch (e: IllegalArgumentException) {
            throw ConfigurationException(e.message ?: "Invalid Ghidra installation configuration.")
        }
    }

    override fun reset() {
        installationListModel.clear()

        registry.configuredPaths().forEach(installationListModel::addElement)
        pendingDefaultPath = registry.defaultConfiguredPath()

        updateDefaultInstallationLabel()
    }

    override fun disposeUIResources() {
    }

    private fun addInstallation() {
        val descriptor = FileChooserDescriptorFactory.createSingleFolderDescriptor()
        descriptor.title = "Select Ghidra Installation Directory"

        val selectedDirectory = FileChooser.chooseFile(descriptor, null, null) ?: return
        val configuredPath = Path.of(selectedDirectory.path)

        try {
            val installation = GhidraInstallationDiscovery.discover(configuredPath)

            val alreadyPresent = (0 until installationListModel.size).any {
                installationListModel.getElementAt(it) == installation.configuredPath
            }

            if (!alreadyPresent) {
                installationListModel.addElement(installation.configuredPath)
            }

            if (pendingDefaultPath == null) {
                pendingDefaultPath = installation.configuredPath
            }

            installationList.setSelectedValue(installation.configuredPath, true)
            updateDefaultInstallationLabel()
        }
        catch (e: GhidraInstallationException) {
            Messages.showErrorDialog(
                e.message ?: "Failed to register Ghidra installation.",
                "Invalid Ghidra Installation",
            )
        }
    }

    private fun removeSelectedInstallation() {
        val selectedPath = installationList.selectedValue ?: return
        installationListModel.removeElement(selectedPath)

        if (pendingDefaultPath == selectedPath) {
            pendingDefaultPath =
                installationListModel.firstElementOrNull()
        }

        updateDefaultInstallationLabel()
    }

    private fun setSelectedInstallationAsDefault() {
        pendingDefaultPath = installationList.selectedValue ?: return
        updateDefaultInstallationLabel()
    }

    private fun updateDefaultInstallationLabel() {
        val defaultPath = pendingDefaultPath?.toString() ?: "None"
        defaultInstallationLabel.text = "Default installation: $defaultPath"
    }

    private fun <T> DefaultListModel<T>.firstElementOrNull(): T? =
        if (isEmpty) null else getElementAt(0)
}