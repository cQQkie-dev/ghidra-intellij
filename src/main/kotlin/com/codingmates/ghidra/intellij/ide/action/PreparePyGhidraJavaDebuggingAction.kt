package com.codingmates.ghidra.intellij.ide.action

import com.codingmates.ghidra.intellij.ide.facet.GhidraFacet
import com.codingmates.ghidra.intellij.ide.icons.GhidraIcons
import com.codingmates.ghidra.intellij.ide.installation.GhidraPlatform
import com.codingmates.ghidra.intellij.ide.project.GhidraProjectKind
import com.codingmates.ghidra.intellij.ide.project.GhidraProjectLayoutDiscovery
import com.codingmates.ghidra.intellij.ide.runConfiguration.PyGhidraJavaDebugConfigurator
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.ui.Messages
import java.nio.file.Path

class PreparePyGhidraJavaDebuggingAction : DumbAwareAction(
    "Prepare PyGhidra Java Debugging",
    "Prepare PyGhidra GUI and Remote JVM Debug configurations for Java extension debugging",
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
                "Prepare PyGhidra Java Debugging",
            )
            return
        }

        val prepared = try {
            PyGhidraJavaDebugConfigurator.ensure(project)
        }
        catch (e: Exception) {
            Messages.showErrorDialog(
                project,
                e.message ?: "Failed to prepare PyGhidra Java debugging.",
                "Prepare PyGhidra Java Debugging",
            )
            return
        }

        val launcherStatus = if (prepared.launcherCreated) {
            "Created"
        }
        else {
            "Updated"
        }

        val attachStatus = if (prepared.attachCreated) {
            "Created"
        }
        else {
            "Selected"
        }

        Messages.showInfoMessage(
            project,
            "$launcherStatus PyGhidra launcher:\n" +
                "${prepared.launcherName}\n\n" +
                "Enabled:\n" +
                "${PyGhidraJavaDebugConfigurator.PYGHIDRA_DEBUG_ENVIRONMENT_VARIABLE}=1\n\n" +
                "$attachStatus Remote JVM Debug configuration:\n" +
                "${prepared.attachName}\n\n" +
                "Next:\n" +
                "1. Run ${prepared.launcherName}\n" +
                "2. Wait for Ghidra to open\n" +
                "3. Debug ${prepared.attachName}\n\n" +
                "The debugger attaches to 127.0.0.1:18001.",
            "PyGhidra Java Debugging Prepared",
        )
    }
}