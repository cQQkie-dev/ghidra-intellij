package com.codingmates.ghidra.intellij.ide.action

import com.codingmates.ghidra.intellij.ide.extension.GhidraSleighBuildSupport
import com.codingmates.ghidra.intellij.ide.facet.GhidraFacet
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

class BuildGhidraSleighLanguageAction : DumbAwareAction(
    "Build SLEIGH Language",
    "Compile the current Ghidra Processor/SLEIGH language definition",
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

        val isProcessorProject = projectRoot?.let { root ->
            GhidraProjectLayoutDiscovery.discover(root).kind ==
                GhidraProjectKind.EXTENSION &&
                GhidraSleighBuildSupport.hasBuildLanguageFile(root)
        } ?: false

        val hasGhidraFacet =
            project?.let(GhidraFacet::findAnyInProject) != null

        event.presentation.isEnabledAndVisible =
            isProcessorProject && hasGhidraFacet
    }

    override fun actionPerformed(event: AnActionEvent) {
        val project = event.getData(CommonDataKeys.PROJECT) ?: return
        val projectRoot = project.basePath?.let(Path::of) ?: return

        if (
            GhidraProjectLayoutDiscovery.discover(projectRoot).kind !=
                GhidraProjectKind.EXTENSION ||
            !GhidraSleighBuildSupport.hasBuildLanguageFile(projectRoot)
        ) {
            Messages.showErrorDialog(
                project,
                "The current project does not contain a Ghidra SLEIGH language build.",
                "Build SLEIGH Language",
            )
            return
        }

        val installationPath = GhidraFacet.findAnyInProject(project)
            ?.installationPath

        if (installationPath.isNullOrBlank()) {
            Messages.showErrorDialog(
                project,
                "No Ghidra facet is configured for this project.",
                "Build SLEIGH Language",
            )
            return
        }

        try {
            GhidraSleighBuildSupport.writeAntProperties(
                projectRoot = projectRoot,
                ghidraInstallationPath = Path.of(installationPath),
            )
        }
        catch (e: Exception) {
            Messages.showErrorDialog(
                project,
                "Failed to create .antProperties.xml: ${
                    e.message ?: "unknown error."
                }",
                "Build SLEIGH Language",
            )
            return
        }

        ProgressManager.getInstance().run(
            object : Task.Backgroundable(
                project,
                "Building Ghidra SLEIGH Language",
                false,
            ) {
                override fun run(indicator: ProgressIndicator) {
                    indicator.isIndeterminate = true

                    try {
                        val output = CapturingProcessHandler(
                            createCommandLine(
                                projectRoot = projectRoot,
                                antExecutable =
                                    installationRegistry.antExecutablePath()?.toString(),
                            )
                        ).runProcess()

                        if (output.exitCode == 0) {
                            notify(
                                project,
                                "Ghidra SLEIGH Language Built",
                                "SLEIGH language compilation completed successfully.",
                                NotificationType.INFORMATION,
                            )
                        }
                        else {
                            val details = output.stderr
                                .ifBlank { output.stdout }
                                .takeLast(2_000)

                            notify(
                                project,
                                "Ghidra SLEIGH Language Build Failed",
                                "Ant exited with code ${output.exitCode}.<br/>" +
                                    "<pre>${StringUtil.escapeXmlEntities(details)}</pre>",
                                NotificationType.ERROR,
                            )
                        }
                    }
                    catch (e: Exception) {
                        notify(
                            project,
                            "Ghidra SLEIGH Language Build Failed",
                            "Unable to run Ant. Configure an Ant executable in " +
                                "Settings → Tools → Ghidra Installations, or install Apache Ant " +
                                "and ensure <code>ant</code> is available on PATH.<br/><br/>${
                                    StringUtil.escapeXmlEntities(
                                        e.message ?: "Unexpected error."
                                    )
                                }",
                            NotificationType.ERROR,
                        )
                    }
                }
            }
        )
    }

    private fun createCommandLine(
        projectRoot: Path,
        antExecutable: String?,
    ): GeneralCommandLine {
        val executable = antExecutable ?: if (
            System.getProperty("os.name").startsWith("Windows", true)
        ) {
            "ant.bat"
        }
        else {
            "ant"
        }

        return GeneralCommandLine(executable)
            .withParameters(
                "-f",
                GhidraSleighBuildSupport.buildLanguageFile(projectRoot).toString(),
            )
            .withWorkDirectory(projectRoot.toFile())
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