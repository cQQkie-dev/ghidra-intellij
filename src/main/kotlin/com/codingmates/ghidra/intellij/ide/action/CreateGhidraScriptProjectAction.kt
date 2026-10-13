package com.codingmates.ghidra.intellij.ide.action

import com.codingmates.ghidra.intellij.ide.gradle.GhidraGradleCommandBuilder
import com.codingmates.ghidra.intellij.ide.gradle.GhidraGradleCommandRunner
import com.codingmates.ghidra.intellij.ide.icons.GhidraIcons
import com.codingmates.ghidra.intellij.ide.installation.GhidraInstallationRegistryService
import com.codingmates.ghidra.intellij.ide.script.CreateGhidraScriptProjectDialog
import com.codingmates.ghidra.intellij.ide.script.GhidraScriptProjectGenerationException
import com.codingmates.ghidra.intellij.ide.script.GhidraScriptProjectGenerator
import com.intellij.ide.impl.ProjectUtil
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
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

class CreateGhidraScriptProjectAction : DumbAwareAction(
    "New Ghidra Script Project",
    "Create a Gradle-backed Ghidra script project",
    GhidraIcons.Ghidra,
) {

    private val installationRegistry: GhidraInstallationRegistryService
        get() = ApplicationManager.getApplication()
            .getService(GhidraInstallationRegistryService::class.java)

    override fun actionPerformed(event: AnActionEvent) {
        val project = event.getData(CommonDataKeys.PROJECT)

        val ghidraInstallationPath = installationRegistry.defaultConfiguredPath()
        if (ghidraInstallationPath == null) {
            Messages.showErrorDialog(
                project,
                "No default Ghidra installation is configured. " +
                    "Configure one in Settings → Tools → Ghidra Installations.",
                "Create Ghidra Script Project",
            )
            return
        }

        val defaultParentDirectory = project?.basePath
            ?.let(Path::of)
            ?.parent
            ?: Path.of(System.getProperty("user.home"))

        val dialog = CreateGhidraScriptProjectDialog(
            project = project,
            defaultParentDirectory = defaultParentDirectory,
        )

        if (!dialog.showAndGet()) {
            return
        }

        try {
            val generated = GhidraScriptProjectGenerator.create(
                dialog.projectSpec(ghidraInstallationPath)
            )

            generateWrapperAndNotify(
                project = project,
                projectDirectory = generated.projectDirectory,
            )
        }
        catch (e: GhidraScriptProjectGenerationException) {
            Messages.showErrorDialog(
                project,
                e.message ?: "Failed to create Ghidra script project.",
                "Create Ghidra Script Project",
            )
        }
    }

    private fun generateWrapperAndNotify(
        project: com.intellij.openapi.project.Project?,
        projectDirectory: Path,
    ) {
        val gradleExecutablePath = installationRegistry.gradleExecutablePath()

        if (gradleExecutablePath == null) {
            notifyProjectCreated(
                project = project,
                projectDirectory = projectDirectory,
                wrapperMessage =
                    "No Gradle Wrapper was generated because no Gradle executable is configured. " +
                        "Configure one in Settings → Tools → Ghidra Installations, " +
                        "then use Tools → Generate Ghidra Gradle Wrapper.",
                notificationType = NotificationType.WARNING,
            )
            return
        }

        val command = try {
            GhidraGradleCommandBuilder.generateWrapper(
                projectRoot = projectDirectory,
                gradleExecutablePath = gradleExecutablePath,
            )
        }
        catch (e: IllegalArgumentException) {
            notifyProjectCreated(
                project = project,
                projectDirectory = projectDirectory,
                wrapperMessage =
                    "No Gradle Wrapper was generated: ${
                        e.message ?: "invalid configured Gradle executable."
                    }",
                notificationType = NotificationType.WARNING,
            )
            return
        }

        ProgressManager.getInstance().run(
            object : Task.Backgroundable(
                project,
                "Generating Gradle Wrapper for Ghidra Script Project",
                false,
            ) {
                override fun run(indicator: ProgressIndicator) {
                    indicator.isIndeterminate = true

                    try {
                        val output = GhidraGradleCommandRunner.run(command)

                        if (output.exitCode == 0) {
                            notifyProjectCreated(
                                project = project,
                                projectDirectory = projectDirectory,
                                wrapperMessage = "Generated a Gradle Wrapper.",
                                notificationType = NotificationType.INFORMATION,
                            )
                        }
                        else {
                            val details = output.stderr
                                .ifBlank { output.stdout }
                                .takeLast(2_000)

                            notifyProjectCreated(
                                project = project,
                                projectDirectory = projectDirectory,
                                wrapperMessage =
                                    "The project was created, but Gradle Wrapper generation " +
                                        "failed with exit code ${output.exitCode}.<br/><pre>${
                                            StringUtil.escapeXmlEntities(details)
                                        }</pre>",
                                notificationType = NotificationType.ERROR,
                            )
                        }
                    }
                    catch (e: Exception) {
                        notifyProjectCreated(
                            project = project,
                            projectDirectory = projectDirectory,
                            wrapperMessage =
                                "The project was created, but Gradle Wrapper generation failed: ${
                                    StringUtil.escapeXmlEntities(
                                        e.message
                                            ?: "unexpected error."
                                    )
                                }",
                            notificationType = NotificationType.ERROR,
                        )
                    }
                }
            }
        )
    }

    private fun notifyProjectCreated(
        project: com.intellij.openapi.project.Project?,
        projectDirectory: Path,
        wrapperMessage: String,
        notificationType: NotificationType,
    ) {
        val notification = NotificationGroupManager.getInstance()
            .getNotificationGroup("Ghidra")
            .createNotification(
                "Ghidra Script Project Created",
                "Created project at:<br/><code>${
                    StringUtil.escapeXmlEntities(projectDirectory.toString())
                }</code><br/><br/>$wrapperMessage<br/><br/>" +
                    "Open the project to import it through IntelliJ's Gradle integration.",
                notificationType,
            )

        notification.addAction(
            NotificationAction.createSimple("Open Project") {
                ProjectUtil.openOrImport(
                    projectDirectory,
                    null,
                    true,
                )
            }
        )

        notification.notify(project)
    }

}