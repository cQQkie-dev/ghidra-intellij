package com.codingmates.ghidra.intellij.ide.action

import com.codingmates.ghidra.intellij.ide.facet.GhidraFacet
import com.codingmates.ghidra.intellij.ide.icons.GhidraIcons
import com.codingmates.ghidra.intellij.ide.project.GhidraProjectKind
import com.codingmates.ghidra.intellij.ide.project.GhidraProjectLayoutDiscovery
import com.codingmates.ghidra.intellij.ide.runConfiguration.GhidraDevelopmentLauncherConfigurator
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.ui.Messages
import java.nio.file.Path

class CreateGhidraDevelopmentLauncherAction : DumbAwareAction(
    "Create Ghidra Development Launcher",
    "Create a Ghidra Launcher Run Configuration for the current project",
    GhidraIcons.Ghidra,
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
            isGhidraProject && hasGhidraFacet
    }

    override fun actionPerformed(event: AnActionEvent) {
        val project = event.getData(CommonDataKeys.PROJECT) ?: return
        if (project.basePath == null) {
            return
        }

        val facet = GhidraFacet.findAnyInProject(project)
        if (facet?.installationPath.isNullOrBlank()) {
            Messages.showErrorDialog(
                project,
                "No Ghidra facet is configured for this project.",
                "Create Ghidra Development Launcher",
            )
            return
        }

        val launcher = GhidraDevelopmentLauncherConfigurator.ensure(project)

        if (!launcher.created) {
            Messages.showInfoMessage(
                project,
                "Selected existing Ghidra development launcher:\n${launcher.name}",
                "Create Ghidra Development Launcher",
            )
            return
        }

        Messages.showInfoMessage(
            project,
            "Created Ghidra development launcher:\n${launcher.name}\n\n" +
                "Use Run or Debug from the main toolbar to start Ghidra.",
            "Create Ghidra Development Launcher",
        )
    }
}