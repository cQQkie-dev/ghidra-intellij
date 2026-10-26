package com.codingmates.ghidra.intellij.ide.migration

import com.codingmates.ghidra.intellij.ide.runConfiguration.GhidraLauncherConfiguration
import com.intellij.execution.RunManager
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemJdkUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import org.jetbrains.plugins.gradle.settings.GradleSettings
import java.nio.file.Path

/**
 * Applies explicitly confirmed Java configuration changes for a Ghidra target migration.
 *
 * This object never decides which changes should be made. The migration dialog makes those
 * choices visible and explicit; this object only applies the approved changes.
 */
object GhidraProjectJavaConfigurationUpdater {

    fun apply(
        project: Project,
        compatibleSdk: GhidraCompatibleSdk,
        updateProjectSdk: Boolean,
        updateGradleJvm: Boolean,
        updateExplicitLauncherJre: Boolean,
    ): GhidraProjectJavaConfigurationUpdateResult {
        var projectSdkUpdated = false
        var gradleJvmUpdated = false
        var launcherJreUpdated = false

        if (updateProjectSdk) {
            ApplicationManager.getApplication().runWriteAction {
                ProjectRootManager.getInstance(project)
                    .setProjectSdk(compatibleSdk.sdk)
            }

            projectSdkUpdated = true
        }

        if (updateGradleJvm) {
            gradleJvmUpdated = updateGradleJvm(
                project = project,
                sdkName = compatibleSdk.name,
            )
        }

        if (updateExplicitLauncherJre) {
            launcherJreUpdated = updateExplicitLauncherJre(
                project = project,
                sdkName = compatibleSdk.name,
            )
        }

        return GhidraProjectJavaConfigurationUpdateResult(
            projectSdkUpdated = projectSdkUpdated,
            gradleJvmUpdated = gradleJvmUpdated,
            launcherJreUpdated = launcherJreUpdated,
        )
    }

    private fun updateGradleJvm(
        project: Project,
        sdkName: String,
    ): Boolean {
        val projectRoot = project.basePath
            ?.let(Path::of)
            ?.toAbsolutePath()
            ?.normalize()
            ?.toString()
            ?: return false

        val gradleSettings = GradleSettings.getInstance(project)
        val currentSettings = gradleSettings
            .getLinkedProjectSettings(projectRoot)
            ?: return false

        val updatedSettings = currentSettings.clone().apply {
            gradleJvm = sdkName
        }

        val replacementSettings = gradleSettings
            .linkedProjectsSettings
            .map { linkedSettings ->
                if (linkedSettings.externalProjectPath == projectRoot) {
                    updatedSettings
                }
                else {
                    linkedSettings
                }
            }

        gradleSettings.setLinkedProjectsSettings(replacementSettings)

        return true
    }

    private fun updateExplicitLauncherJre(
        project: Project,
        sdkName: String,
    ): Boolean {
        val launcherConfiguration = RunManager.getInstance(project)
            .allSettings
            .asSequence()
            .map { it.configuration }
            .filterIsInstance<GhidraLauncherConfiguration>()
            .firstOrNull { configuration ->
                configuration.isAlternativeJrePathEnabled()
            }
            ?: return false

        launcherConfiguration.setAlternativeJrePath(sdkName)
        launcherConfiguration.setAlternativeJrePathEnabled(true)

        return true
    }
}

data class GhidraProjectJavaConfigurationUpdateResult(
    val projectSdkUpdated: Boolean,
    val gradleJvmUpdated: Boolean,
    val launcherJreUpdated: Boolean,
)