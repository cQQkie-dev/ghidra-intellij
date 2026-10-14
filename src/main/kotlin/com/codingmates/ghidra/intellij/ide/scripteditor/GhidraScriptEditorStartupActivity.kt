package com.codingmates.ghidra.intellij.ide.scripteditor

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.StartupActivity

/**
 * Starts the persisted Script Editor listener after an IntelliJ project opens.
 */
class GhidraScriptEditorStartupActivity : StartupActivity.DumbAware {

    override fun runActivity(project: Project) {
        ApplicationManager.getApplication()
            .getService(GhidraScriptEditorListenerService::class.java)
            .startIfEnabled()
    }
}