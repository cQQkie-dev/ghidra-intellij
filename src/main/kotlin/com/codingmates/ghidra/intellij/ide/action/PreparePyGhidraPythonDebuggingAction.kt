package com.codingmates.ghidra.intellij.ide.action

import com.codingmates.ghidra.intellij.ide.facet.GhidraFacet
import com.codingmates.ghidra.intellij.ide.icons.GhidraIcons
import com.codingmates.ghidra.intellij.ide.installation.GhidraPlatform
import com.codingmates.ghidra.intellij.ide.project.GhidraProjectKind
import com.codingmates.ghidra.intellij.ide.project.GhidraProjectLayoutDiscovery
import com.codingmates.ghidra.intellij.ide.runConfiguration.PyGhidraPythonDebugConfigurator
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.ui.Messages
import java.nio.file.Path

class PreparePyGhidraPythonDebuggingAction : DumbAwareAction(
    "Prepare PyGhidra Python Debugging",
    "Prepare PyGhidra and IntelliJ Python Debug Server configurations",
    GhidraIcons.PyGhidra,
) {

    override fun getActionUpdateThread(): ActionUpdateThread =
        ActionUpdateThread.BGT

    override fun update(event: AnActionEvent) {
        val project = event.getData(CommonDataKeys.PROJECT)
        val projectRoot = project?.basePath?.let(Path::of)

        val isGhidraProject = projectRoot?.let {
            val projectKind = GhidraProjectLayoutDiscovery.discover(it).kind

            projectKind == GhidraProjectKind.EXTENSION ||
                projectKind == GhidraProjectKind.SCRIPT
        } ?: false

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
                "Prepare PyGhidra Python Debugging",
            )
            return
        }

        val prepared = try {
            PyGhidraPythonDebugConfigurator.ensure(project)
        }
        catch (e: Exception) {
            Messages.showErrorDialog(
                project,
                e.message ?: "Failed to prepare PyGhidra Python debugging.",
                "Prepare PyGhidra Python Debugging",
            )
            return
        }

        val launcherStatus = if (prepared.launcherCreated) {
            "Created"
        }
        else {
            "Updated"
        }

        val debugServerStatus = if (prepared.debugServerCreated) {
            "Created"
        }
        else {
            "Selected"
        }

        Messages.showInfoMessage(
            project,
            "$launcherStatus PyGhidra launcher:\n" +
                "${prepared.launcherName}\n\n" +
                "Configured PYTHONPATH with IntelliJ's Python debugger helper:\n" +
                "${prepared.pydevHelperDirectory}\n\n" +
                "$debugServerStatus Python Debug Server:\n" +
                "${prepared.debugServerName}\n\n" +
                "Next:\n" +
                "1. Debug ${prepared.debugServerName}\n" +
                "2. Run ${prepared.launcherName}\n" +
                "3. Run a PyGhidra script that calls pydevd_pycharm.settrace(...)\n\n" +
                "The Python Debug Server listens on localhost:5678.",
            "PyGhidra Python Debugging Prepared",
        )
    }
}