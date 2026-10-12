package com.codingmates.ghidra.intellij.ide.action

import com.codingmates.ghidra.intellij.ide.extension.GhidraExtensionArtifactFinder
import com.codingmates.ghidra.intellij.ide.extension.GhidraExtensionInstallationException
import com.codingmates.ghidra.intellij.ide.extension.GhidraExtensionInstaller
import com.codingmates.ghidra.intellij.ide.facet.GhidraFacet
import com.codingmates.ghidra.intellij.ide.installation.GhidraInstallationDiscovery
import com.codingmates.ghidra.intellij.ide.installation.GhidraInstallationException
import com.codingmates.ghidra.intellij.ide.project.GhidraProjectKind
import com.codingmates.ghidra.intellij.ide.project.GhidraProjectLayoutDiscovery
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.text.StringUtil
import java.nio.file.Path

class InstallGhidraExtensionAction : DumbAwareAction(
    "Install Latest Ghidra Extension",
    "Install the newest built Ghidra extension archive",
    null,
) {

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(event: AnActionEvent) {
        val project = event.getData(CommonDataKeys.PROJECT)
        val projectRoot = project?.basePath?.let(Path::of)

        val isExtensionProject = projectRoot?.let {
            GhidraProjectLayoutDiscovery.discover(it).kind == GhidraProjectKind.EXTENSION
        } ?: false

        val hasGhidraFacet =
            project?.let(GhidraFacet::findAnyInProject) != null

        event.presentation.isEnabledAndVisible =
            isExtensionProject && hasGhidraFacet
    }

    override fun actionPerformed(event: AnActionEvent) {
        val project = event.getData(CommonDataKeys.PROJECT) ?: return
        val projectRoot = project.basePath?.let(Path::of) ?: return

        val facet = GhidraFacet.findAnyInProject(project)
        if (facet == null) {
            showError(
                project,
                "No Ghidra facet is configured for this project.",
            )
            return
        }

        val installationPath = facet.installationPath
        val settingsPath = facet.settingsPath

        if (installationPath.isBlank() || settingsPath.isNullOrBlank()) {
            showError(
                project,
                "The Ghidra facet does not contain a valid installation and settings path.",
            )
            return
        }

        val archive = GhidraExtensionArtifactFinder.findNewest(projectRoot)
        if (archive == null) {
            showError(
                project,
                "No extension ZIP archive was found in ${projectRoot.resolve("dist")}. " +
                    "Run Distribute Ghidra Extension first.",
            )
            return
        }

        val confirmation = Messages.showYesNoDialog(
            project,
            "Install the latest extension archive?\n\n$archive\n\n" +
                "Ghidra must be restarted after installation.",
            "Install Ghidra Extension",
            Messages.getQuestionIcon(),
        )

        if (confirmation != Messages.YES) {
            return
        }

        ProgressManager.getInstance().run(
            object : Task.Backgroundable(
                project,
                "Installing Ghidra Extension",
                false,
            ) {
                override fun run(indicator: ProgressIndicator) {
                    indicator.isIndeterminate = true

                    try {
                        val installation =
                            GhidraInstallationDiscovery.discover(Path.of(installationPath))

                        val extensionsDirectory =
                            Path.of(settingsPath).resolve("Extensions")

                        val installed = GhidraExtensionInstaller.install(
                            archivePath = archive,
                            ghidraInstallation = installation,
                            extensionsDirectory = extensionsDirectory,
                        )

                        notify(
                            project,
                            "Ghidra Extension Installed",
                            "Installed <b>${StringUtil.escapeXmlEntities(installed.archive.extensionName)}</b>" +
                                " to:<br/><code>${
                                    StringUtil.escapeXmlEntities(
                                        installed.installationDirectory.toString()
                                    )
                                }</code><br/><br/>Restart Ghidra for the extension to take effect.",
                            NotificationType.INFORMATION,
                        )
                    }
                    catch (e: GhidraInstallationException) {
                        showInstallFailure(project, e.message)
                    }
                    catch (e: GhidraExtensionInstallationException) {
                        showInstallFailure(project, e.message)
                    }
                    catch (e: Exception) {
                        showInstallFailure(
                            project,
                            e.message ?: "Unexpected error while installing extension.",
                        )
                    }
                }
            }
        )
    }

    private fun showError(
        project: com.intellij.openapi.project.Project,
        message: String,
    ) {
        Messages.showErrorDialog(
            project,
            message,
            "Install Ghidra Extension",
        )
    }

    private fun showInstallFailure(
        project: com.intellij.openapi.project.Project,
        message: String?,
    ) {
        notify(
            project,
            "Ghidra Extension Installation Failed",
            StringUtil.escapeXmlEntities(message ?: "Unknown installation error."),
            NotificationType.ERROR,
        )
    }

    private fun notify(
        project: com.intellij.openapi.project.Project,
        title: String,
        content: String,
        type: NotificationType,
    ) {
        NotificationGroupManager.getInstance()
            .getNotificationGroup("Ghidra")
            .createNotification(title, content, type)
            .notify(project)
    }
}