package com.codingmates.ghidra.intellij.ide.action

import com.codingmates.ghidra.intellij.ide.project.GhidraProjectKind
import com.codingmates.ghidra.intellij.ide.project.GhidraProjectLayoutDiscovery
import com.codingmates.ghidra.intellij.ide.script.CreateGhidraScriptDialog
import com.codingmates.ghidra.intellij.ide.script.GhidraScriptGenerationException
import com.codingmates.ghidra.intellij.ide.script.GhidraScriptGenerator
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vfs.LocalFileSystem
import java.nio.file.Path

class CreateGhidraScriptAction : DumbAwareAction(
    "New Ghidra Script",
    "Create a new Java or Python Ghidra script",
    null,
) {

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(event: AnActionEvent) {
        val project = event.getData(CommonDataKeys.PROJECT)
        val projectRoot = project?.basePath?.let(Path::of)

        val projectKind = projectRoot?.let {
            GhidraProjectLayoutDiscovery.discover(it).kind
        }

        event.presentation.isEnabledAndVisible =
            projectKind == GhidraProjectKind.SCRIPT ||
                projectKind == GhidraProjectKind.EXTENSION
    }

    override fun actionPerformed(event: AnActionEvent) {
        val project = event.getData(CommonDataKeys.PROJECT) ?: return
        val projectRoot = project.basePath?.let(Path::of) ?: return

        val scriptDirectory = projectRoot.resolve("ghidra_scripts")
        val dialog = CreateGhidraScriptDialog(project, scriptDirectory)

        if (!dialog.showAndGet()) {
            return
        }

        try {
            val scriptPath = GhidraScriptGenerator.create(
                scriptDirectory = dialog.scriptDirectory(),
                spec = dialog.scriptSpec(),
            )

            val virtualFile = LocalFileSystem.getInstance()
                .refreshAndFindFileByNioFile(scriptPath)

            if (virtualFile != null) {
                FileEditorManager.getInstance(project).openFile(virtualFile, true)
            }
        }
        catch (e: GhidraScriptGenerationException) {
            Messages.showErrorDialog(
                project,
                e.message ?: "Failed to create Ghidra script.",
                "Create Ghidra Script",
            )
        }
    }
}