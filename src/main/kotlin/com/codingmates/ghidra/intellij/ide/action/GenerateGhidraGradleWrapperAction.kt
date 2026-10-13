package com.codingmates.ghidra.intellij.ide.action

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
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.text.StringUtil
import java.nio.file.Path

class GenerateGhidraGradleWrapperAction : DumbAwareAction(
    "Generate Ghidra Gradle Wrapper",
    "Generate a Gradle Wrapper for the current Ghidra project",
    GhidraIcons.Ghidra,
) {

    private val installationRegistry: GhidraInstallationRegistryService
        get() = ApplicationManager.getApplication()
            .getService(GhidraInstallationRegistryService::class.java)

    override fun getActionUpdateThread(): ActionUpdateThread =
        ActionUpdateThread.BGT

    override fun update(event: AnActionEvent) {
        val project = event.getData(CommonDataKeys.PROJECT)
        val projectRoot = project?.basePath?.let(Path::of)

        val projectKind = projectRoot?.let {
            GhidraProjectLayoutDiscovery.discover(it).kind
        }

        val isGhidraGradleProject =
            projectKind == GhidraProjectKind.EXTENSION ||
                projectKind == GhidraProjectKind.SCRIPT

        val hasWrapper = projectRoot?.let {
            GhidraGradleCommandBuilder.hasWrapper(it)
        } ?: false

        val hasConfiguredGradle =
            installationRegistry.gradleExecutablePath() != null

        event.presentation.isEnabledAndVisible =
            isGhidraGradleProject &&
                !hasWrapper &&
                hasConfiguredGradle
    }

    override fun actionPerformed(event: AnActionEvent) {
        val project = event.getData(CommonDataKeys.PROJECT) ?: return
        val projectRoot = project.basePath?.let(Path::of) ?: return

        val gradleExecutablePath = installationRegistry.gradleExecutablePath()
        if (gradleExecutablePath == null) {
            Messages.showErrorDialog(
                project,
                "No Gradle executable is configured. " +
                    "Configure one in Settings → Tools → Ghidra Installations.",
                "Generate Ghidra Gradle Wrapper",
            )
            return
        }

        val command = try {
            GhidraGradleCommandBuilder.generateWrapper(
                projectRoot = projectRoot,
                gradleExecutablePath = gradleExecutablePath,
            )
        }
        catch (e: IllegalArgumentException) {
            Messages.showErrorDialog(
                project,
                e.message ?: "Invalid Gradle executable.",
                "Generate Ghidra Gradle Wrapper",
            )
            return
        }

        ProgressManager.getInstance().run(
            object : Task.Backgroundable(
                project,
                "Generating Ghidra Gradle Wrapper",
                false,
            ) {
                override fun run(indicator: ProgressIndicator) {
                    indicator.isIndeterminate = true

                    try {
                        val output = CapturingProcessHandler(
                            createCommandLine(command)
                        ).runProcess()

                        if (output.exitCode == 0) {
                            notify(
                                project,
                                "Ghidra Gradle Wrapper Generated",
                                "Generated Gradle Wrapper in:<br/><code>${
                                    StringUtil.escapeXmlEntities(
                                        projectRoot.toString()
                                    )
                                }</code>",
                                NotificationType.INFORMATION,
                            )
                        }
                        else {
                            val details = output.stderr
                                .ifBlank { output.stdout }
                                .takeLast(2_000)

                            notify(
                                project,
                                "Ghidra Gradle Wrapper Generation Failed",
                                "Gradle exited with code ${output.exitCode}.<br/>" +
                                    "<pre>${StringUtil.escapeXmlEntities(details)}</pre>",
                                NotificationType.ERROR,
                            )
                        }
                    }
                    catch (e: Exception) {
                        notify(
                            project,
                            "Ghidra Gradle Wrapper Generation Failed",
                            StringUtil.escapeXmlEntities(
                                e.message ?: "Unexpected error while generating Gradle Wrapper."
                            ),
                            NotificationType.ERROR,
                        )
                    }
                }
            }
        )
    }

    private fun createCommandLine(command: GhidraGradleCommand): GeneralCommandLine {
        val commandLine = if (
            System.getProperty("os.name").startsWith("Windows", true)
        ) {
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