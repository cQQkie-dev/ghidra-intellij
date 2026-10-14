package com.codingmates.ghidra.intellij.ide.facet

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.externalSystem.service.project.manage.ProjectDataImportListener
import com.intellij.openapi.project.Project
import com.intellij.util.messages.MessageBusConnection
import java.nio.file.Path

/**
 * Configures the Ghidra facet after a generated Gradle project has been opened.
 *
 * The setup first runs on the next non-modal UI event. If Gradle has not created the primary
 * module yet, it retries once after the corresponding external-project import finishes.
 */
object GhidraGeneratedProjectFacetSetup {

    fun configureAfterGradleImport(project: Project) {
        val projectRoot = project.basePath
            ?.let(Path::of)
            ?.toAbsolutePath()
            ?.normalize()
            ?: return

        val connection = project.messageBus.connect()

        connection.subscribe(
            ProjectDataImportListener.TOPIC,
            object : ProjectDataImportListener {
                override fun onImportFinished(externalProjectPath: String?) {
                    if (isProjectRoot(externalProjectPath, projectRoot)) {
                        scheduleConfiguration(
                            project = project,
                            connection = connection,
                            disconnectAfterAttempt = true,
                        )
                    }
                }

                override fun onImportFailed(externalProjectPath: String?) {
                    if (isProjectRoot(externalProjectPath, projectRoot)) {
                        connection.disconnect()
                    }
                }
            },
        )

        scheduleConfiguration(
            project = project,
            connection = connection,
            disconnectAfterAttempt = false,
        )
    }

    private fun scheduleConfiguration(
        project: Project,
        connection: MessageBusConnection,
        disconnectAfterAttempt: Boolean,
    ) {
        ApplicationManager.getApplication().invokeLater(
            {
                val configured =
                    !project.isDisposed &&
                        GhidraGeneratedProjectConfigurator.configure(project)

                if (configured || disconnectAfterAttempt) {
                    connection.disconnect()
                }
            },
            ModalityState.nonModal(),
        )
    }

    private fun isProjectRoot(
        externalProjectPath: String?,
        projectRoot: Path,
    ): Boolean =
        externalProjectPath != null &&
            runCatching {
                Path.of(externalProjectPath)
                    .toAbsolutePath()
                    .normalize() == projectRoot
            }.getOrDefault(false)
}