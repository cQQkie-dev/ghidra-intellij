package com.codingmates.ghidra.intellij.ide.runConfiguration

import com.codingmates.ghidra.intellij.ide.facet.GhidraFacet
import com.codingmates.ghidra.intellij.ide.installation.GhidraPlatform
import com.codingmates.ghidra.intellij.ide.project.GhidraProjectKind
import com.codingmates.ghidra.intellij.ide.project.GhidraProjectLayoutDiscovery
import com.intellij.execution.Executor
import com.intellij.execution.CommonProgramRunConfigurationParameters
import com.intellij.execution.configuration.EnvironmentVariablesComponent
import com.intellij.execution.configurations.ConfigurationFactory
import com.intellij.execution.configurations.LocatableConfigurationBase
import com.intellij.execution.configurations.RunConfiguration
import com.intellij.execution.configurations.RunProfileState
import com.intellij.execution.configurations.RuntimeConfigurationError
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.util.ProgramParametersUtil
import com.intellij.openapi.options.SettingsEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtil
import com.intellij.util.xmlb.XmlSerializer
import com.intellij.util.xmlb.XmlSerializerUtil
import org.jdom.Element
import java.nio.file.Files
import java.nio.file.Path

class PyGhidraGuiConfiguration(
    project: Project,
    factory: ConfigurationFactory,
    name: String?,
) :
    LocatableConfigurationBase<Any?>(project, factory, name),
    CommonProgramRunConfigurationParameters {

    override fun checkConfiguration() {
        if (GhidraPlatform.current() != GhidraPlatform.LINUX) {
            throw RuntimeConfigurationError(
                "PyGhidra GUI launching is currently supported on Linux only."
            )
        }

        ProgramParametersUtil.checkWorkingDirectoryExist(this, project, null)

        val facet = GhidraFacet.findAnyInProject(project)
            ?: throw RuntimeConfigurationError(
                "No Ghidra facet is configured for this project. " +
                    "Configure a Ghidra facet before launching PyGhidra."
            )

        if (facet.installationPath.isBlank()) {
            throw RuntimeConfigurationError(
                "The configured Ghidra facet does not contain an installation path."
            )
        }

        val launcherPath = Path.of(facet.installationPath)
            .resolve("support/pyghidraRun")

        if (!Files.isRegularFile(launcherPath)) {
            throw RuntimeConfigurationError(
                "The selected Ghidra installation does not contain " +
                    "support/pyghidraRun:\n$launcherPath"
            )
        }

        if (!Files.isExecutable(launcherPath)) {
            throw RuntimeConfigurationError(
                "The PyGhidra launcher is not executable:\n$launcherPath"
            )
        }

        if (loadsCurrentExtensionProject()) {
            val projectRoot = project.basePath
                ?.let(Path::of)
                ?: throw RuntimeConfigurationError(
                    "The project does not have a base directory."
                )

            if (
                GhidraProjectLayoutDiscovery.discover(projectRoot).kind !=
                GhidraProjectKind.EXTENSION
            ) {
                throw RuntimeConfigurationError(
                    "Load current extension project is enabled, but the current project " +
                        "is not a recognized Ghidra extension project."
                )
            }
        }
    }

    override fun clone(): RunConfiguration {
        val clone = super.clone() as PyGhidraGuiConfiguration
        clone.state = XmlSerializerUtil.createCopy(state)
        clone.envs = LinkedHashMap(clone.envs)
        return clone
    }

    override fun getConfigurationEditor(): SettingsEditor<out RunConfiguration> =
        PyGhidraGuiConfigurationEditor(project)

    override fun getState(
        executor: Executor,
        environment: ExecutionEnvironment,
    ): RunProfileState =
        PyGhidraGuiCommandLineState(environment, this)

    override fun onNewConfigurationCreated() {
        if (getWorkingDirectory().isNullOrBlank()) {
            setWorkingDirectory(
                FileUtil.toSystemIndependentName(project.basePath.orEmpty())
            )
        }

        if (!loadsCurrentExtensionProject()) {
            val projectRoot = project.basePath?.let(Path::of)

            if (
                projectRoot != null &&
                GhidraProjectLayoutDiscovery.discover(projectRoot).kind ==
                    GhidraProjectKind.EXTENSION
            ) {
                setLoadCurrentExtensionProject(true)
            }
        }
    }

    override fun readExternal(element: Element) {
        super.readExternal(element)
        XmlSerializer.deserializeInto(state, element)
        EnvironmentVariablesComponent.readExternal(element, envs)
    }

    override fun writeExternal(element: Element) {
        super.writeExternal(element)
        XmlSerializer.serializeInto(state, element, null)
        EnvironmentVariablesComponent.writeExternal(element, envs)
    }

    fun loadsCurrentExtensionProject(): Boolean =
        state.loadCurrentExtensionProject

    fun setLoadCurrentExtensionProject(enabled: Boolean) {
        state.loadCurrentExtensionProject = enabled
    }

    override fun getProgramParameters(): String? = null

    override fun setProgramParameters(value: String?) {
    }

    override fun getEnvs(): Map<String, String> = envs

    override fun setEnvs(value: Map<String, String>) {
        envs = LinkedHashMap(value)
    }

    override fun isPassParentEnvs(): Boolean =
        state.passParentEnvironments

    override fun setPassParentEnvs(enabled: Boolean) {
        state.passParentEnvironments = enabled
    }

    override fun getWorkingDirectory(): String? =
        state.workingDirectory

    override fun setWorkingDirectory(value: String?) {
        state.workingDirectory = value
    }

    private var state = PyGhidraGuiConfigurationBean()
    private var envs: MutableMap<String, String> = LinkedHashMap()

    private data class PyGhidraGuiConfigurationBean(
        var workingDirectory: String? = null,
        var loadCurrentExtensionProject: Boolean = false,
        var passParentEnvironments: Boolean = true,
    )
}