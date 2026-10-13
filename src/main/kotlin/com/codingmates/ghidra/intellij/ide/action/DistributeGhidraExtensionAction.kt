package com.codingmates.ghidra.intellij.ide.action

import com.codingmates.ghidra.intellij.ide.extension.GhidraExtensionArtifactFinder
import com.codingmates.ghidra.intellij.ide.facet.GhidraFacet
import com.codingmates.ghidra.intellij.ide.gradle.GhidraGradleCommand
import com.codingmates.ghidra.intellij.ide.gradle.GhidraGradleCommandBuilder
import com.codingmates.ghidra.intellij.ide.icons.GhidraIcons
import com.codingmates.ghidra.intellij.ide.installation.GhidraInstallationRegistryService
import com.codingmates.ghidra.intellij.ide.project.GhidraProjectKind
import com.codingmates.ghidra.intellij.ide.project.GhidraProjectLayoutDiscovery
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.CapturingProcessHandler
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.text.StringUtil
import java.nio.file.Path

class DistributeGhidraExtensionAction : DumbAwareAction(
    "Distribute Ghidra Extension",
    "Build and package the current Ghidra extension",
    GhidraIcons.Ghidra,
) {

    private val installationRegistry: GhidraInstallationRegistryService
        get() = com.intellij.openapi.application.ApplicationManager
            .getApplication()
            .getService(GhidraInstallationRegistryService::class.java)

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

        val layout = GhidraProjectLayoutDiscovery.discover(projectRoot)
        if (layout.kind != GhidraProjectKind.EXTENSION) {
            Messages.showErrorDialog(
                project,
                "The current project is not a recognized Ghidra extension project.",
                "Distribute Ghidra Extension",
            )
            return
        }

        val installationPath = GhidraFacet.findAnyInProject(project)?.installationPath
        if (installationPath.isNullOrBlank()) {
            Messages.showErrorDialog(
                project,
                "No Ghidra installation is configured for this project.",
                "Distribute Ghidra Extension",
            )
            return
        }

        val command = GhidraGradleCommandBuilder.distributeExtension(
            projectRoot = projectRoot,
            ghidraInstallationPath = Path.of(installationPath),
            gradleExecutablePath = installationRegistry.gradleExecutablePath(),
        )

        ProgressManager.getInstance().run(
            object : Task.Backgroundable(
                project,
                "Distributing Ghidra Extension",
                false,
            ) {
                override fun run(indicator: ProgressIndicator) {
                    indicator.isIndeterminate = true

                    try {
                        val output = CapturingProcessHandler(
                            createCommandLine(command)
                        ).runProcess()

                        if (output.exitCode == 0) {
                            val artifact = GhidraExtensionArtifactFinder.findNewest(projectRoot)

                            val content = if (artifact != null) {
                                "Extension distribution completed successfully.<br/>" +
                                    "<code>${StringUtil.escapeXmlEntities(artifact.toString())}</code>"
                            }
                            else {
                                "Extension distribution completed successfully, but no ZIP artifact was found in dist/."
                            }

                            notify(
                                project,
                                "Ghidra Extension Distributed",
                                content,
                                NotificationType.INFORMATION,
                            )
                        }
                        else {
                            val details = output.stderr
                                .ifBlank { output.stdout }
                                .takeLast(2_000)

                            notify(
                                project,
                                "Ghidra Extension Distribution Failed",
                                "Gradle exited with code ${output.exitCode}.<br/>" +
                                    "<pre>${StringUtil.escapeXmlEntities(details)}</pre>",
                                NotificationType.ERROR,
                            )
                        }
                    }
                    catch (e: Exception) {
                        notify(
                            project,
                            "Ghidra Extension Distribution Failed",
                            StringUtil.escapeXmlEntities(
                                e.message ?: "Unexpected error while running Gradle."
                            ),
                            NotificationType.ERROR,
                        )
                    }
                }
            }
        )
    }

    private fun createCommandLine(command: GhidraGradleCommand): GeneralCommandLine {
        val commandLine = if (System.getProperty("os.name").startsWith("Windows", true)) {
            GeneralCommandLine("cmd.exe", "/c", command.executable)
        }
        else {
            GeneralCommandLine(command.executable)
        }

        return commandLine
            .withParameters(command.arguments)
            .withWorkDirectory(command.workingDirectory.toFile())
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