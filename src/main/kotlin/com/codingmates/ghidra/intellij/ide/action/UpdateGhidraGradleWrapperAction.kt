package com.codingmates.ghidra.intellij.ide.action

import com.codingmates.ghidra.intellij.ide.facet.GhidraFacet
import com.codingmates.ghidra.intellij.ide.gradle.GhidraGradleCommand
import com.codingmates.ghidra.intellij.ide.gradle.GhidraGradleCommandBuilder
import com.codingmates.ghidra.intellij.ide.gradle.GhidraGradleCommandRunner
import com.codingmates.ghidra.intellij.ide.gradle.GhidraGradleExecutableVersionInspector
import com.codingmates.ghidra.intellij.ide.icons.GhidraIcons
import com.codingmates.ghidra.intellij.ide.installation.GhidraInstallationDiscovery
import com.codingmates.ghidra.intellij.ide.installation.GhidraInstallationRegistryService
import com.codingmates.ghidra.intellij.ide.migration.GhidraCompatibleSdkDiscovery
import com.codingmates.ghidra.intellij.ide.migration.GhidraGradleWrapperDiscovery
import com.codingmates.ghidra.intellij.ide.migration.GhidraTargetRequirements
import com.codingmates.ghidra.intellij.ide.migration.UpdateGhidraGradleWrapperDialog
import com.codingmates.ghidra.intellij.ide.project.GhidraProjectKind
import com.codingmates.ghidra.intellij.ide.project.GhidraProjectLayoutDiscovery
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

/**
 * Updates the current extension project's Gradle Wrapper to the selected facet target's minimum
 * supported Gradle version.
 *
 * The configured external Gradle executable performs the update because an existing Wrapper may
 * be too old to run on the selected Ghidra target's required Java version.
 */
class UpdateGhidraGradleWrapperAction : DumbAwareAction(
    "Update Ghidra Gradle Wrapper",
    "Update the Gradle Wrapper for the current Ghidra extension target",
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

        event.presentation.isEnabledAndVisible =
            projectRoot?.let { root ->
                GhidraProjectLayoutDiscovery.discover(root).kind ==
                    GhidraProjectKind.EXTENSION
            } == true &&
                installationRegistry.gradleExecutablePath() != null &&
                project?.let(GhidraFacet::findAnyInProject) != null
    }

    override fun actionPerformed(event: AnActionEvent) {
        val project = event.getData(CommonDataKeys.PROJECT) ?: return

        val projectRoot = project.basePath
            ?.let(Path::of)
            ?.toAbsolutePath()
            ?.normalize()
            ?: return

        if (
            GhidraProjectLayoutDiscovery.discover(projectRoot).kind !=
                GhidraProjectKind.EXTENSION
        ) {
            Messages.showErrorDialog(
                project,
                "The current project is not a recognized Ghidra extension project.",
                "Update Ghidra Gradle Wrapper",
            )
            return
        }

        val gradleExecutablePath = installationRegistry.gradleExecutablePath()
        if (gradleExecutablePath == null) {
            Messages.showErrorDialog(
                project,
                "No Gradle executable is configured. Configure one in " +
                    "Settings → Tools → Ghidra Installations.",
                "Update Ghidra Gradle Wrapper",
            )
            return
        }

        val facetInstallationPath = GhidraFacet.findAnyInProject(project)
            ?.installationPath
            ?.takeIf(String::isNotBlank)

        if (facetInstallationPath == null) {
            Messages.showErrorDialog(
                project,
                "No Ghidra installation is configured for this project.",
                "Update Ghidra Gradle Wrapper",
            )
            return
        }

        val installation = try {
            GhidraInstallationDiscovery.discover(
                Path.of(facetInstallationPath)
            )
        }
        catch (e: Exception) {
            Messages.showErrorDialog(
                project,
                "Failed to inspect the selected Ghidra installation: ${
                    e.message ?: "unknown error."
                }",
                "Update Ghidra Gradle Wrapper",
            )
            return
        }

        val requirements = GhidraTargetRequirements.from(installation)
        val targetGradleVersion = requirements.gradleMinimumVersion

        if (targetGradleVersion == null) {
            Messages.showErrorDialog(
                project,
                "The selected Ghidra installation does not declare a minimum " +
                    "supported Gradle version.",
                "Update Ghidra Gradle Wrapper",
            )
            return
        }

        val compatibleSdks = GhidraCompatibleSdkDiscovery.discover(requirements)
        if (compatibleSdks.isEmpty()) {
            Messages.showErrorDialog(
                project,
                "No registered Java SDK is compatible with Ghidra " +
                    "${installation.version}. Add a compatible Java SDK in " +
                    "File → Project Structure → SDKs before updating the Wrapper.",
                "Update Ghidra Gradle Wrapper",
            )
            return
        }

        val currentWrapper =
            GhidraGradleWrapperDiscovery.discover(projectRoot)

        val dialog = UpdateGhidraGradleWrapperDialog(
            project = project,
            installation = installation,
            currentWrapper = currentWrapper,
            targetGradleVersion = targetGradleVersion,
            compatibleSdks = compatibleSdks,
        )

        if (!dialog.showAndGet()) {
            return
        }

        val selectedSdk = dialog.selectedCompatibleSdk()
            ?: return

        val javaHome = runCatching {
            Path.of(selectedSdk.homePath)
                .toAbsolutePath()
                .normalize()
        }.getOrElse {
            Messages.showErrorDialog(
                project,
                "The selected Java SDK has an invalid home path: " +
                    selectedSdk.homePath,
                "Update Ghidra Gradle Wrapper",
            )
            return
        }

        val command = try {
            GhidraGradleCommandBuilder.updateWrapper(
                projectRoot = projectRoot,
                ghidraInstallationPath = installation.configuredPath,
                gradleExecutablePath = gradleExecutablePath,
                targetGradleVersion = targetGradleVersion,
            )
        }
        catch (e: IllegalArgumentException) {
            Messages.showErrorDialog(
                project,
                e.message ?: "Invalid Gradle executable.",
                "Update Ghidra Gradle Wrapper",
            )
            return
        }

        ProgressManager.getInstance().run(
            object : Task.Backgroundable(
                project,
                "Updating Ghidra Gradle Wrapper",
                false,
            ) {
                override fun run(indicator: ProgressIndicator) {
                    indicator.isIndeterminate = true

                    try {
                        val versionOutput = GhidraGradleCommandRunner.run(
                            command = GhidraGradleCommand(
                                executable = gradleExecutablePath.toString(),
                                arguments = listOf("--version"),
                                workingDirectory = projectRoot,
                                usesWrapper = false,
                            ),
                            javaHome = javaHome,
                        )

                        if (versionOutput.exitCode != 0) {
                            notifyFailure(
                                project = project,
                                details =
                                    "Failed to inspect the configured Gradle executable.\n\n" +
                                        versionOutput.stderr
                                            .ifBlank { versionOutput.stdout }
                                            .takeLast(2_000),
                            )
                            return
                        }

                        val configuredGradleVersion =
                            GhidraGradleExecutableVersionInspector.parseVersion(
                                versionOutput.stdout
                            )

                        if (configuredGradleVersion == null) {
                            notifyFailure(
                                project = project,
                                details =
                                    "Could not determine the configured Gradle executable " +
                                        "version from its --version output.",
                            )
                            return
                        }

                        if (configuredGradleVersion < targetGradleVersion) {
                            notifyFailure(
                                project = project,
                                details =
                                    "Configured Gradle $configuredGradleVersion is too old to " +
                                        "update this project for Ghidra ${installation.version}. " +
                                        "The selected target requires Gradle " +
                                        "$targetGradleVersion or newer.\n\n" +
                                        "Configure a compatible Gradle executable in " +
                                        "Settings → Tools → Ghidra Installations.",
                            )
                            return
                        }

                        val maximumGradleVersion =
                            requirements.gradleMaximumVersion

                        if (
                            maximumGradleVersion != null &&
                            configuredGradleVersion >= maximumGradleVersion
                        ) {
                            notifyFailure(
                                project = project,
                                details =
                                    "Configured Gradle $configuredGradleVersion is outside the " +
                                        "selected Ghidra Gradle range. Ghidra " +
                                        "${installation.version} requires Gradle " +
                                        "$targetGradleVersion or newer and below " +
                                        "$maximumGradleVersion.\n\n" +
                                        "Configure a compatible Gradle executable in " +
                                        "Settings → Tools → Ghidra Installations.",
                            )
                            return
                        }

                        val output = GhidraGradleCommandRunner.run(
                            command = command,
                            javaHome = javaHome,
                        )

                        if (output.exitCode != 0) {
                            notifyFailure(
                                project = project,
                                details = output.stderr
                                    .ifBlank { output.stdout }
                                    .takeLast(2_000),
                            )
                            return
                        }

                        val updatedWrapper =
                            GhidraGradleWrapperDiscovery.discover(projectRoot)

                        val updatedVersion = updatedWrapper?.version

                        if (
                            updatedVersion == null ||
                            updatedVersion < targetGradleVersion
                        ) {
                            notifyFailure(
                                project = project,
                                details =
                                    "Gradle completed successfully, but the project Wrapper " +
                                        "was not updated to at least $targetGradleVersion.",
                            )
                            return
                        }

                        notifySuccess(
                            project = project,
                            updatedVersion = updatedVersion.toString(),
                            javaSdkName = selectedSdk.name,
                        )
                    }
                    catch (e: Exception) {
                        notifyFailure(
                            project = project,
                            details = e.message ?: "Unexpected error while updating Wrapper.",
                        )
                    }
                }
            }
        )
    }

    private fun notifySuccess(
        project: com.intellij.openapi.project.Project,
        updatedVersion: String,
        javaSdkName: String,
    ) {
        NotificationGroupManager.getInstance()
            .getNotificationGroup("Ghidra")
            .createNotification(
                "Ghidra Gradle Wrapper Updated",
                "Updated the project Gradle Wrapper to <b>Gradle ${
                    StringUtil.escapeXmlEntities(updatedVersion)
                }</b> using Java SDK <b>${
                    StringUtil.escapeXmlEntities(javaSdkName)
                }</b>.<br/><br/>" +
                    "You may now set the linked Gradle JVM to a Java version compatible " +
                    "with the selected Ghidra target and reload Gradle.",
                NotificationType.INFORMATION,
            )
            .notify(project)
    }

    private fun notifyFailure(
        project: com.intellij.openapi.project.Project,
        details: String,
    ) {
        NotificationGroupManager.getInstance()
            .getNotificationGroup("Ghidra")
            .createNotification(
                "Ghidra Gradle Wrapper Update Failed",
                "<pre>${
                    StringUtil.escapeXmlEntities(details)
                }</pre>",
                NotificationType.ERROR,
            )
            .notify(project)
    }
}