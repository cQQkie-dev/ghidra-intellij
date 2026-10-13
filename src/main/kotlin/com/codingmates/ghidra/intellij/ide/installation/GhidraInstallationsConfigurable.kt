package com.codingmates.ghidra.intellij.ide.installation

import com.codingmates.ghidra.intellij.ide.extension.GhidraSkeletonTemplateDiscovery
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
import javax.swing.JTextField

/**
 * Application settings page for registered Ghidra runtime installations.
 */
class GhidraInstallationsConfigurable : Configurable {

    private val installationListModel = DefaultListModel<Path>()
    private val installationList = JBList(installationListModel)
    private val defaultInstallationLabel = JBLabel()
    private val gradleExecutableField = JTextField()
    private val skeletonTemplateField = JTextField()
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

        val centerPanel = JPanel(BorderLayout(0, JBUI.scale(8)))
        centerPanel.add(JBScrollPane(installationList), BorderLayout.CENTER)

        val gradleExecutablePanel = JPanel(BorderLayout(JBUI.scale(6), 0))
        gradleExecutablePanel.add(
            JBLabel("Gradle executable:"),
            BorderLayout.WEST,
        )
        gradleExecutablePanel.add(
            gradleExecutableField,
            BorderLayout.CENTER,
        )

        val browseGradleButton = JButton("Browse...")
        browseGradleButton.addActionListener {
            val descriptor = FileChooserDescriptorFactory.createSingleFileDescriptor()
            descriptor.title = "Select Gradle Executable"

            FileChooser.chooseFile(descriptor, null, null)?.let { selectedFile ->
                gradleExecutableField.text = selectedFile.path
            }
        }

        gradleExecutablePanel.add(
            browseGradleButton,
            BorderLayout.EAST,
        )

        val skeletonTemplatePanel = JPanel(BorderLayout(JBUI.scale(6), 0))
        skeletonTemplatePanel.add(
            JBLabel("Skeleton template:"),
            BorderLayout.WEST,
        )
        skeletonTemplatePanel.add(
            skeletonTemplateField,
            BorderLayout.CENTER,
        )

        val browseSkeletonButton = JButton("Browse...")
        browseSkeletonButton.addActionListener {
            val descriptor = FileChooserDescriptorFactory.createSingleFolderDescriptor()
            descriptor.title = "Select Ghidra Skeleton Template Directory"

            FileChooser.chooseFile(descriptor, null, null)?.let { selectedDirectory ->
                skeletonTemplateField.text = selectedDirectory.path
            }
        }

        skeletonTemplatePanel.add(
            browseSkeletonButton,
            BorderLayout.EAST,
        )

        val configurationPanel = JPanel(
            java.awt.GridLayout(2, 1, 0, JBUI.scale(6))
        )
        configurationPanel.add(gradleExecutablePanel)
        configurationPanel.add(skeletonTemplatePanel)

        centerPanel.add(configurationPanel, BorderLayout.SOUTH)

        panel.add(centerPanel, BorderLayout.CENTER)

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

        val currentGradleExecutable =
            registry.gradleExecutablePath()?.toString().orEmpty()

        val pendingGradleExecutable =
            gradleExecutableField.text.trim()

        val currentSkeletonTemplate =
            registry.skeletonTemplatePath()?.toString().orEmpty()

        val pendingSkeletonTemplate =
            skeletonTemplateField.text.trim()

        return currentPaths != pendingPaths ||
            currentDefault != pendingDefault ||
            currentGradleExecutable != pendingGradleExecutable ||
            currentSkeletonTemplate != pendingSkeletonTemplate
    }

    @Throws(ConfigurationException::class)
    override fun apply() {
        val configuredPaths = (0 until installationListModel.size)
            .map { installationListModel.getElementAt(it) }

        val gradleExecutablePath = gradleExecutableField.text
            .trim()
            .takeIf(String::isNotEmpty)
            ?.let(Path::of)

        val skeletonTemplatePath = skeletonTemplateField.text
            .trim()
            .takeIf(String::isNotEmpty)
            ?.let(Path::of)

        try {
            if (gradleExecutablePath != null) {
                registry.setGradleExecutablePath(gradleExecutablePath)
            }

            if (skeletonTemplatePath != null) {
                GhidraSkeletonTemplateDiscovery.discover(skeletonTemplatePath)
            }

            registry.replaceAll(configuredPaths, pendingDefaultPath)
            registry.setGradleExecutablePath(gradleExecutablePath)
            registry.setSkeletonTemplatePath(skeletonTemplatePath)
        }
        catch (e: GhidraInstallationException) {
            throw ConfigurationException(
                e.message ?: "Failed to register Ghidra installation."
            )
        }
        catch (e: Exception) {
            throw ConfigurationException(
                e.message ?: "Invalid Ghidra installation configuration."
            )
        }
    }

    override fun reset() {
        installationListModel.clear()

        registry.configuredPaths().forEach(installationListModel::addElement)
        pendingDefaultPath = registry.defaultConfiguredPath()
        gradleExecutableField.text =
            registry.gradleExecutablePath()?.toString().orEmpty()
        skeletonTemplateField.text =
            registry.skeletonTemplatePath()?.toString().orEmpty()

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
            pendingDefaultPath = installationListModel.firstElementOrNull()
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