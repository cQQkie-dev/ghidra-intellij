package com.codingmates.ghidra.intellij.ide.action

import com.codingmates.ghidra.intellij.ide.icons.GhidraIcons
import com.codingmates.ghidra.intellij.ide.installation.GhidraInstallationRegistryService
import com.codingmates.ghidra.intellij.ide.script.CreateGhidraScriptProjectDialog
import com.codingmates.ghidra.intellij.ide.script.GhidraScriptProjectGenerationException
import com.codingmates.ghidra.intellij.ide.script.GhidraScriptProjectGenerator
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.ApplicationManager
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

            NotificationGroupManager.getInstance()
                .getNotificationGroup("Ghidra")
                .createNotification(
                    "Ghidra Script Project Created",
                    "Created project at:<br/><code>${
                        StringUtil.escapeXmlEntities(
                            generated.projectDirectory.toString()
                        )
                    }</code><br/><br/>Open the directory as a Gradle project in IntelliJ.",
                    NotificationType.INFORMATION,
                )
                .notify(project)
        }
        catch (e: GhidraScriptProjectGenerationException) {
            Messages.showErrorDialog(
                project,
                e.message ?: "Failed to create Ghidra script project.",
                "Create Ghidra Script Project",
            )
        }
    }
}