package com.codingmates.ghidra.intellij.ide.runConfiguration

import com.codingmates.ghidra.intellij.ide.facet.GhidraFacet
import com.codingmates.ghidra.intellij.ide.installation.GhidraInstallationException
import com.codingmates.ghidra.intellij.ide.installation.GhidraLaunchPropertiesParser
import com.codingmates.ghidra.intellij.ide.installation.GhidraPlatform
import com.intellij.execution.ExecutionException
import com.intellij.execution.application.BaseJavaApplicationCommandLineState
import com.intellij.execution.configurations.JavaParameters
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.util.JavaParametersUtil
import java.nio.file.Path
import org.jetbrains.annotations.NotNull


class GhidraLauncherCommandLineState(
    environment: ExecutionEnvironment?,
    configuration: @NotNull GhidraLauncherConfiguration
) : BaseJavaApplicationCommandLineState<GhidraLauncherConfiguration>(environment, configuration) {
    override fun createJavaParameters(): JavaParameters {
        val project = configuration.project
        val javaParameters = JavaParameters()
        var jrePath: String? = null

        if (configuration.isAlternativeJrePathEnabled) {
            jrePath = configuration.alternativeJrePath
        }

        javaParameters.jdk = JavaParametersUtil.createProjectJdk(project, jrePath)
        javaParameters.mainClass = "ghidra.GhidraLauncher"
        if (configuration.getHeadless()) {
            javaParameters.programParametersList.add("ghidra.app.util.headless.AnalyzeHeadless")
        } else {
            javaParameters.programParametersList.add("ghidra.GhidraRun")
        }
        javaParameters.programParametersList.addParametersString(configuration.getArgs())
        val installationPath = GhidraFacet.findAnyInProject(project)?.installationPath
            ?: throw ExecutionException(
                "No Ghidra facet is configured for this project. " +
                    "Configure a Ghidra facet before launching."
            )

        try {
            val launchPropertiesPath = Path.of(installationPath)
                .toRealPath()
                .resolve("support/launch.properties")

            val launchProperties = GhidraLaunchPropertiesParser.parse(launchPropertiesPath)

            javaParameters.vmParametersList.addAll(
                launchProperties.vmArgumentsFor(GhidraPlatform.current())
            )
        }
        catch (e: GhidraInstallationException) {
            throw ExecutionException(
                "Failed to read Ghidra launch properties: ${e.message}",
                e,
            )
        }
        catch (e: Exception) {
            throw ExecutionException(
                "Failed to resolve Ghidra installation path: $installationPath",
                e,
            )
        }

        JavaParametersUtil.configureProject(
            project,
            javaParameters,
            JavaParameters.JDK_AND_CLASSES_AND_TESTS,
            jrePath
        )
        setupJavaParameters(javaParameters)
        val cp = javaParameters.classPath.pathList
        val hasUtility = cp.any { it.endsWith("/Framework/Utility/lib/Utility.jar") || it.endsWith("\\Framework\\Utility\\lib\\Utility.jar") }
        val hasLog4j = cp.any { it.contains("log4j", ignoreCase = true) }

        if (!hasUtility || !hasLog4j) {
            throw ExecutionException(
                "Ghidra classpath looks incomplete (Utility/log4j). " +
                    "Open the Ghidra facet and re-apply so all module libs are added."
            )
        }
        return javaParameters
    }
}