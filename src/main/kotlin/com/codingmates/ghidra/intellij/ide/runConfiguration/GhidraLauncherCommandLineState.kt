package com.codingmates.ghidra.intellij.ide.runConfiguration


import com.codingmates.ghidra.intellij.ide.facet.GhidraFacet
import com.codingmates.ghidra.intellij.ide.installation.GhidraInstallationDiscovery
import com.codingmates.ghidra.intellij.ide.installation.GhidraInstallationException
import com.codingmates.ghidra.intellij.ide.installation.GhidraLaunchPropertiesParser
import com.codingmates.ghidra.intellij.ide.installation.GhidraPlatform
import com.intellij.execution.ExecutionException
import com.intellij.execution.application.BaseJavaApplicationCommandLineState
import com.intellij.execution.configurations.JavaParameters
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.util.JavaParametersUtil
import java.nio.file.Path
import java.util.zip.ZipFile
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
        javaParameters.mainClass = "ghidra.Ghidra"
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

            addRuntimeCompatibilityVmArguments(
                javaParameters = javaParameters,
                installationPath = installationPath,
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

    /**
     * Adds JVM options required by Ghidra runtime capabilities but not represented in
     * support/launch.properties.
     *
     * Ghidra releases that provide the serial-filter factory configure it during application
     * initialization. Some supported JDKs establish their default factory too early unless
     * Ghidra's factory is supplied as a startup JVM property.
     *
     * The check is installation-layout based rather than tied to a hardcoded Ghidra version, so
     * runtimes without the factory remain unaffected.
     */
    private fun addRuntimeCompatibilityVmArguments(
        javaParameters: JavaParameters,
        installationPath: String,
    ) {
        val installation = GhidraInstallationDiscovery.discover(
            Path.of(installationPath)
        )

        val fileSystemJar = installation.canonicalPath.resolve(
            "Ghidra/Framework/FileSystem/lib/FileSystem.jar"
        )

        val serialFilterFactoryClass =
            "ghidra/framework/remote/GhidraSerialFilterFactory.class"

        val supportsSerialFilterFactory =
            ZipFile(fileSystemJar.toFile()).use { archive ->
                archive.getEntry(serialFilterFactoryClass) != null
            }

        if (!supportsSerialFilterFactory) {
            return
        }

        val serialFilterFactoryArgument =
            "-Djdk.serialFilterFactory=" +
                "ghidra.framework.remote.GhidraSerialFilterFactory"

        if (
            javaParameters.vmParametersList.parameters.none {
                it.startsWith("-Djdk.serialFilterFactory=")
            }
        ) {
            javaParameters.vmParametersList.add(
                serialFilterFactoryArgument
            )
        }
    }
}