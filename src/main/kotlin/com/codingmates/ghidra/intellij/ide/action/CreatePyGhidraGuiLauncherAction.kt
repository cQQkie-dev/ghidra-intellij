package com.codingmates.ghidra.intellij.ide.action

import com.codingmates.ghidra.intellij.ide.facet.GhidraFacet
import com.codingmates.ghidra.intellij.ide.icons.GhidraIcons
import com.codingmates.ghidra.intellij.ide.installation.GhidraPlatform
import com.codingmates.ghidra.intellij.ide.project.GhidraProjectKind
import com.codingmates.ghidra.intellij.ide.project.GhidraProjectLayoutDiscovery
import com.codingmates.ghidra.intellij.ide.runConfiguration.PyGhidraGuiLauncherConfigurator
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.ui.Messages
import java.nio.file.Path

class CreatePyGhidraGuiLauncherAction : DumbAwareAction(
    "Create PyGhidra GUI Launcher",
    "Create a PyGhidra GUI Run Configuration for the current project",
    GhidraIcons.PyGhidra,
) {

    override fun getActionUpdateThread(): ActionUpdateThread =
        ActionUpdateThread.BGT

    override fun update(event: AnActionEvent) {
        val project = event.getData(CommonDataKeys.PROJECT)
        val projectRoot = project?.basePath?.let(Path::of)

        val projectKind = projectRoot?.let {
            GhidraProjectLayoutDiscovery.discover(it).kind
        }

        val isGhidraProject =
            projectKind == GhidraProjectKind.EXTENSION ||
                projectKind == GhidraProjectKind.SCRIPT

        val hasGhidraFacet =
            project?.let(GhidraFacet::findAnyInProject) != null

        event.presentation.isEnabledAndVisible =
            GhidraPlatform.current() == GhidraPlatform.LINUX &&
                isGhidraProject &&
                hasGhidraFacet
    }

    override fun actionPerformed(event: AnActionEvent) {
        val project = event.getData(CommonDataKeys.PROJECT) ?: return

        val facet = GhidraFacet.findAnyInProject(project)
        if (facet?.installationPath.isNullOrBlank()) {
            Messages.showErrorDialog(
                project,
                "No Ghidra facet is configured for this project.",
                "Create PyGhidra GUI Launcher",
            )
            return
        }

        val launcher = try {
            PyGhidraGuiLauncherConfigurator.ensure(project)
        }
        catch (e: Exception) {
            Messages.showErrorDialog(
                project,
                e.message ?: "Failed to create a PyGhidra GUI launcher.",
                "Create PyGhidra GUI Launcher",
            )
            return
        }

        if (!launcher.created) {
            Messages.showInfoMessage(
                project,
                "Selected existing PyGhidra GUI launcher:\n${launcher.name}",
                "Create PyGhidra GUI Launcher",
            )
            return
        }

        Messages.showInfoMessage(
            project,
            "Created PyGhidra GUI launcher:\n${launcher.name}\n\n" +
                "Use Run from the main toolbar to start Ghidra through PyGhidra.",
            "Create PyGhidra GUI Launcher",
        )
    }
}