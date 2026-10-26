package com.codingmates.ghidra.intellij.ide.action

import com.codingmates.ghidra.intellij.ide.extension.GhidraSleighBuildSupport
import com.codingmates.ghidra.intellij.ide.facet.GhidraGeneratedProjectConfigurator
import com.codingmates.ghidra.intellij.ide.icons.GhidraIcons
import com.codingmates.ghidra.intellij.ide.installation.GhidraInstallation
import com.codingmates.ghidra.intellij.ide.installation.GhidraInstallationRegistryService
import com.codingmates.ghidra.intellij.ide.migration.GhidraProjectJavaConfigurationUpdater
import com.codingmates.ghidra.intellij.ide.migration.GhidraProjectTargetSnapshotCollector
import com.codingmates.ghidra.intellij.ide.migration.MigrateGhidraProjectTargetDialog
import com.codingmates.ghidra.intellij.ide.project.GhidraProjectKind
import com.codingmates.ghidra.intellij.ide.project.GhidraProjectLayoutDiscovery
import com.codingmates.ghidra.intellij.ide.project.GhidraProjectLocalTargetConfiguration
import com.codingmates.ghidra.intellij.ide.runConfiguration.GhidraDevelopmentLauncherConfigurator
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.externalSystem.util.ExternalSystemUtil
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.text.StringUtil
import java.nio.file.Path
import org.jetbrains.plugins.gradle.util.GradleConstants

/**
 * Migrates the runtime/build target of an existing Ghidra Extension Project.
 *
 * Stage 1 changes only the project-local Ghidra target, facet, and SLEIGH local Ant target.
 * Explicitly selected Project SDK, Gradle JVM, launcher JRE, and safe Gradle reload changes may
 * also be applied. Gradle Wrapper updates remain a separate explicit action.
 */
class MigrateGhidraProjectTargetAction : DumbAwareAction(
    "Migrate Ghidra Project Target",
    "Change the Ghidra runtime target for the current extension project",
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

        event.presentation.isEnabledAndVisible = projectRoot?.let { root ->
            GhidraProjectLayoutDiscovery.discover(root).kind ==
                GhidraProjectKind.EXTENSION
        } == true
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
                "Migrate Ghidra Project Target",
            )
            return
        }

        val installations = discoverRegisteredInstallations(project) ?: return
        val snapshot = try {
            GhidraProjectTargetSnapshotCollector.collect(project)
        }
        catch (e: Exception) {
            Messages.showErrorDialog(
                project,
                "Failed to inspect the current Ghidra project target: ${
                    e.message ?: "unknown error."
                }",
                "Migrate Ghidra Project Target",
            )
            return
        }

        val dialog = MigrateGhidraProjectTargetDialog(
            project = project,
            targetInstallations = installations,
            defaultInstallation = preferredTarget(snapshot.localInstallationPath),
            snapshot = snapshot,
        )

        if (!dialog.showAndGet()) {
            return
        }

        val selectedInstallation = dialog.selectedInstallation()

        try {
            val propertiesFile =
                GhidraProjectLocalTargetConfiguration.writeInstallation(
                    projectRoot = projectRoot,
                    ghidraInstallationPath = selectedInstallation.configuredPath,
                )

            val sleighPropertiesFile = if (
                GhidraSleighBuildSupport.hasBuildLanguageFile(projectRoot)
            ) {
                GhidraSleighBuildSupport.writeAntProperties(
                    projectRoot = projectRoot,
                    ghidraInstallationPath = selectedInstallation.configuredPath,
                )
            }
            else {
                null
            }

            val configuredKind = GhidraGeneratedProjectConfigurator.configure(
                project = project,
                installation = selectedInstallation,
            )

            if (configuredKind != GhidraProjectKind.EXTENSION) {
                throw IllegalStateException(
                    "The local target was updated, but IntelliJ could not find the " +
                        "primary Gradle module required to configure the Ghidra facet."
                )
            }

            val launcherResult =
                GhidraDevelopmentLauncherConfigurator.ensure(project)

            val javaConfigurationResult = dialog.selectedCompatibleSdk()
                ?.takeIf {
                    dialog.shouldUpdateProjectSdk() ||
                        dialog.shouldUpdateGradleJvm() ||
                        dialog.shouldUpdateLauncherJre()
                }
                ?.let { compatibleSdk ->
                    GhidraProjectJavaConfigurationUpdater.apply(
                        project = project,
                        compatibleSdk = compatibleSdk,
                        updateProjectSdk = dialog.shouldUpdateProjectSdk(),
                        updateGradleJvm = dialog.shouldUpdateGradleJvm(),
                        updateExplicitLauncherJre =
                            dialog.shouldUpdateLauncherJre(),
                    )
                }

            if (dialog.shouldReloadGradle()) {
                requestGradleReload(
                    project = project,
                    projectRoot = projectRoot,
                )
            }

            notifyMigrationCompleted(
                project = project,
                installation = selectedInstallation,
                propertiesFile = propertiesFile,
                sleighPropertiesFile = sleighPropertiesFile,
                launcherCreated = launcherResult.created,
                javaConfigurationResult = javaConfigurationResult,
                gradleReloadRequested = dialog.shouldReloadGradle(),
            )
        }
        catch (e: Exception) {
            Messages.showErrorDialog(
                project,
                "Failed to migrate the Ghidra project target: ${
                    e.message ?: "unknown error."
                }",
                "Migrate Ghidra Project Target",
            )
        }
    }

    private fun requestGradleReload(
        project: com.intellij.openapi.project.Project,
        projectRoot: Path,
    ) {
        ApplicationManager.getApplication().invokeLater {
            if (!project.isDisposed) {
                ExternalSystemUtil.requestImport(
                    project,
                    projectRoot.toString(),
                    GradleConstants.SYSTEM_ID,
                )
            }
        }
    }

    private fun discoverRegisteredInstallations(
        project: com.intellij.openapi.project.Project,
    ): List<GhidraInstallation>? {
        val configuredPaths = installationRegistry.configuredPaths()

        if (configuredPaths.isEmpty()) {
            Messages.showErrorDialog(
                project,
                "No Ghidra installation is configured. Configure one in " +
                    "Settings → Tools → Ghidra Installations.",
                "Migrate Ghidra Project Target",
            )
            return null
        }

        return try {
            configuredPaths.map(installationRegistry::register)
        }
        catch (e: Exception) {
            Messages.showErrorDialog(
                project,
                "Failed to read configured Ghidra installations: ${
                    e.message ?: "unknown error."
                }",
                "Migrate Ghidra Project Target",
            )
            null
        }
    }

    private fun preferredTarget(
        localInstallationPath: Path?,
    ): Path? {
        val normalizedLocalPath = localInstallationPath
            ?.toAbsolutePath()
            ?.normalize()

        if (
            normalizedLocalPath != null &&
            installationRegistry.configuredPaths().any { configuredPath ->
                configuredPath.toAbsolutePath().normalize() == normalizedLocalPath
            }
        ) {
            return normalizedLocalPath
        }

        return installationRegistry.defaultConfiguredPath()
    }

    private fun notifyMigrationCompleted(
        project: com.intellij.openapi.project.Project,
        installation: GhidraInstallation,
        propertiesFile: Path,
        sleighPropertiesFile: Path?,
        launcherCreated: Boolean,
        javaConfigurationResult:
            com.codingmates.ghidra.intellij.ide.migration
                .GhidraProjectJavaConfigurationUpdateResult?,
        gradleReloadRequested: Boolean,
    ) {
        val content = buildString {
            append(
                "Migrated the local project target to <b>Ghidra ${
                    StringUtil.escapeXmlEntities(installation.version)
                } ${
                    StringUtil.escapeXmlEntities(installation.releaseName)
                }</b>.<br/><br/>"
            )

            append(
                "Updated:<br/>" +
                    "• <code>${
                        StringUtil.escapeXmlEntities(propertiesFile.toString())
                    }</code><br/>" +
                    "• Ghidra facet"
            )

            sleighPropertiesFile?.let { antPropertiesFile ->
                append(
                    "<br/>• <code>${
                        StringUtil.escapeXmlEntities(antPropertiesFile.toString())
                    }</code>"
                )
            }

            append(
                if (launcherCreated) {
                    "<br/>• Created Ghidra development launcher"
                }
                else {
                    "<br/>• Preserved existing Ghidra development launcher"
                }
            )

            javaConfigurationResult?.let { result ->
                val updatedItems = buildList {
                    if (result.projectSdkUpdated) {
                        add("Project SDK")
                    }

                    if (result.gradleJvmUpdated) {
                        add("linked Gradle JVM")
                    }

                    if (result.launcherJreUpdated) {
                        add("explicit Ghidra Launcher JRE")
                    }
                }

                if (updatedItems.isNotEmpty()) {
                    append(
                        "<br/><br/><b>Updated Java configuration:</b><br/>• " +
                            updatedItems.joinToString("<br/>• ")
                    )
                }
            }

            if (gradleReloadRequested) {
                append(
                    "<br/><br/>Gradle reload requested. Review the Gradle Sync output " +
                        "before building or launching."
                )
            }
            else {
                append(
                    "<br/><br/><b>Required follow-up:</b><br/>" +
                        "Reload Gradle after resolving any global GHIDRA_INSTALL_DIR override. " +
                        "Review remaining migration diagnostics before building or launching."
                )
            }
        }

        NotificationGroupManager.getInstance()
            .getNotificationGroup("Ghidra")
            .createNotification(
                "Ghidra Project Target Migrated",
                content,
                NotificationType.INFORMATION,
            )
            .notify(project)
    }
}