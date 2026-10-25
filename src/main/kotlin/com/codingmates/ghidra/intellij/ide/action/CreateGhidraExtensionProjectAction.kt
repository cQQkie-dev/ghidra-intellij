package com.codingmates.ghidra.intellij.ide.action

import com.codingmates.ghidra.intellij.ide.extension.CreateGhidraExtensionProjectDialog
import com.codingmates.ghidra.intellij.ide.extension.GhidraExtensionProjectGenerationException
import com.codingmates.ghidra.intellij.ide.extension.GhidraExtensionProjectGenerator
import com.codingmates.ghidra.intellij.ide.facet.GhidraGeneratedProjectFacetSetup
import com.codingmates.ghidra.intellij.ide.gradle.GhidraGradleCommandBuilder
import com.codingmates.ghidra.intellij.ide.gradle.GhidraGradleCommandRunner
import com.codingmates.ghidra.intellij.ide.icons.GhidraIcons
import com.codingmates.ghidra.intellij.ide.installation.GhidraInstallationRegistryService
import com.codingmates.ghidra.intellij.ide.installation.GhidraInstallation
import com.codingmates.ghidra.intellij.ide.migration.GhidraGlobalTargetOverrideDiscovery
import com.codingmates.ghidra.intellij.ide.migration.GhidraTargetRequirements
import com.intellij.ide.impl.ProjectUtil
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationAction
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

class CreateGhidraExtensionProjectAction : DumbAwareAction(
    "New Ghidra Extension Project",
    "Create a Gradle-backed Ghidra extension project",
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
                "No Ghidra installation is configured. " +
                    "Configure one in Settings → Tools → Ghidra Installations.",
                "Create Ghidra Extension Project",
            )
            return
        }

        val skeletonTemplatePath = installationRegistry.skeletonTemplatePath()
        if (skeletonTemplatePath == null) {
            Messages.showErrorDialog(
                project,
                "No Ghidra Skeleton template is configured. " +
                    "Configure one in Settings → Tools → Ghidra Installations.",
                "Create Ghidra Extension Project",
            )
            return
        }

        val defaultParentDirectory = project?.basePath
            ?.let(Path::of)
            ?.parent
            ?: Path.of(System.getProperty("user.home"))

        val dialog = CreateGhidraExtensionProjectDialog(
            project = project,
            defaultParentDirectory = defaultParentDirectory,
            targetInstallations = targetInstallations,
            defaultInstallation = installationRegistry.defaultConfiguredPath(),
        )

        if (!dialog.showAndGet()) {
            return
        }

        try {
            val targetInstallation = installationRegistry.register(
                dialog.targetInstallationPath()
            )

            if (!confirmTargetRequirements(project, targetInstallation)) {
                return
            }

            val generated = GhidraExtensionProjectGenerator.create(
                dialog.projectSpec(
                    skeletonTemplatePath = skeletonTemplatePath,
                )
            )

            generateWrapperAndNotify(
                project = project,
                projectDirectory = generated.projectDirectory,
            )
        }
        catch (e: GhidraExtensionProjectGenerationException) {
            Messages.showErrorDialog(
                project,
                e.message ?: "Failed to create Ghidra extension project.",
                "Create Ghidra Extension Project",
            )
        }
    }

    private fun confirmTargetRequirements(
        project: com.intellij.openapi.project.Project?,
        installation: GhidraInstallation,
    ): Boolean {
        val requirements = GhidraTargetRequirements.from(installation)
        val requiredJavaVersion = requirements.requiredJavaVersion

        val environmentOverride =
            GhidraGlobalTargetOverrideDiscovery.discoverEnvironmentOverride(
                System.getenv()
            )

        val userGradleOverride =
            GhidraGlobalTargetOverrideDiscovery
                .discoverUserGradlePropertiesOverride(
                    Path.of(
                        System.getProperty("user.home"),
                        ".gradle",
                    )
                )

        val requiresAcknowledgement =
            (requiredJavaVersion ?: 0) >= 25 ||
                environmentOverride != null ||
                userGradleOverride != null

        if (!requiresAcknowledgement) {
            return true
        }

        val details = buildString {
            append(
                "Selected target: Ghidra ${installation.version} " +
                    "${installation.releaseName}\n\n"
            )

            append("Requirements:\n")
            requiredJavaVersion?.let {
                append("• Java $it or newer\n")
            }
            requirements.gradleMinimumVersion?.let {
                append("• Gradle $it or newer\n")
            }

            if (environmentOverride != null || userGradleOverride != null) {
                append("\nGlobal target override detected:\n")

                environmentOverride?.let { override ->
                    append(
                        "• Environment GHIDRA_INSTALL_DIR=" +
                            "${override.configuredValue}\n"
                    )
                }

                userGradleOverride?.let { override ->
                    append(
                        "• ${override.propertiesFile}: " +
                            "GHIDRA_INSTALL_DIR=${override.configuredValue}\n"
                    )
                }

                append(
                    "\nThe official Ghidra Skeleton gives the environment variable " +
                        "priority over project-local Gradle properties. A global override " +
                        "may therefore cause normal Gradle sync/build operations to use a " +
                        "different Ghidra installation."
                )
            }

            append(
                "\n\nAfter opening the generated project, configure its Project SDK, " +
                    "Gradle JVM, and any explicit Ghidra Launcher JRE as required."
            )
        }

        return Messages.showYesNoDialog(
            project,
            details,
            "Ghidra Project Requirements",
            "Create Anyway",
            "Cancel",
            Messages.getWarningIcon(),
        ) == Messages.YES
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
                "Generating Gradle Wrapper for Ghidra Extension Project",
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
                "Ghidra Extension Project Created",
                "Created project at:<br/><code>${
                    StringUtil.escapeXmlEntities(projectDirectory.toString())
                }</code><br/><br/>$wrapperMessage<br/><br/>" +
                    "Open the project to import it through IntelliJ's Gradle integration.",
                notificationType,
            )

        notification.addAction(
            NotificationAction.createSimple("Open Project") {
                val openedProject = ProjectUtil.openOrImport(
                    projectDirectory,
                    null,
                    true,
                )

                if (openedProject != null) {
                    GhidraGeneratedProjectFacetSetup.configureAfterGradleImport(
                        openedProject
                    )
                }
            }
        )

        notification.notify(project)
    }
}