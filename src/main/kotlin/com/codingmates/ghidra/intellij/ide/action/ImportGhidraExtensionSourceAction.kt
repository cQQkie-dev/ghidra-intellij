package com.codingmates.ghidra.intellij.ide.action

import com.codingmates.ghidra.intellij.ide.extension.GhidraExtensionSourceImportException
import com.codingmates.ghidra.intellij.ide.extension.GhidraExtensionSourceImporter
import com.codingmates.ghidra.intellij.ide.extension.ImportGhidraExtensionSourceDialog
import com.codingmates.ghidra.intellij.ide.facet.GhidraGeneratedProjectFacetSetup
import com.codingmates.ghidra.intellij.ide.icons.GhidraIcons
import com.codingmates.ghidra.intellij.ide.installation.GhidraInstallationRegistryService
import com.intellij.ide.impl.ProjectUtil
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.text.StringUtil

class ImportGhidraExtensionSourceAction : DumbAwareAction(
    "Import Ghidra Extension Source",
    "Open an existing Gradle-backed Ghidra extension project",
    GhidraIcons.Ghidra,
) {

    private val installationRegistry: GhidraInstallationRegistryService
        get() = ApplicationManager.getApplication()
            .getService(GhidraInstallationRegistryService::class.java)

    override fun actionPerformed(event: AnActionEvent) {
        val project = event.getData(CommonDataKeys.PROJECT)

        val targetInstallations = installationRegistry.configuredPaths()
        if (targetInstallations.isEmpty()) {
            Messages.showErrorDialog(
                project,
                "No Ghidra installation is configured. Configure one in " +
                    "Settings → Tools → Ghidra Installations.",
                "Import Ghidra Extension Source",
            )
            return
        }

        val dialog = ImportGhidraExtensionSourceDialog(
            project = project,
            targetInstallations = targetInstallations,
            defaultInstallation = installationRegistry.defaultConfiguredPath(),
        )

        if (!dialog.showAndGet()) {
            return
        }

        try {
            val sourceProject = GhidraExtensionSourceImporter.inspect(
                dialog.sourceDirectory()
            )

            val targetInstallation = installationRegistry.register(
                dialog.targetInstallationPath()
            )

            val sourceCanonicalPath =
                sourceProject.projectDirectory.toRealPath()

            if (sourceCanonicalPath.startsWith(targetInstallation.canonicalPath)) {
                Messages.showErrorDialog(
                    project,
                    "Extension source directory cannot reside inside the selected " +
                        "Ghidra installation.",
                    "Import Ghidra Extension Source",
                )
                return
            }

            val migrationWarning =
                sourceProject.migrationWarning(targetInstallation.version)

            if (migrationWarning != null) {
                val confirmation = Messages.showYesNoDialog(
                    project,
                    migrationWarning,
                    "Migrate Ghidra Extension Target",
                    Messages.getWarningIcon(),
                )

                if (confirmation != Messages.YES) {
                    return
                }
            }

            val propertiesFile =
                GhidraExtensionSourceImporter.configureLocalInstallation(
                    projectDirectory = sourceProject.projectDirectory,
                    ghidraInstallationPath =
                        targetInstallation.configuredPath,
                )

            val openedProject = ProjectUtil.openOrImport(
                sourceProject.projectDirectory,
                null,
                true,
            )

            if (openedProject != null) {
                GhidraGeneratedProjectFacetSetup.configureAfterGradleImport(
                    openedProject
                )
            }

            NotificationGroupManager.getInstance()
                .getNotificationGroup("Ghidra")
                .createNotification(
                    "Ghidra Extension Source Imported",
                    "Configured local target Ghidra <b>${
                        StringUtil.escapeXmlEntities(
                            "${targetInstallation.version} " +
                                targetInstallation.releaseName
                        )
                    }</b> in:<br/><code>${
                        StringUtil.escapeXmlEntities(
                            propertiesFile.toString()
                        )
                    }</code>",
                    NotificationType.INFORMATION,
                )
                .notify(project)
        }
        catch (e: GhidraExtensionSourceImportException) {
            Messages.showErrorDialog(
                project,
                e.message ?: "Failed to import Ghidra extension source.",
                "Import Ghidra Extension Source",
            )
        }
        catch (e: Exception) {
            Messages.showErrorDialog(
                project,
                e.message ?: "Unexpected error while importing Ghidra extension source.",
                "Import Ghidra Extension Source",
            )
        }
    }
}