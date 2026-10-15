package com.codingmates.ghidra.intellij.ide.runConfiguration

import com.codingmates.ghidra.intellij.ide.icons.GhidraIcons
import com.intellij.execution.configurations.ConfigurationFactory
import com.intellij.execution.configurations.ConfigurationType
import com.intellij.execution.configurations.ConfigurationTypeBase
import com.intellij.execution.configurations.RunConfiguration
import com.intellij.openapi.project.Project

class PyGhidraGuiConfigurationType : ConfigurationTypeBase(
    "PyGhidraGuiConfiguration",
    "PyGhidra GUI",
    "Start Ghidra through its official PyGhidra launcher.",
    GhidraIcons.Ghidra,
), ConfigurationType {

    init {
        addFactory(
            object : ConfigurationFactory(this) {
                override fun createTemplateConfiguration(
                    project: Project,
                ): RunConfiguration =
                    PyGhidraGuiConfiguration(project, this, "")

                override fun getId(): String = name
            }
        )
    }
}