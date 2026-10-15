package com.codingmates.ghidra.intellij.ide.runConfiguration

import com.intellij.execution.configurations.RunProfile
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.execution.runners.DefaultProgramRunner

class PyGhidraGuiRunner : DefaultProgramRunner() {

    override fun getRunnerId(): String =
        "PyGhidraGuiRunner"

    override fun canRun(
        executorId: String,
        runProfile: RunProfile,
    ): Boolean =
        executorId == DefaultRunExecutor.EXECUTOR_ID &&
            runProfile is PyGhidraGuiConfiguration
}