package com.codingmates.ghidra.intellij.ide.migration

import com.codingmates.ghidra.intellij.ide.facet.GhidraFacet
import com.codingmates.ghidra.intellij.ide.project.GhidraProjectInstallationDiscovery
import com.codingmates.ghidra.intellij.ide.runConfiguration.GhidraLauncherConfiguration
import com.intellij.execution.RunManager
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemJdkUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.util.lang.JavaVersion
import org.jetbrains.plugins.gradle.settings.GradleSettings
import java.nio.file.Path

/**
 * Collects IntelliJ and filesystem project state for [GhidraTargetMigrationPlanner].
 *
 * The result deliberately uses only pure migration model classes. This keeps policy evaluation
 * independently testable and isolates IntelliJ Platform APIs in one adapter.
 */
object GhidraProjectTargetSnapshotCollector {

    fun collect(project: Project): GhidraProjectTargetSnapshot {
        val projectRoot = project.basePath
            ?.let(Path::of)
            ?.toAbsolutePath()
            ?.normalize()
            ?: error("The project does not have a base directory.")

        val gradleSettings = GradleSettings.getInstance(project)
        val linkedGradleProject = gradleSettings
            .getLinkedProjectSettings(projectRoot.toString())

        val rawGradleJvm = linkedGradleProject
            ?.gradleJvm
            ?.trim()
            ?.takeIf(String::isNotEmpty)

        return GhidraProjectTargetSnapshot(
            projectRoot = projectRoot,
            localInstallationPath =
                GhidraProjectInstallationDiscovery.discover(projectRoot),
            facetInstallationPath =
                GhidraFacet.findAnyInProject(project)
                    ?.installationPath
                    ?.takeIf(String::isNotBlank)
                    ?.let(::parsePathOrNull),
            wrapper = GhidraGradleWrapperDiscovery.discover(projectRoot),
            environmentOverride =
                GhidraGlobalTargetOverrideDiscovery.discoverEnvironmentOverride(
                    System.getenv()
                ),
            userGradlePropertiesOverride =
                GhidraGlobalTargetOverrideDiscovery
                    .discoverUserGradlePropertiesOverride(
                        resolveGradleUserHome(
                            project = project,
                            configuredServiceDirectory =
                                gradleSettings.serviceDirectoryPath,
                        )
                    ),
            projectSdk = sdkRuntime(
                label = "Project SDK",
                sdk = ProjectRootManager.getInstance(project).projectSdk,
            ) ?: GhidraJavaRuntimeInfo(
                label = "Project SDK",
                featureVersion = null,
                homePath = null,
            ),
            explicitLauncherJre = findExplicitLauncherJre(project),
            gradleJvm = rawGradleJvm?.let { configuredJvm ->
                sdkRuntime(
                    label = "Gradle JVM",
                    sdk = runCatching {
                        ExternalSystemJdkUtil.getJdk(project, configuredJvm)
                    }.getOrNull(),
                ) ?: GhidraJavaRuntimeInfo(
                    label = "Gradle JVM",
                    featureVersion = null,
                    homePath = null,
                )
            },
        )
    }

    private fun findExplicitLauncherJre(
        project: Project,
    ): GhidraJavaRuntimeInfo? {
        val launcher = RunManager.getInstance(project)
            .allSettings
            .asSequence()
            .map { it.configuration }
            .filterIsInstance<GhidraLauncherConfiguration>()
            .firstOrNull { it.isAlternativeJrePathEnabled() }
            ?: return null

        val configuredJre = launcher
            .getAlternativeJrePath()
            ?.trim()
            ?.takeIf(String::isNotEmpty)
            ?: return GhidraJavaRuntimeInfo(
                label = "Ghidra Launcher JRE",
                featureVersion = null,
                homePath = null,
            )

        return sdkRuntime(
            label = "Ghidra Launcher JRE",
            sdk = runCatching {
                ExternalSystemJdkUtil.getJdk(project, configuredJre)
            }.getOrNull(),
        )
    }

    private fun sdkRuntime(
        label: String,
        sdk: Sdk?,
    ): GhidraJavaRuntimeInfo? {
        if (sdk == null) {
            return null
        }

        val homePath = sdk.homePath?.let(::parsePathOrNull)

        val featureVersion = sdk.homePath
            ?.let(::javaFeatureVersion)
            ?: return GhidraJavaRuntimeInfo(
                label = label,
                featureVersion = null,
                homePath = homePath,
            )

        return GhidraJavaRuntimeInfo(
            label = label,
            featureVersion = featureVersion,
            homePath = homePath,
        )
    }

    private fun javaFeatureVersion(homePath: String): Int? =
        runCatching {
            ExternalSystemJdkUtil.getJavaVersion(homePath)?.feature
        }.getOrNull()

    private fun resolveGradleUserHome(
        project: Project,
        configuredServiceDirectory: String?,
    ): Path {
        val serviceDirectory = configuredServiceDirectory
            ?.trim()
            ?.takeIf(String::isNotEmpty)
            ?.let(::parsePathOrNull)

        if (serviceDirectory != null) {
            return serviceDirectory
        }

        val environmentDirectory = System.getenv("GRADLE_USER_HOME")
            ?.trim()
            ?.takeIf(String::isNotEmpty)
            ?.let(::parsePathOrNull)

        if (environmentDirectory != null) {
            return environmentDirectory
        }

        return Path.of(
            System.getProperty("user.home"),
            ".gradle",
        ).toAbsolutePath().normalize()
    }

    private fun parsePathOrNull(value: String): Path? =
        runCatching {
            Path.of(value)
                .toAbsolutePath()
                .normalize()
        }.getOrNull()
}