package com.codingmates.ghidra.intellij.ide.action

import com.codingmates.ghidra.intellij.ide.facet.GhidraFacet
import com.codingmates.ghidra.intellij.ide.icons.GhidraIcons
import com.codingmates.ghidra.intellij.ide.installation.GhidraPlatform
import com.codingmates.ghidra.intellij.ide.project.GhidraProjectKind
import com.codingmates.ghidra.intellij.ide.project.GhidraProjectLayoutDiscovery
import com.codingmates.ghidra.intellij.ide.runConfiguration.PyGhidraJavaDebugAttachConfigurator
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.ui.Messages
import java.nio.file.Path

class CreatePyGhidraJavaDebugAttachAction : DumbAwareAction(
    "Create PyGhidra Java Debug Attach",
    "Create a Remote JVM Debug configuration for PyGhidra Java extension debugging",
    GhidraIcons.PyGhidra,
) {

    override fun getActionUpdateThread(): ActionUpdateThread =
        ActionUpdateThread.BGT

    override fun update(event: AnActionEvent) {
        val project = event.getData(CommonDataKeys.PROJECT)
        val projectRoot = project?.basePath?.let(Path::of)

        val isExtensionProject = projectRoot?.let {
            GhidraProjectLayoutDiscovery.discover(it).kind ==
                GhidraProjectKind.EXTENSION
        } ?: false

        val hasGhidraFacet =
            project?.let(GhidraFacet::findAnyInProject) != null

        event.presentation.isEnabledAndVisible =
            GhidraPlatform.current() == GhidraPlatform.LINUX &&
                isExtensionProject &&
                hasGhidraFacet
    }

    override fun actionPerformed(event: AnActionEvent) {
        val project = event.getData(CommonDataKeys.PROJECT) ?: return

        val facet = GhidraFacet.findAnyInProject(project)
        if (facet?.installationPath.isNullOrBlank()) {
            Messages.showErrorDialog(
                project,
                "No Ghidra facet is configured for this project.",
                "Create PyGhidra Java Debug Attach",
            )
            return
        }

        val launcher = try {
            PyGhidraJavaDebugAttachConfigurator.ensure(project)
        }
        catch (e: Exception) {
            Messages.showErrorDialog(
                project,
                e.message ?: "Failed to create the PyGhidra Java Debug Attach configuration.",
                "Create PyGhidra Java Debug Attach",
            )
            return
        }

        if (!launcher.created) {
            Messages.showInfoMessage(
                project,
                "Selected existing PyGhidra Java Debug Attach configuration:\n" +
                    launcher.name,
                "Create PyGhidra Java Debug Attach",
            )
            return
        }

        Messages.showInfoMessage(
            project,
            "Created PyGhidra Java Debug Attach configuration:\n${launcher.name}\n\n" +
                "Before attaching, run the PyGhidra GUI launcher with " +
                "PYGHIDRA_DEBUG=1. Then use Debug to attach to 127.0.0.1:18001.",
            "Create PyGhidra Java Debug Attach",
        )
    }
}